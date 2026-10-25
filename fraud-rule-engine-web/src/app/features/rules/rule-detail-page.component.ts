import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { combineLatest, finalize, forkJoin, Subscription } from 'rxjs';
import { RuleAuditRecord, RuleConfiguration } from '../../core/api/api.models';
import { FraudRuleApiService } from '../../core/api/fraud-rule-api.service';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { NotificationService } from '../../core/notifications/notification.service';
import { RuleEditorComponent } from './rule-editor.component';
import { RULE_DEFINITIONS } from './rule-parameter-catalog';

@Component({
  selector: 'app-rule-detail-page',
  imports: [DatePipe, DecimalPipe, ReactiveFormsModule, RouterLink, RuleEditorComponent],
  template: `
    <a class="back-link" routerLink="/rules">← Back to rules</a>
    @if (loading() && !loaded()) {
      <section class="state-card" role="status">Loading rule configuration and audit history…</section>
    } @else if (loadFailed()) {
      <section class="state-card" role="status">
        <h1>Rule details unavailable</h1>
        <p>The service could not load this rule's configuration and audit history.</p>
        <button class="secondary-button" type="button" (click)="load()">Try again</button>
      </section>
    } @else {
      <section class="page-heading">
        <p class="eyebrow">RULE CONFIGURATION AND AUDIT</p>
        <h1>{{ ruleCode() }}</h1>
        <p>{{ ruleLabel() }}</p>
      </section>

      @if (configuration(); as rule) {
        <section class="configuration-card">
          <header class="configuration-heading">
            <div>
              <p class="eyebrow">CONFIGURATION AT SELECTED INSTANT</p>
              <h2>Version {{ rule.version }}</h2>
            </div>
            <span [class]="rule.enabled ? 'state-chip state-chip--enabled' : 'state-chip state-chip--disabled'">
              {{ rule.enabled ? 'Enabled' : 'Disabled' }}
            </span>
          </header>
          <dl class="configuration-facts">
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
          <h3>Parameters</h3>
          @if (parameterEntries(rule).length) {
            <dl class="parameter-list">
              @for (entry of parameterEntries(rule); track entry[0]) {
                <div><dt>{{ entry[0] }}</dt><dd>{{ entry[1] }}</dd></div>
              }
            </dl>
          } @else {
            <p class="muted">No parameters are configured.</p>
          }

          @if (canWrite()) {
            <div class="configuration-actions">
              <button class="primary-button" type="button" (click)="editOpen.set(!editOpen())">
                {{ editOpen() ? 'Close editor' : 'Create updated version' }}
              </button>
              @if (rule.enabled) {
                <button
                  class="danger-button"
                  type="button"
                  (click)="deactivateOpen.set(!deactivateOpen())"
                >
                  {{ deactivateOpen() ? 'Close deactivation' : 'Deactivate rule' }}
                </button>
              }
            </div>
            @if (editOpen()) {
              <app-rule-editor
                [configuration]="rule"
                (saved)="updated($event)"
                (cancelled)="editOpen.set(false)"
              />
            }
            @if (deactivateOpen() && rule.enabled) {
              <section class="deactivation-panel">
                <h3>Deactivate this rule</h3>
                <p>
                  Deactivation creates a new disabled version at the effective instant. Earlier
                  versions and evaluation snapshots remain unchanged.
                </p>
                <form [formGroup]="deactivationForm" (ngSubmit)="previewDeactivation()">
                  <label class="field">
                    <span>Reason code</span>
                    <input
                      formControlName="reasonCode"
                      maxlength="64"
                      pattern="[A-Z][A-Z0-9_]{0,63}"
                      placeholder="e.g. ASSESSMENT_CHANGE"
                    />
                  </label>
                  <label class="field">
                    <span>Effective at (optional; browser local time)</span>
                    <input formControlName="effectiveAt" type="datetime-local" />
                  </label>
                  @if (deactivationError()) {
                    <p class="inline-error" role="alert">{{ deactivationError() }}</p>
                  }
                  @if (deactivationPreview()) {
                    <div class="confirmation-panel">
                      <h4>Confirm deactivation</h4>
                      <p>
                        Create a disabled version effective
                        <strong>{{ effectiveAtLabel() }}</strong> with reason
                        <code>{{ deactivationForm.controls.reasonCode.value }}</code>.
                      </p>
                      <div class="configuration-actions">
                        <button
                          class="secondary-button"
                          type="button"
                          (click)="deactivationPreview.set(false)"
                        >
                          Continue editing
                        </button>
                        <button
                          class="danger-button"
                          type="button"
                          [disabled]="deactivating()"
                          (click)="confirmDeactivation()"
                        >
                          {{ deactivating() ? 'Deactivating…' : 'Confirm deactivation' }}
                        </button>
                      </div>
                    </div>
                  } @else {
                    <button class="danger-button" type="submit" [disabled]="deactivating()">
                      Review deactivation
                    </button>
                  }
                </form>
              </section>
            }
          }
        </section>
      } @else {
        <section class="state-card">
          <h2>No current configuration returned</h2>
          <p>
            The service has no effective configuration for this rule at the current instant.
            Available historical audit events are shown below.
          </p>
        </section>
      }

      <section class="history-section">
        <div class="section-heading">
          <div>
            <p class="eyebrow">IMMUTABLE CHANGE HISTORY</p>
            <h2>Audit records</h2>
          </div>
          <button class="secondary-button" type="button" (click)="load()" [disabled]="loading()">
            Refresh
          </button>
        </div>
        @if (history().length === 0) {
          <section class="state-card"><p>No audit records were returned for this rule.</p></section>
        } @else {
          <div class="table-scroll" tabindex="0" role="region" aria-label="Rule audit history">
            <table>
              <thead>
                <tr>
                  <th scope="col">Occurred at (UTC)</th>
                  <th scope="col">Action</th>
                  <th scope="col">Version</th>
                  <th scope="col">Reason</th>
                  <th scope="col">Actor reference</th>
                </tr>
              </thead>
              <tbody>
                @for (entry of history(); track entry.configurationVersion + '-' + entry.occurredAt) {
                  <tr>
                    <td>{{ entry.occurredAt | date: 'yyyy-MM-dd HH:mm:ss' : 'UTC' }}</td>
                    <td>{{ entry.action }}</td>
                    <td>{{ entry.configurationVersion }}</td>
                    <td><code>{{ entry.reasonCode }}</code></td>
                    <td><code>{{ entry.actorReference }}</code></td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <p class="muted">
            Actor references are hashed audit metadata, not personal identity. History is read-only.
          </p>
        }
      </section>
      <aside class="assessment-note">
        This is synthetic assessment configuration—not calibrated fraud policy or a payment
        authorization control. No payment action is available here.
      </aside>
    }
  `,
  styleUrl: './rule-detail-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RuleDetailPageComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly rulesApi = inject(FraudRuleApiService);
  private readonly session = inject(AuthSessionService);
  private readonly notifications = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);
  private request: Subscription | null = null;

  readonly ruleCode = signal('');
  readonly selectedEffectiveAt = signal<string | null>(null);
  readonly configuration = signal<RuleConfiguration | null>(null);
  readonly history = signal<RuleAuditRecord[]>([]);
  readonly loading = signal(false);
  readonly loaded = signal(false);
  readonly loadFailed = signal(false);
  readonly editOpen = signal(false);
  readonly deactivateOpen = signal(false);
  readonly deactivationPreview = signal(false);
  readonly deactivating = signal(false);
  readonly deactivationError = signal('');
  readonly canWrite = signal(this.session.hasPermission('RULES_WRITE'));
  readonly deactivationForm = new FormGroup({
    reasonCode: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^[A-Z][A-Z0-9_]{0,63}$/)],
    }),
    effectiveAt: new FormControl('', { nonNullable: true }),
  });

  constructor() {
    this.deactivationForm.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.deactivationPreview.set(false));
  }

  ngOnInit(): void {
    combineLatest([this.route.paramMap, this.route.queryParamMap]).pipe(
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(([params, query]) => {
      this.ruleCode.set(params.get('ruleCode') ?? '');
      this.selectedEffectiveAt.set(query.get('effectiveAt'));
      this.loaded.set(false);
      this.load();
    });
  }

  ruleLabel(): string {
    const code = this.ruleCode();
    return RULE_DEFINITIONS.find((definition) => definition.code === code)?.label ?? code;
  }

  parameterEntries(rule: RuleConfiguration): [string, string][] {
    return Object.entries(rule.parameters);
  }

  load(): void {
    const code = this.ruleCode();
    if (!code) {
      this.loadFailed.set(true);
      return;
    }
    this.request?.unsubscribe();
    this.loading.set(true);
    this.loadFailed.set(false);
    this.request = forkJoin({
      configurations: this.rulesApi.list(this.selectedEffectiveAt() ?? undefined),
      history: this.rulesApi.history(code),
    })
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading.set(false)))
      .subscribe({
        next: ({ configurations, history }) => {
          this.configuration.set(
            configurations.find((configuration) => configuration.ruleCode === code) ?? null,
          );
          this.history.set(history.history);
          this.loaded.set(true);
        },
        error: () => this.loadFailed.set(true),
      });
  }

  updated(configuration: RuleConfiguration): void {
    this.editOpen.set(false);
    this.notifications.show(
      `${configuration.ruleCode} version ${configuration.version} created.`,
      'success',
    );
    this.showConfigurationAt(configuration.effectiveFrom);
  }

  previewDeactivation(): void {
    const effectiveAt = this.deactivationForm.controls.effectiveAt.value;
    if (effectiveAt && (!Number.isFinite(new Date(effectiveAt).getTime()) || new Date(effectiveAt).getTime() < Date.now())) {
      this.deactivationError.set('Choose a valid effective instant that is not in the past.');
      return;
    }
    if (this.deactivationForm.invalid) {
      this.deactivationError.set('Enter a valid uppercase audit reason code.');
      return;
    }
    this.deactivationError.set('');
    this.deactivationPreview.set(true);
  }

  effectiveAtLabel(): string {
    const value = this.deactivationForm.controls.effectiveAt.value;
    return value ? `${new Date(value).toISOString()} UTC` : 'the service current instant';
  }

  confirmDeactivation(): void {
    const configuration = this.configuration();
    if (!configuration || this.deactivating() || !this.deactivationPreview() || this.deactivationForm.invalid) {
      return;
    }
    const { reasonCode, effectiveAt } = this.deactivationForm.getRawValue();
    this.deactivating.set(true);
    this.deactivationError.set('');
    this.rulesApi
      .deactivate(configuration.ruleCode, {
        reasonCode,
        effectiveAt: effectiveAt ? new Date(effectiveAt).toISOString() : null,
      })
      .pipe(finalize(() => this.deactivating.set(false)))
      .subscribe({
        next: (updated) => {
          this.deactivateOpen.set(false);
          this.deactivationPreview.set(false);
          this.deactivationForm.reset();
          this.notifications.show(`${updated.ruleCode} was deactivated in version ${updated.version}.`, 'success');
          this.showConfigurationAt(updated.effectiveFrom);
        },
        error: () =>
          this.deactivationError.set(
            'The service did not deactivate this rule. Review any API notification and try again.',
          ),
      });
  }

  private showConfigurationAt(effectiveAt: string): void {
    if (this.selectedEffectiveAt() === effectiveAt) {
      this.load();
      return;
    }
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { effectiveAt },
      queryParamsHandling: 'merge',
    });
  }
}
