import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { Page, UserProfile } from '../../core/api/api.models';
import { UserApiService } from '../../core/api/user-api.service';
import { NotificationService } from '../../core/notifications/notification.service';
import { UserFormComponent } from './user-form.component';

@Component({
  selector: 'app-user-list-page',
  imports: [ReactiveFormsModule, RouterLink, UserFormComponent],
  template: `
    <p class="eyebrow">LOCAL IDENTITY ADMINISTRATION</p>
    <h1>Users</h1>
    <p>Review pending requests and manage synthetic assessment accounts.</p>
    <div class="page-actions">
      <button class="primary-button" type="button" (click)="createOpen.set(!createOpen())">
        {{ createOpen() ? 'Close account form' : 'Create account' }}
      </button>
      <button class="secondary-button" type="button" (click)="load()" [disabled]="loading()">Refresh</button>
      <label class="field">
        <span>Users per page</span>
        <select [formControl]="pageSize" (change)="load(0)">
          <option [ngValue]="20">20</option>
          <option [ngValue]="50">50</option>
          <option [ngValue]="100">100</option>
        </select>
      </label>
    </div>
    @if (createOpen()) {
      <section class="panel">
        <h2>Create an assessment account</h2>
        <p>Unlike public registration, admin-created accounts are ACTIVE immediately.</p>
        <app-user-form mode="create" (saved)="created($event)" />
      </section>
    }
    @if (failed()) {
      <section class="panel" role="alert">
        <p>Users could not be loaded. Your access may have changed, or the service is unavailable.</p>
        <button class="secondary-button" type="button" (click)="load()">Try again</button>
      </section>
    } @else if (loading()) {
      <p role="status">Loading accounts…</p>
    } @else if (result(); as page) {
      <p role="status">{{ page.totalElements }} account(s) · Page {{ page.page + 1 }} of {{ page.totalPages || 1 }}</p>
      @if (page.content.length) {
        <div class="table-scroll" tabindex="0" role="region" aria-label="User accounts">
          <table>
            <thead><tr>
              <th scope="col">Name</th><th scope="col">Email</th>
              <th scope="col">Role</th><th scope="col">Status</th><th scope="col">Manage</th>
            </tr></thead>
            <tbody>
              @for (user of page.content; track user.id) {
                <tr>
                  <td>{{ user.name }} {{ user.surname }}</td><td>{{ user.email }}</td>
                  <td>{{ user.role }}</td><td><span [class]="'badge status-' + user.status.toLowerCase()">{{ user.status }}</span></td>
                  <td><a [routerLink]="['/users', user.id]" [attr.aria-label]="'Manage ' + user.email">Manage</a></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      } @else {
        <section class="panel"><p>No users on this page. Use Previous to return to earlier results.</p></section>
      }
      <div class="actions">
        <button class="secondary-button" type="button" [disabled]="page.page === 0" (click)="load(page.page - 1)">
          Previous
        </button>
        <button class="secondary-button" type="button"
          [disabled]="page.page + 1 >= page.totalPages" (click)="load(page.page + 1)">Next</button>
      </div>
    }
  `,
  styleUrl: './identity.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserListPageComponent implements OnInit {
  private readonly users = inject(UserApiService);
  private readonly notifications = inject(NotificationService);
  readonly result = signal<Page<UserProfile> | null>(null);
  readonly loading = signal(false);
  readonly failed = signal(false);
  readonly createOpen = signal(false);
  readonly pageSize = new FormControl(20, { nonNullable: true });

  ngOnInit(): void {
    this.load(0);
  }

  load(page = this.result()?.page ?? 0): void {
    if (this.loading()) {
      return;
    }
    this.loading.set(true);
    this.failed.set(false);
    this.users.list(page, this.pageSize.value).pipe(finalize(() => this.loading.set(false))).subscribe({
      next: (result) => this.result.set(result),
      error: () => this.failed.set(true),
    });
  }

  created(user: UserProfile): void {
    this.createOpen.set(false);
    this.notifications.show(`Account created with ${user.role} role and ${user.status} status.`, 'success');
    this.load(0);
  }
}
