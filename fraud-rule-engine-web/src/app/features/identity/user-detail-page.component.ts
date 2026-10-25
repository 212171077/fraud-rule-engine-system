import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, EMPTY, finalize, switchMap, tap } from 'rxjs';
import { UserProfile, UserRole, UserStatus } from '../../core/api/api.models';
import { UserApiService } from '../../core/api/user-api.service';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { NotificationService } from '../../core/notifications/notification.service';
import { UserFormComponent } from './user-form.component';

type AccessChange =
  | { kind: 'role'; value: UserRole }
  | { kind: 'status'; value: UserStatus };

@Component({
  selector: 'app-user-detail-page',
  imports: [DatePipe, ReactiveFormsModule, RouterLink, UserFormComponent],
  template: `
    <a routerLink="/users">Back to users</a>
    <p class="eyebrow">LOCAL IDENTITY ADMINISTRATION</p>
    <h1>Manage account</h1>
    @if (failed()) {
      <section class="panel" role="alert">
        <p>This account could not be loaded. It may be unavailable or your access may have changed.</p>
        <button class="secondary-button" type="button" (click)="load()">Try again</button>
      </section>
    } @else if (loading()) {
      <p role="status">Loading account…</p>
    } @else if (user(); as account) {
      <section class="panel">
        <h2>{{ account.name }} {{ account.surname }}</h2>
        <dl class="facts">
          <div><dt>Role</dt><dd>{{ account.role }}</dd></div>
          <div><dt>Status</dt><dd>{{ account.status }}</dd></div>
          <div><dt>Created (UTC)</dt><dd>{{ account.createdAt | date: 'yyyy-MM-dd HH:mm:ss' : 'UTC' }}</dd></div>
          <div><dt>Updated (UTC)</dt><dd>{{ account.updatedAt | date: 'yyyy-MM-dd HH:mm:ss' : 'UTC' }}</dd></div>
        </dl>
        <p>Profile updates also revoke this account's existing tokens.</p>
        <app-user-form mode="profile" [user]="account" (saved)="profileSaved($event)" />
      </section>
      <section class="panel">
        <h2>Role and account status</h2>
        @if (self()) {
          <p>You cannot change your own role or account status.</p>
        } @else {
          <p>Each role or status change revokes the account's current tokens. Interactive users must sign in again.</p>
          @if (account.role === 'SERVICE') {
            <p>SERVICE role transitions are not supported. This identity cannot sign in to the website.</p>
          }
          <fieldset [disabled]="saving() || pending() !== null">
            <div class="fields">
              @if (account.role !== 'SERVICE') {
                <label class="field">
                  <span>Role</span>
                  <select [formControl]="role">
                    <option value="AUDITOR">AUDITOR</option>
                    <option value="FRAUD_ANALYST">FRAUD_ANALYST</option>
                    <option value="ADMIN">ADMIN</option>
                  </select>
                </label>
              }
              <label class="field">
                <span>Account status</span>
                <select [formControl]="status">
                  <option value="PENDING">PENDING</option>
                  <option value="ACTIVE">ACTIVE</option>
                  <option value="DISABLED">DISABLED</option>
                </select>
              </label>
            </div>
            <div class="actions">
              @if (account.role !== 'SERVICE') {
                <button class="secondary-button" type="button" [disabled]="role.value === account.role"
                  (click)="reviewRole()">Review role change</button>
              }
              <button class="secondary-button" type="button" [disabled]="status.value === account.status"
                (click)="reviewStatus()">Review status change</button>
            </div>
          </fieldset>
          @if (pending(); as change) {
            <section class="confirmation" aria-label="Confirm access change">
              <h3>Confirm {{ change.kind }} change</h3>
              <p>Set {{ account.email }} to <strong>{{ change.value }}</strong>?</p>
              @if (change.kind === 'status' && change.value === 'ACTIVE') {
                <p>This approves access for the account's current role: {{ account.role }}.</p>
              } @else if (change.kind === 'status') {
                <p>This prevents interactive sign-in and access while the account is not ACTIVE.</p>
              }
              <p>Existing tokens will be revoked. This does not change historical fraud evaluations.</p>
              <div class="actions">
                <button class="secondary-button" type="button" [disabled]="saving()" (click)="pending.set(null)">Cancel</button>
                <button class="primary-button" type="button" [disabled]="saving()" (click)="confirm()">
                  {{ saving() ? 'Saving…' : 'Confirm change' }}
                </button>
              </div>
            </section>
          }
          @if (saveFailed()) {
            <p class="error" role="alert">The access change was not saved. Review the API notification and reload before retrying.</p>
            <button class="secondary-button" type="button" (click)="load()">Reload account</button>
          }
        }
      </section>
    }
  `,
  styleUrl: './identity.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserDetailPageComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly users = inject(UserApiService);
  private readonly session = inject(AuthSessionService);
  private readonly notifications = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);
  readonly user = signal<UserProfile | null>(null);
  readonly loading = signal(false);
  readonly failed = signal(false);
  readonly saving = signal(false);
  readonly saveFailed = signal(false);
  readonly pending = signal<AccessChange | null>(null);
  readonly self = computed(() => this.user()?.id === this.session.user()?.id);
  readonly role = new FormControl<UserRole>('AUDITOR', { nonNullable: true });
  readonly status = new FormControl<UserStatus>('PENDING', { nonNullable: true });

  ngOnInit(): void {
    this.route.paramMap.pipe(
      tap(() => this.startLoad()),
      switchMap((params) => this.users.get(params.get('id') ?? '').pipe(
        catchError(() => {
          this.failed.set(true);
          this.loading.set(false);
          return EMPTY;
        }),
      )),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: (user) => { this.setUser(user); this.loading.set(false); },
    });
  }

  load(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id || this.saving()) {
      return;
    }
    this.startLoad();
    this.users.get(id).pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.loading.set(false)),
    ).subscribe({
      next: (user) => this.setUser(user),
      error: () => this.failed.set(true),
    });
  }

  profileSaved(user: UserProfile): void {
    this.setUser(user);
    this.notifications.show('Profile updated. The account must sign in again; existing tokens were revoked.', 'success');
    if (this.self()) {
      this.session.logout();
    }
  }

  reviewRole(): void {
    if (!this.self() && this.user()?.role !== 'SERVICE' && this.role.value !== 'SERVICE') {
      this.pending.set({ kind: 'role', value: this.role.value });
    }
  }

  reviewStatus(): void {
    if (!this.self()) {
      this.pending.set({ kind: 'status', value: this.status.value });
    }
  }

  confirm(): void {
    const user = this.user();
    const change = this.pending();
    if (!user || !change || this.self() || this.saving()) {
      return;
    }
    const request = change.kind === 'role'
      ? this.users.updateRole(user.id, change.value)
      : this.users.updateStatus(user.id, change.value);
    this.saving.set(true);
    this.saveFailed.set(false);
    request.pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.saving.set(false)),
    ).subscribe({
      next: (updated) => {
        this.setUser(updated);
        this.notifications.show('Account access updated. Existing tokens were revoked.', 'success');
      },
      error: () => this.saveFailed.set(true),
    });
  }

  private startLoad(): void {
    this.loading.set(true);
    this.failed.set(false);
    this.saveFailed.set(false);
    this.pending.set(null);
    this.user.set(null);
  }

  private setUser(user: UserProfile): void {
    this.user.set(user);
    this.role.setValue(user.role);
    this.status.setValue(user.status);
    this.pending.set(null);
  }
}
