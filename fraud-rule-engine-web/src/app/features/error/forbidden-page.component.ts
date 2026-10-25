import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-forbidden-page',
  imports: [RouterLink],
  template: `
    <section class="error-page">
      <p class="eyebrow">ACCESS RESTRICTED</p>
      <h1>You don't have access to this area</h1>
      <p>Your account permissions do not allow this action or page.</p>
      <a routerLink="/overview">Return to overview</a>
    </section>
  `,
  styles: `
    .error-page { margin: 4rem auto; max-width: 38rem; }
    .eyebrow { color: var(--brand-red); font-size: .7rem; font-weight: 800; letter-spacing: .13em; }
    h1 { color: var(--brand-blue); font-size: 2rem; }
    p { color: var(--text-muted); line-height: 1.6; }
    a { color: var(--brand-blue); font-weight: 700; }
  `,
})
export class ForbiddenPageComponent {}
