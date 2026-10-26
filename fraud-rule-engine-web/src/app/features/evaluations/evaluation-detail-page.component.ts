import { DatePipe, DecimalPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize, Subscription } from 'rxjs';
import { Evaluation, RuleEvaluation } from '../../core/api/api.models';
import { EvaluationApiService } from '../../core/api/evaluation-api.service';
import { describeReason, humanizeKey, humanizeValue } from '../../shared/labels';
@Component({
  selector: 'app-evaluation-detail-page',
  imports: [DatePipe, DecimalPipe, RouterLink],
  template: `
    <a class="back-link" routerLink="/evaluations" queryParamsHandling="preserve">
      <span aria-hidden="true">←</span> Back to evaluations
    </a>

    @if (loading() && !evaluation()) {
      <section class="state-card" role="status">Loading evaluation details…</section>
    } @else if (loadFailed()) {
      <section class="state-card" role="status">
        <h1>Evaluation unavailable</h1>
        <p>
          This evaluation could not be loaded. It may no longer be available, or the service may be
          unreachable.
        </p>
        <button class="secondary-button" type="button" (click)="retry()">Try again</button>
      </section>
    } @else if (evaluation(); as result) {
      <section class="page-heading">
        <p class="eyebrow">PERSISTED EVALUATION</p>
        <h1>{{ decisionLabel(result.decision) }}</h1>
        <div class="heading-meta">
          <span class="status-chip">{{ statusLabel(result.status) }}</span>
          <span>Service score <strong>{{ result.riskScore | number: '1.0-2' }}</strong></span>
        </div>
      </section>

      <section class="facts-panel" aria-labelledby="transaction-heading">
        <div class="section-heading">
          <div>
            <p class="eyebrow">EVENT FACTS</p>
            <h2 id="transaction-heading">Transaction details</h2>
          </div>
          <span class="category-tag">{{ result.category }}</span>
        </div>
        <dl class="facts-grid">
          <div>
            <dt>Amount</dt>
            <dd>{{ result.amount | number: '1.2-2' }} {{ result.currency }}</dd>
          </div>
          <div>
            <dt>Occurred at (UTC)</dt>
            <dd>{{ result.occurredAt | date: 'yyyy-MM-dd HH:mm:ss' : 'UTC' }} UTC</dd>
          </div>
          <div>
            <dt>Evaluated at (UTC)</dt>
            <dd>{{ result.evaluatedAt | date: 'yyyy-MM-dd HH:mm:ss' : 'UTC' }} UTC</dd>
          </div>
          <div>
            <dt>Event ID</dt>
            <dd class="opaque-id">{{ result.eventId }}</dd>
          </div>
          <div>
            <dt>Evaluation ID</dt>
            <dd class="opaque-id">{{ result.evaluationId }}</dd>
          </div>
        </dl>
      </section>

      <section class="versions-panel" aria-labelledby="versions-heading">
        <div class="section-heading">
          <div>
            <p class="eyebrow">REPRODUCIBILITY</p>
            <h2 id="versions-heading">Captured configuration</h2>
          </div>
        </div>
        <dl class="version-grid">
          <div>
            <dt>Rule configuration snapshot</dt>
            <dd>Version {{ result.ruleConfigurationVersion }}</dd>
          </div>
          @for (entry of referenceDataEntries(result); track entry[0]) {
            <div>
              <dt>{{ entry[0] }} reference data</dt>
              <dd>Version {{ entry[1] }}</dd>
            </div>
          } @empty {
            <div>
              <dt>Reference data</dt>
              <dd>No reference-data version was reported.</dd>
            </div>
          }
        </dl>
      </section>

      <section class="rules-section" aria-labelledby="rules-heading">
        <div class="section-heading">
          <div>
            <p class="eyebrow">EXPLAINABLE OUTCOMES</p>
            <h2 id="rules-heading">Rule evaluation</h2>
            <p class="section-description">
              Outcomes are shown as recorded. Not applicable is not a non-match; failure is an
              operational outcome.
            </p>
          </div>
        </div>
        @if (result.rules.length === 0) {
          <div class="state-card">No per-rule outcomes were reported for this evaluation.</div>
        } @else {
          <div class="rule-list">
            @for (rule of result.rules; track rule.ruleCode + '-' + rule.configurationVersion) {
              <article class="rule-card">
                <header class="rule-heading">
                  <div>
                    <h3>{{ rule.ruleCode }}</h3>
                    <p>Configuration version {{ rule.configurationVersion }}</p>
                  </div>
                  <span [class]="outcomeClass(rule.outcome)">
                    {{ outcomeLabel(rule.outcome) }}
                  </span>
                </header>
                @if (rule.reasonCode) {
                  <p class="reason-code">Reason: {{ reasonDescription(rule) }}</p>
                }
                @if (evidenceEntries(rule).length > 0) {
                  <dl class="evidence-list">
                    @for (entry of evidenceEntries(rule); track entry[0]) {
                      <div>
                        <dt>{{ entry[0] }}</dt>
                        <dd>{{ entry[1] }}</dd>
                      </div>
                    }
                  </dl>
                } @else {
                  <p class="no-evidence">No additional evidence was reported.</p>
                }
              </article>
            }
          </div>
        }
      </section>

      <aside class="assessment-note">
        The score and decision are assessment-service outputs, not calibrated fraud probabilities or
        instructions to authorize, decline, freeze, reverse, or settle a payment. Evidence is
        displayed as text exactly as returned by the API.
      </aside>
    }
  `,
  styleUrl: './evaluation-detail-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EvaluationDetailPageComponent implements OnInit {
  private readonly evaluations = inject(EvaluationApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  readonly evaluation = signal<Evaluation | null>(null);
  readonly loading = signal(false);
  readonly loadFailed = signal(false);
  private evaluationId: string | null = null;
  private evaluationSubscription: Subscription | null = null;

  ngOnInit(): void {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.evaluationId = params.get('evaluationId');
      this.evaluation.set(null);
      this.loadEvaluation();
    });
  }

  decisionLabel(decision: string): string {
    return decision.replaceAll('_', ' ');
  }

  statusLabel(status: string): string {
    return status.replaceAll('_', ' ');
  }

  outcomeLabel(outcome: string): string {
    return outcome.replaceAll('_', ' ');
  }

  outcomeClass(outcome: string): string {
    const knownOutcomes = ['MATCH', 'NO_MATCH', 'NOT_APPLICABLE', 'FAILED'];
    return knownOutcomes.includes(outcome)
      ? `outcome-chip outcome-chip--${outcome.toLowerCase()}`
      : 'outcome-chip';
  }

  reasonDescription(rule: RuleEvaluation): string {
    return describeReason(rule.reasonCode ?? '', rule.evidence, rule.ruleCode);
  }

  evidenceEntries(rule: RuleEvaluation): [string, string][] {
    return Object.entries(rule.evidence).map(([key, value]) => [humanizeKey(key), humanizeValue(value)]);
  }

  referenceDataEntries(evaluation: Evaluation): [string, number][] {
    return Object.entries(evaluation.referenceDataVersions);
  }

  retry(): void {
    this.loadEvaluation();
  }

  private loadEvaluation(): void {
    this.evaluationSubscription?.unsubscribe();
    if (!this.evaluationId) {
      this.evaluation.set(null);
      this.loadFailed.set(true);
      return;
    }
    this.loading.set(true);
    this.loadFailed.set(false);
    this.evaluationSubscription = this.evaluations
      .get(this.evaluationId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (evaluation) => this.evaluation.set(evaluation),
        error: () => this.loadFailed.set(true),
      });
  }
}
