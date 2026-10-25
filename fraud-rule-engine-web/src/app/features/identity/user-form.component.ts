import {
  ChangeDetectionStrategy, Component, DestroyRef, effect, inject, input, output, signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize, Observable } from 'rxjs';
import { UserProfile, UserRole } from '../../core/api/api.models';
import { PatchUserProfileRequest, UserApiService } from '../../core/api/user-api.service';
import { AuthApiService } from '../../core/auth/auth-api.service';
import { matchingPasswords, nonBlank, passwordValidators } from './identity-validation';

@Component({
  selector: 'app-user-form',
  imports: [ReactiveFormsModule],
  template: `
    <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
      <fieldset [disabled]="saving() || preview()">
        <legend>{{ mode() === 'profile' ? 'Profile details' : 'Account details' }}</legend>
        <div class="fields">
          <label class="field">
            <span>Name</span>
            <input formControlName="name" autocomplete="given-name" maxlength="100" required />
          </label>
          <label class="field">
            <span>Surname</span>
            <input formControlName="surname" autocomplete="family-name" maxlength="100" />
          </label>
          <label class="field">
            <span>Email address</span>
            <input formControlName="email" type="email" autocomplete="email" maxlength="254" required />
          </label>
          @if (mode() === 'create') {
            <label class="field">
              <span>Role</span>
              <select formControlName="role">
                @for (role of roles; track role) { <option [value]="role">{{ role }}</option> }
              </select>
            </label>
          }
          @if (mode() !== 'profile' && !serviceIdentity()) {
            <label class="field">
              <span>{{ mode() === 'create' ? 'Initial password' : 'Password' }}</span>
              <input formControlName="password" type="password" autocomplete="new-password"
                minlength="12" maxlength="72" required />
              <small>12–72 characters, at most 72 UTF-8 bytes. Never use a real account password.</small>
            </label>
            <label class="field">
              <span>Confirm password</span>
              <input formControlName="confirmPassword" type="password" autocomplete="new-password"
                maxlength="72" required />
            </label>
          }
        </div>
        @if (serviceIdentity()) {
          <p>SERVICE identities have no password and cannot sign in through this website.</p>
        }
      </fieldset>
      @if (validationAttempted() && form.invalid) {
        <p class="error" role="alert">
          Enter a non-blank name and surname (up to 100 characters) and a valid email address.
          @if (mode() !== 'profile' && !serviceIdentity()) {
            Passwords must match and meet the length and UTF-8 byte limits.
          }
          @if (mode() === 'profile' && !user()?.surname) {
            An unchanged legacy empty surname may be left blank.
          }
        </p>
      }
      @if (failed()) {
        <p class="error" role="alert">
          The service did not save these details. Review the API notification and retry.
        </p>
      }
      @if (preview()) {
        <section class="confirmation" aria-label="Confirm account details">
          <h3>{{ mode() === 'profile' ? 'Confirm profile update' : 'Confirm account creation' }}</h3>
          @if (mode() === 'profile') {
          <p>
            Save {{ form.controls.name.value }} {{ form.controls.surname.value }}
            ({{ form.controls.email.value }})?
          </p>
          <p>This revokes the account's existing tokens. If this is your account, you will be signed out.</p>
          } @else {
            <p>
              Create {{ form.controls.name.value }} {{ form.controls.surname.value }}
              ({{ form.controls.email.value }}) as <strong>{{ form.controls.role.value }}</strong>?
              Admin-created accounts are ACTIVE immediately.
            </p>
            <p>
              @if (serviceIdentity()) {
                This identity cannot use password sign-in.
              } @else {
                This grants the selected role's access without a pending-approval step.
              }
            </p>
          }
          <div class="actions">
            <button class="secondary-button" type="button" [disabled]="saving()" (click)="preview.set(false)">
              Continue editing
            </button>
            <button class="primary-button" type="button" [disabled]="saving()" (click)="save()">
              {{ saving() ? 'Saving…' : mode() === 'profile' ? 'Confirm profile update' : 'Confirm and create' }}
            </button>
          </div>
        </section>
      } @else {
        <div class="actions">
          <button class="primary-button" type="submit" [disabled]="saving()">
            {{ saving() ? 'Saving…' : mode() === 'create' ? 'Review account' :
              mode() === 'register' ? 'Request account' : 'Review profile update' }}
          </button>
        </div>
      }
    </form>
  `,
  styleUrl: './identity.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserFormComponent {
  private readonly builder = inject(NonNullableFormBuilder);
  private readonly users = inject(UserApiService);
  private readonly auth = inject(AuthApiService);
  private readonly destroyRef = inject(DestroyRef);
  readonly mode = input<'register' | 'create' | 'profile'>('profile');
  readonly user = input<UserProfile | null>(null);
  readonly saved = output<UserProfile>();
  readonly saving = signal(false);
  readonly preview = signal(false);
  readonly failed = signal(false);
  readonly validationAttempted = signal(false);
  readonly serviceIdentity = signal(false);
  readonly roles: UserRole[] = ['AUDITOR', 'FRAUD_ANALYST', 'ADMIN', 'SERVICE'];
  readonly form = this.builder.group({
    name: ['', [nonBlank, Validators.maxLength(100)]],
    surname: ['', [nonBlank, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    role: this.builder.control<UserRole>('AUDITOR', Validators.required),
    password: [''],
    confirmPassword: [''],
  });

  constructor() {
    effect(() => {
      const user = this.user();
      this.form.reset({
        name: user?.name ?? '',
        surname: user?.surname ?? '',
        email: user?.email ?? '',
        role: user?.role ?? 'AUDITOR',
        password: '',
        confirmPassword: '',
      }, { emitEvent: false });
      this.form.controls.surname.setValidators(
        this.mode() === 'profile' && user?.surname === ''
          ? [Validators.maxLength(100)]
          : [nonBlank, Validators.maxLength(100)],
      );
      this.form.controls.surname.updateValueAndValidity();
      this.configurePasswords();
      this.preview.set(false);
    });
    this.form.controls.role.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.form.controls.password.reset();
      this.form.controls.confirmPassword.reset();
      this.configurePasswords();
    });
  }

  submit(): void {
    this.validationAttempted.set(true);
    this.form.markAllAsTouched();
    if (this.form.invalid || this.saving()) {
      return;
    }
    if (this.mode() !== 'register') {
      this.preview.set(true);
    } else {
      this.save();
    }
  }

  save(): void {
    if (this.form.invalid || this.saving() || (this.mode() !== 'register' && !this.preview())) {
      return;
    }
    const value = this.form.getRawValue();
    const profile = { name: value.name.trim(), surname: value.surname.trim(), email: value.email.trim() };
    let request: Observable<UserProfile>;
    if (this.mode() === 'register') {
      request = this.auth.register({ ...profile, password: value.password });
    } else if (this.mode() === 'create') {
      request = this.users.create({
        ...profile,
        role: value.role,
        ...(value.role === 'SERVICE' ? {} : { password: value.password }),
      });
    } else {
      const user = this.user();
      if (!user) {
        this.failed.set(true);
        return;
      }
      const patch: PatchUserProfileRequest = {
        name: profile.name,
        email: profile.email,
        ...(profile.surname === '' && user.surname === '' ? {} : { surname: profile.surname }),
      };
      request = this.users.update(user.id, patch);
    }
    this.saving.set(true);
    this.failed.set(false);
    request.pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.saving.set(false)),
    ).subscribe({
      next: (user) => {
        this.form.controls.password.reset();
        this.form.controls.confirmPassword.reset();
        this.preview.set(false);
        this.saved.emit(user);
      },
      error: () => this.failed.set(true),
    });
  }

  private configurePasswords(): void {
    const required = this.mode() !== 'profile' &&
      !(this.mode() === 'create' && this.form.controls.role.value === 'SERVICE');
    this.serviceIdentity.set(this.mode() === 'create' && this.form.controls.role.value === 'SERVICE');
    this.form.controls.password.setValidators(required ? passwordValidators : []);
    this.form.controls.confirmPassword.setValidators(required ? [Validators.required] : []);
    this.form.setValidators(required ? matchingPasswords : null);
    this.form.controls.password.updateValueAndValidity();
    this.form.controls.confirmPassword.updateValueAndValidity();
    this.form.updateValueAndValidity();
  }
}
