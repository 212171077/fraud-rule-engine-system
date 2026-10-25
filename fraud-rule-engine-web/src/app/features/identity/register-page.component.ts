import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { UserFormComponent } from './user-form.component';

@Component({
  selector: 'app-register-page',
  imports: [RouterLink, UserFormComponent],
  template: `
    <main class="public-page">
      <a routerLink="/login">Back to sign in</a>
      <section class="panel">
        <p class="eyebrow">SYNTHETIC ASSESSMENT ONLY</p>
        @if (registered()) {
          <h1>Account request received</h1>
          <p role="status">
            Your account is PENDING with the AUDITOR role. An administrator must approve
            it before you can sign in. No access token or fraud-data access has been granted.
          </p>
        } @else {
          <h1>Request an account</h1>
          <p>
            Registration does not grant immediate access. Use synthetic profile details and
            a password that is not used for any real account.
          </p>
          <app-user-form mode="register" (saved)="registered.set(true)" />
        }
      </section>
    </main>
  `,
  styleUrl: './identity.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RegisterPageComponent {
  readonly registered = signal(false);
}
