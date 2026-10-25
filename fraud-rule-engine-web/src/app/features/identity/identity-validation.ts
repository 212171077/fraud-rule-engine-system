import { AbstractControl, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';

export const nonBlank: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
  typeof control.value === 'string' && control.value.trim().length > 0
    ? null
    : { nonBlank: true };

export const passwordValidators = [
  Validators.required,
  Validators.minLength(12),
  Validators.maxLength(72),
  ((control: AbstractControl): ValidationErrors | null =>
    typeof control.value === 'string' && new TextEncoder().encode(control.value).length > 72
      ? { passwordBytes: true }
      : null) satisfies ValidatorFn,
];

export const matchingPasswords: ValidatorFn = (control) =>
  control.get('password')?.value === control.get('confirmPassword')?.value
    ? null
    : { passwordMismatch: true };
