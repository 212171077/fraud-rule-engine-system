import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize, Subscription } from 'rxjs';
import { RuleConfiguration } from '../../core/api/api.models';
import { FraudRuleApiService } from '../../core/api/fraud-rule-api.service';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { NotificationService } from '../../core/notifications/notification.service';
import { RuleEditorComponent } from './rule-editor.component';
import { RULE_DEFINITIONS } from './rule-parameter-catalog';

@Component({
  selector: 'app-rule-list-page',
  imports: [DatePipe, DecimalPipe, ReactiveFormsModule, RouterLink, RuleEditorComponent],
  template: `
    <section class="page-heading">
      <p class="eyebrow">ASSESSMENT WORKSPACE</p>
      <h1>Rules</h1>
      <p class="page-description">
        Review the effective synthetic rule configurations and their immutable audit history.
      </p>
    </section>

    <section class="scope-panel">
      <div>
        <h2>Effective configuration</h2>
        <p>Leave the instant blank to review the configuration effective now.</p>
      </div>
      <form class="effective-filter" (ngSubmit)="loadRules()">
        <label for="effective-at">Effective at (browser local time)</label>
        <input id="effective-at" type="datetime-local" [formControl]="effectiveAt" />
        <button class="secondary-button" type="submit" [disabled]="loading()">Apply</button>
      </form>
    </section>
    @if (filterError()) { <p role="alert">{{ filterError() }}</p> }

    @if (canWrite()) {
      <div class="create-action">
        <p>Rule changes create new immutable configuration versions and audit records.</p>
        <button
          class="primary-button"
          type="button"
          (click)="createOpen.set(!createOpen())"
          [disabled]="createOpen()"
        >
          Create supported rule
        </button>
      </div>
      @if (createOpen()) {
        <app-rule-editor
          [existingCodes]="configuredCodes()"
          (saved)="created($event)"
          (cancelled)="createOpen.set(false)"
        />
      }
    }

    @if (loading() && !rules()) {
      <section class="state-card" role="status">Loading rule configurations…</section>
    } @else if (loadFailed()) {
      <section class="state-card" role="status">
        <h2>Rule configurations unavailable</h2>
        <p>The service could not load rule configurations.</p>
        <button class="secondary-button" type="button" (click)="loadRules()">Try again</button>
      </section>
    } @else if (rules(); as configurations) {
      @if (configurations.length === 0) {
        <section class="state-card empty-state">
          <h2>No effective rule configurations</h2>
          <p>
            There are no configurations for this instant. Only the six rule types supported by the
            service can be configured.
          </p>
          @if (canWrite() && !createOpen()) {
            <button class="primary-button" type="button" (click)="createOpen.set(true)">
              Create supported rule
            </button>
          }
        </section>
      } @else {
        <section class="rule-grid" aria-label="Effective rule configurations">
          @for (rule of configurations; track rule.ruleCode) {
            <article class="rule-card">
              <header class="rule-heading">
                <div>
                  <p class="rule-category">{{ ruleLabel(rule.ruleCode) }}</p>
                  <h2>{{ rule.ruleCode }}</h2>
                </div>
                <span [class]="rule.enabled ? 'state-chip state-chip--enabled' : 'state-chip state-chip--disabled'">
                  {{ rule.enabled ? 'Enabled' : 'Disabled' }}
                </span>
              </header>
              <dl class="rule-facts">
                <div>
                  <dt>Version</dt>
                  <dd>{{ rule.version }}</dd>
                </div>
                <div>
                  <dt>Score contribution</dt>
                  <dd>{{ rule.scoreContribution | number: '1.0-4' }}</dd>
                </div>
                <div>
                  <dt>Effective from (UTC)</dt>
                  <dd>{{ rule.effectiveFrom | date: 'yyyy-MM-dd HH:mm:ss' : 'UTC' }}</dd>
                </div>
                <div>
                  <dt>Effective until (UTC)</dt>
                  <dd>
                    @if (rule.effectiveUntil) {
                      {{ rule.effectiveUntil | date: 'yyyy-MM-dd HH:mm:ss' : 'UTC' }}
                    } @else {
                      No end instant reported
                    }
                  </dd>
                </div>
              </dl>
              <details class="parameters">
                <summary>View {{ parameterCount(rule) }} parameter(s)</summary>
                @if (parameterEntries(rule).length > 0) {
                  <dl class="parameter-list">
                    @for (entry of parameterEntries(rule); track entry[0]) {
                      <div><dt>{{ entry[0] }}</dt><dd>{{ entry[1] }}</dd></div>
                    }
                  </dl>
                } @else {
                  <p>No parameters are configured.</p>
                }
              </details>
              <a
                class="details-link"
                [routerLink]="['/rules', rule.ruleCode]"
                [queryParams]="selectedEffectiveAt() ? { effectiveAt: selectedEffectiveAt() } : null"
              >
                View configuration and audit history
              </a>
            </article>
          }
        </section>
      }
    }

    <aside class="assessment-note">
      Rule values and score contributions are illustrative assessment configuration, not calibrated
      fraud controls or Capitec production policy. The service validates every change and remains
      the authority for effective versions.
    </aside>
  `,
  styleUrl: './rule-list-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RuleListPageComponent implements OnInit {
  private readonly rulesApi = inject(FraudRuleApiService);
  private readonly session = inject(AuthSessionService);
  private readonly notifications = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private request: Subscription | null = null;

  readonly canWrite = signal(this.session.hasPermission('RULES_WRITE'));
  readonly loading = signal(false);
  readonly loadFailed = signal(false);
  readonly filterError = signal('');
  readonly rules = signal<RuleConfiguration[] | null>(null);
  readonly createOpen = signal(false);
  readonly effectiveAt = new FormControl('', { nonNullable: true });
  readonly selectedEffectiveAt = signal<string | null>(null);
  readonly configuredCodes = signal<string[]>([]);

  ngOnInit(): void {
    this.loadRules();
  }

  loadRules(): void {
    const localInstant = this.effectiveAt.value;
    if (localInstant && !Number.isFinite(new Date(localInstant).getTime())) {
      this.filterError.set('Choose a valid effective instant.');
      return;
    }
    this.filterError.set('');
    this.request?.unsubscribe();
    const effectiveAt = localInstant ? new Date(localInstant).toISOString() : undefined;
    this.selectedEffectiveAt.set(effectiveAt ?? null);
    this.loading.set(true);
    this.loadFailed.set(false);
    this.request = this.rulesApi
      .list(effectiveAt)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading.set(false)))
      .subscribe({
        next: (rules) => {
          this.rules.set(rules);
          this.configuredCodes.set(rules.map((rule) => rule.ruleCode));
        },
        error: () => this.loadFailed.set(true),
      });
  }

  ruleLabel(ruleCode: string): string {
    return RULE_DEFINITIONS.find((rule) => rule.code === ruleCode)?.label ?? ruleCode;
  }

  parameterEntries(rule: RuleConfiguration): [string, string][] {
    return Object.entries(rule.parameters);
  }

  parameterCount(rule: RuleConfiguration): number {
    return Object.keys(rule.parameters).length;
  }

  created(configuration: RuleConfiguration): void {
    this.createOpen.set(false);
    this.notifications.show(`${configuration.ruleCode} version ${configuration.version} created.`, 'success');
    void this.router.navigate(['/rules', configuration.ruleCode], {
      queryParams: { effectiveAt: configuration.effectiveFrom },
    });
  }
}
