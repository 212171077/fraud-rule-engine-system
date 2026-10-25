import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize, Subscription } from 'rxjs';
import { EvaluationApiService } from '../../core/api/evaluation-api.service';
import { EvaluationSummary, FraudDecision } from '../../core/api/api.models';
import { defaultDateRange, toUtcInterval } from './evaluation-date-range';

const DECISIONS: readonly FraudDecision[] = ['CLEAR', 'REVIEW', 'HIGH_RISK', 'UNDETERMINED'];

@Component({
  selector: 'app-overview-page',
  imports: [DatePipe, DecimalPipe, ReactiveFormsModule],
  template: `
    <section class="page-heading">
      <p class="eyebrow">ASSESSMENT WORKSPACE</p>
      <h1>Overview</h1>
      <p class="page-description">
        Review persisted assessment activity for a selected UTC date range.
      </p>
    </section>

    <form class="range-panel" [formGroup]="form" (ngSubmit)="loadSummary()">
      <div class="range-heading">
        <div>
          <h2>Evaluation summary</h2>
          <p>UTC dates · the selected end date is included.</p>
        </div>
        <button class="secondary-button" type="submit" [disabled]="loading() || form.invalid">
          @if (loading()) {
            Loading…
          } @else {
            Refresh summary
          }
        </button>
      </div>
      <div class="range-controls">
        <label>
          <span>From date (UTC)</span>
          <input type="date" formControlName="from" required />
        </label>
        <label>
          <span>Through date (UTC)</span>
          <input type="date" formControlName="through" required />
        </label>
        <button class="primary-button" type="submit" [disabled]="loading() || form.invalid">
          Apply dates
        </button>
      </div>
      @if (rangeError()) {
        <p class="inline-error" role="alert">{{ rangeError() }}</p>
      }
      @if (summary(); as result) {
        <p class="interval-note">
          Showing <strong>{{ result.from | date: 'yyyy-MM-dd HH:mm:ss' : 'UTC' }} UTC</strong>
          through, but not including,
          <strong>{{ result.to | date: 'yyyy-MM-dd HH:mm:ss' : 'UTC' }} UTC</strong>.
          Maximum interval: 90 days.
        </p>
      }
    </form>

    @if (loadFailed()) {
      <section class="state-card" role="status">
        <h2>Summary unavailable</h2>
        <p>The evaluation summary could not be loaded. Check the service and retry.</p>
        <button class="secondary-button" type="button" (click)="loadSummary()">Try again</button>
      </section>
    } @else if (loading() && !summary()) {
      <section class="state-card" role="status">Loading evaluation summary…</section>
    } @else if (summary(); as result) {
      <section class="summary-grid" aria-label="Evaluation summary counts">
        <article class="summary-card summary-card--total">
          <span class="summary-label">Total evaluations</span>
          <strong class="summary-value">{{ result.total }}</strong>
          <span class="summary-detail">Persisted in selected interval</span>
        </article>
        @for (decision of decisionNames(result); track decision) {
          <article class="summary-card" [class]="decisionCardClass(decision)">
            <span class="summary-label">{{ decisionLabel(decision) }}</span>
            <strong class="summary-value">{{ result.decisions[decision] ?? 0 }}</strong>
            <span class="summary-detail">
              {{ (result.decisionPercentages[decision] ?? 0) | number: '1.0-2' }}% of total
            </span>
          </article>
        }
      </section>
      <p class="assessment-note">
        Assessment results are persisted service outcomes—not calibrated fraud probabilities or
        payment decisions.
      </p>
    }
  `,
  styleUrl: './overview-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OverviewPageComponent implements OnInit {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly evaluations = inject(EvaluationApiService);
  private readonly initialRange = defaultDateRange();
  private readonly destroyRef = inject(DestroyRef);
  private request: Subscription | null = null;

  readonly form = this.formBuilder.group({
    from: [this.initialRange.from, Validators.required],
    through: [this.initialRange.through, Validators.required],
  });
  readonly summary = signal<EvaluationSummary | null>(null);
  readonly loading = signal(false);
  readonly loadFailed = signal(false);
  readonly rangeError = signal('');

  ngOnInit(): void {
    this.loadSummary();
  }

  decisionLabel(decision: string): string {
    return decision.replaceAll('_', ' ');
  }

  decisionNames(summary: EvaluationSummary): string[] {
    return [
      ...DECISIONS,
      ...Object.keys(summary.decisions).filter(
        (decision) => !DECISIONS.includes(decision as FraudDecision),
      ),
    ];
  }

  decisionCardClass(decision: string): string {
    const knownClass: Partial<Record<FraudDecision, string>> = {
      CLEAR: 'summary-card summary-card--clear',
      REVIEW: 'summary-card summary-card--review',
      HIGH_RISK: 'summary-card summary-card--high-risk',
      UNDETERMINED: 'summary-card summary-card--undetermined',
    };
    return knownClass[decision as FraudDecision] ?? 'summary-card summary-card--unknown';
  }

  loadSummary(): void {
    this.request?.unsubscribe();
    const interval = toUtcInterval(this.form.getRawValue());
    if (!interval) {
      this.rangeError.set('Choose a valid UTC date range no longer than 90 days.');
      return;
    }
    this.rangeError.set('');
    this.summary.set(null);
    this.loading.set(true);
    this.loadFailed.set(false);
    this.request = this.evaluations
      .summary(interval.from, interval.to)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading.set(false)))
      .subscribe({
        next: (summary) => this.summary.set(summary),
        error: () => this.loadFailed.set(true),
      });
  }
}
