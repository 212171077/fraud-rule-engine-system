import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NgOptimizedImage } from '@angular/common';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { NotificationService } from '../../core/notifications/notification.service';
import { AuthModeService } from '../../core/auth/auth-mode.service';

@Component({
  selector: 'app-login-page',
  imports: [NgOptimizedImage, ReactiveFormsModule, RouterLink],
  template: `
    <main class="login-layout">
      <section class="brand-panel" aria-label="Fraud Rule Engine">
        <div class="brand-mark">
          <span class="brand-red"></span>
          <span class="brand-blue"></span>
        </div>
        <p class="eyebrow">ASSESSMENT WORKSPACE</p>
        <h1>Clear insight.<br />Confident review.</h1>
        <p class="brand-description">
          Explore persisted assessments and understand the evidence behind each result.
        </p>
        <p class="synthetic-note">Synthetic assessment environment</p>
      </section>

      <section class="login-panel">
        <a class="wordmark" routerLink="/login" aria-label="Fraud Rule Engine sign in">
          <img ngSrc="branding/capitec-logo.svg" width="133" height="19" alt="Capitec" priority />
        </a>
        <div class="login-content">
          <p class="eyebrow">WELCOME BACK</p>
          @if (authMode.local()) {
          <h2>Sign in to continue</h2>
          @if (expired()) {
            <p class="session-message" role="status">Your session ended. Sign in again to continue.</p>
          }
          <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
            <label for="email">Email address</label>
            <input
              id="email"
              type="email"
              formControlName="email"
              autocomplete="username"
              inputmode="email"
              required
              [attr.aria-invalid]="form.controls.email.invalid && form.controls.email.touched"
              [attr.aria-describedby]="form.controls.email.invalid && form.controls.email.touched ? 'email-error' : null"
            />
            @if (form.controls.email.invalid && form.controls.email.touched) {
              <span class="field-error" id="email-error">Enter a valid email address.</span>
            }
            <label for="password">Password</label>
            <input
              id="password"
              type="password"
              formControlName="password"
              autocomplete="current-password"
              required
              [attr.aria-invalid]="form.controls.password.invalid && form.controls.password.touched"
              [attr.aria-describedby]="form.controls.password.invalid && form.controls.password.touched ? 'password-error' : null"
            />
            @if (form.controls.password.invalid && form.controls.password.touched) {
              <span class="field-error" id="password-error">Enter your password.</span>
            }
            <button class="submit-button" type="submit" [disabled]="form.invalid || submitting()">
              @if (submitting()) {
                Signing in…
              } @else {
                Sign in
              }
            </button>
          </form>
          <p><a routerLink="/register">Request an assessment account</a></p>
          } @else {
            <h2>Local sign-in unavailable</h2>
            <p role="alert">
              @if (authMode.error()) {
                Authentication configuration could not be loaded. Contact the deployment operator.
              } @else {
                Local credentials are disabled for this deployment. External browser sign-in is not
                configured; contact the deployment operator.
              }
            </p>
          }
          <p class="security-note">
            Access is limited to approved assessment users. Use only synthetic assessment data.
          </p>
        </div>
        <footer>Fraud Rule Engine <span aria-hidden="true">·</span> Assessment environment</footer>
      </section>
    </main>
  `,
  styleUrl: './login-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginPageComponent {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly session = inject(AuthSessionService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly notifications = inject(NotificationService);
  readonly authMode = inject(AuthModeService);

  readonly submitting = signal(false);
  readonly expired = signal(this.route.snapshot.queryParamMap.get('expired') === 'true');
  readonly form = this.formBuilder.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    password: ['', [Validators.required, Validators.maxLength(72)]],
  });

  submit(): void {
    if (!this.authMode.local() || this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.session
      .login(this.form.getRawValue())
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: () => {
          this.notifications.show('Signed in successfully.', 'success');
          const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
          void this.router.navigateByUrl(returnUrl?.startsWith('/') ? returnUrl : '/overview');
        },
        error: () => undefined,
      });
  }
}
