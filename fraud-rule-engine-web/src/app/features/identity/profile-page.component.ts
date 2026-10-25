import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { finalize } from 'rxjs';
import { UserProfile } from '../../core/api/api.models';
import { UserApiService } from '../../core/api/user-api.service';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { NotificationService } from '../../core/notifications/notification.service';
import { PasswordChangeComponent } from './password-change.component';
import { UserFormComponent } from './user-form.component';

@Component({
  selector: 'app-profile-page',
  imports: [DatePipe, UserFormComponent, PasswordChangeComponent],
  template: `
    <p class="eyebrow">ASSESSMENT ACCOUNT</p>
    <h1>My profile</h1>
    @if (failed()) {
      <section class="panel" role="alert">
        <p>Your profile could not be loaded.</p>
        <button class="secondary-button" type="button" (click)="load()">Try again</button>
      </section>
    } @else if (profile(); as user) {
      <section class="panel">
        <h2>{{ user.name }} {{ user.surname }}</h2>
        <dl class="facts">
          <div><dt>Role</dt><dd>{{ user.role }}</dd></div>
          <div><dt>Status</dt><dd>{{ user.status }}</dd></div>
          <div><dt>Created (UTC)</dt><dd>{{ user.createdAt | date: 'yyyy-MM-dd HH:mm:ss' : 'UTC' }}</dd></div>
        </dl>
        @if (canWrite()) {
          <p>Profile updates revoke existing tokens. You will be signed out after saving.</p>
          <app-user-form mode="profile" [user]="user" (saved)="saved($event)" />
        } @else {
          <p>{{ user.email }}</p>
        }
      </section>
      @if (canWrite()) { <app-password-change /> }
    } @else {
      <p role="status">Loading your profile…</p>
    }
  `,
  styleUrl: './identity.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfilePageComponent implements OnInit {
  private readonly users = inject(UserApiService);
  private readonly session = inject(AuthSessionService);
  private readonly notifications = inject(NotificationService);
  readonly profile = signal<UserProfile | null>(null);
  readonly failed = signal(false);
  readonly loading = signal(false);

  canWrite(): boolean {
    return this.session.hasPermission('OWN_PROFILE_WRITE');
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    const id = this.session.user()?.id;
    if (!id) {
      this.failed.set(true);
      return;
    }
    this.failed.set(false);
    this.loading.set(true);
    this.users.get(id).pipe(finalize(() => this.loading.set(false))).subscribe({
      next: (user) => this.profile.set(user),
      error: () => this.failed.set(true),
    });
  }

  saved(user: UserProfile): void {
    this.profile.set(user);
    this.notifications.show('Profile updated. Sign in again with your current email and password.', 'success');
    this.session.logout();
  }
}
