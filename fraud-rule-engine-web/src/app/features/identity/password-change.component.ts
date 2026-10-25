import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { AuthApiService } from '../../core/auth/auth-api.service';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { NotificationService } from '../../core/notifications/notification.service';
import { matchingPasswords, passwordValidators } from './identity-validation';

@Component({
  selector: 'app-password-change',
  imports: [ReactiveFormsModule],
  template: `
    <section class="panel">
      <h2>Change password</h2>
      <p>Changing your password revokes existing access tokens. You will need to sign in again.</p>
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <fieldset [disabled]="saving() || preview()">
          <div class="fields">
            <label class="field">
              <span>Current password</span>
              <input type="password" formControlName="currentPassword"
                autocomplete="current-password" maxlength="72" required />
            </label>
            <label class="field">
              <span>New password</span>
              <input type="password" formControlName="password"
                autocomplete="new-password" minlength="12" maxlength="72" required />
              <small>12–72 characters and at most 72 UTF-8 bytes.</small>
            </label>
            <label class="field">
              <span>Confirm new password</span>
              <input type="password" formControlName="confirmPassword"
                autocomplete="new-password" maxlength="72" required />
            </label>
          </div>
        </fieldset>
        @if (attempted() && form.invalid) {
          <p class="error" role="alert">
            Enter the current password and matching new passwords within the stated limits.
          </p>
        }
        @if (failed()) {
          <p class="error" role="alert">The password was not changed. Review the API notification.</p>
        }
        @if (preview()) {
          <section class="confirmation" aria-label="Confirm password change">
            <h3>Confirm password change</h3>
            <p>All existing tokens will be revoked and you will be signed out.</p>
            <div class="actions">
              <button type="button" class="secondary-button" [disabled]="saving()" (click)="preview.set(false)">
                Continue editing
              </button>
              <button type="button" class="primary-button" [disabled]="saving()" (click)="confirm()">
                {{ saving() ? 'Changing…' : 'Confirm password change' }}
              </button>
            </div>
          </section>
        } @else {
          <div class="actions"><button class="primary-button" type="submit">Review password change</button></div>
        }
      </form>
    </section>
  `,
  styleUrl: './identity.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PasswordChangeComponent {
  private readonly builder = inject(NonNullableFormBuilder);
  private readonly auth = inject(AuthApiService);
  private readonly session = inject(AuthSessionService);
  private readonly notifications = inject(NotificationService);
  readonly saving = signal(false);
  readonly preview = signal(false);
  readonly attempted = signal(false);
  readonly failed = signal(false);
  readonly form = this.builder.group({
    currentPassword: ['', [Validators.required, Validators.maxLength(72)]],
    password: ['', passwordValidators],
    confirmPassword: ['', Validators.required],
  }, { validators: matchingPasswords });

  submit(): void {
    this.attempted.set(true);
    if (this.form.valid && !this.saving()) {
      this.preview.set(true);
    }
  }

  confirm(): void {
    if (!this.preview() || this.form.invalid || this.saving()) {
      return;
    }
    const value = this.form.getRawValue();
    this.saving.set(true);
    this.failed.set(false);
    this.auth.changePassword(value.currentPassword, value.password)
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: () => {
          this.form.reset();
          this.preview.set(false);
          this.notifications.show('Password changed. Sign in again with your new password.', 'success');
          this.session.logout();
        },
        error: () => {
          this.failed.set(true);
          this.preview.set(false);
          this.form.reset();
        },
      });
  }
}
