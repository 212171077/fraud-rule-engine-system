import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
  selector: 'app-workspace-page',
  template: `
    <section class="page-heading">
      <p class="eyebrow">ASSESSMENT WORKSPACE</p>
      <h1>{{ title }}</h1>
      <p class="page-description">{{ description }}</p>
    </section>
    <section class="foundation-card" aria-labelledby="foundation-title">
      <div class="card-icon" aria-hidden="true">✓</div>
      <div>
        <h2 id="foundation-title">Your workspace is ready</h2>
        <p>
          The secure application shell is connected to the assessment service. This feature will be
          delivered in a later roadmap phase.
        </p>
      </div>
      <span class="phase-tag">FOUNDATION</span>
    </section>
    <aside class="assessment-callout">
      <strong>Assessment environment</strong>
      <span>Use synthetic data only. Results are not calibrated production fraud controls.</span>
    </aside>
  `,
  styleUrl: './workspace-page.component.scss',
})
export class WorkspacePageComponent {
  private readonly route = inject(ActivatedRoute);

  readonly title = this.route.snapshot.data['title'] as string;
  readonly description = this.route.snapshot.data['description'] as string;
}
