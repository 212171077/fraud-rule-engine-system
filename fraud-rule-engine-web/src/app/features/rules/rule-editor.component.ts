import { ChangeDetectionStrategy, Component, effect, inject, input, output, signal } from '@angular/core';
import { FormArray, FormControl, FormGroup, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  CreateRuleRequest,
  PatchRuleRequest,
  RuleConfiguration,
} from '../../core/api/api.models';
import { FraudRuleApiService } from '../../core/api/fraud-rule-api.service';
import {
  RULE_DEFINITIONS,
  RuleDefinition,
  validateParameterDraft,
} from './rule-parameter-catalog';

type ParameterRow = FormGroup<{
  name: FormControl<string>;
  value: FormControl<string>;
}>;

@Component({
  selector: 'app-rule-editor',
  imports: [ReactiveFormsModule],
  template: `
    <section class="editor-panel" aria-labelledby="editor-title">
      <div class="editor-heading">
        <div>
          <p class="eyebrow">{{ configuration() ? 'NEW CONFIGURATION VERSION' : 'NEW RULE' }}</p>
          <h2 id="editor-title">{{ configuration() ? 'Create an updated version' : 'Create rule configuration' }}</h2>
        </div>
        <button class="text-button" type="button" (click)="cancelled.emit()">Close</button>
      </div>

      @if (configuration(); as current) {
        <p class="replacement-note">
          Updating {{ current.ruleCode }} creates version {{ current.version + 1 }}. Submitting the
          parameter list replaces the complete parameter map for that version.
        </p>
      } @else {
        <label class="field">
          <span>Supported rule type</span>
          <select [formControl]="form.controls.ruleCode" required>
            <option value="">Choose a rule type</option>
            @for (definition of definitions; track definition.code) {
              @if (!existingCodes().includes(definition.code)) {
                <option [value]="definition.code">{{ definition.label }} · {{ definition.code }}</option>
              }
            }
          </select>
        </label>
      }

      @if (selectedDefinition(); as definition) {
        <p class="rule-description">{{ definition.description }}</p>
        <form [formGroup]="form" (ngSubmit)="preparePreview()">
          <div class="editor-fields">
            <label class="field">
              <span>Score contribution</span>
              <input
                type="number"
                min="0"
                step="any"
                formControlName="scoreContribution"
                required
              />
              <small>Assessment-scoped contribution; not calibrated for production use.</small>
            </label>
            <label class="field">
              <span>Reason code</span>
              <input
                type="text"
                formControlName="reasonCode"
                maxlength="64"
                pattern="[A-Z][A-Z0-9_]{0,63}"
                autocomplete="off"
                placeholder="e.g. ASSESSMENT_UPDATE"
                required
              />
              <small>Required audit reason: uppercase letters, digits, and underscores.</small>
            </label>
            <label class="field">
              <span>Effective at (optional; browser local time)</span>
              <input type="datetime-local" formControlName="effectiveAt" />
              <small>Leave blank to use the service's current instant.</small>
            </label>
            <label class="enabled-field">
              <input type="checkbox" formControlName="enabled" />
              <span>Enabled for evaluations beginning at the effective instant</span>
            </label>
          </div>

          <fieldset class="parameter-fieldset">
            <legend>Parameters</legend>
            <p class="field-help">
              Only backend-supported parameter names are offered. Supplied values are validated by
              the service before a version is saved.
            </p>
            <div class="parameter-rows" formArrayName="parameters">
              @for (row of parameters.controls; track $index; let index = $index) {
                <div class="parameter-row" [formGroupName]="index">
                  <label class="field parameter-name">
                    <span>Parameter name</span>
                    <input
                      type="text"
                      formControlName="name"
                      [attr.list]="'parameter-options-' + definition.code"
                      autocomplete="off"
                    />
                  </label>
                  <label class="field parameter-value">
                    <span>Value</span>
                    <input type="text" formControlName="value" maxlength="256" />
                  </label>
                  <button
                    class="remove-button"
                    type="button"
                    [attr.aria-label]="'Remove parameter row ' + (index + 1)"
                    (click)="removeParameter(index)"
                  >
                    Remove
                  </button>
                </div>
              }
            </div>
            <datalist [id]="'parameter-options-' + definition.code">
              @for (parameter of definition.parameters; track parameter.name) {
                <option [value]="parameter.name">{{ parameter.description }}</option>
              }
            </datalist>
            <ul class="parameter-reference">
              @for (parameter of definition.parameters; track parameter.name) {
                <li>
                  <code>{{ parameter.name }}</code>
                  @if (parameter.required) {
                    <strong>Required.</strong>
                  }
                  {{ parameter.description }}
                </li>
              }
            </ul>
            <button class="secondary-button" type="button" (click)="addParameter()">
              Add parameter
            </button>
          </fieldset>

          @if (validationError()) {
            <p class="inline-error" role="alert">{{ validationError() }}</p>
          }
          @if (saveFailed()) {
            <p class="inline-error" role="alert">
              The service did not save this configuration. Review any API notification and try
              again; no success is assumed.
            </p>
          }

          @if (preview()) {
            <section class="confirmation-panel" aria-labelledby="confirmation-heading">
              <h3 id="confirmation-heading">Review before saving</h3>
              <p>
                {{ configuration() ? 'Create a new immutable version of' : 'Create' }}
                <strong>{{ form.controls.ruleCode.value }}</strong>
                with score contribution
                <strong>{{ form.controls.scoreContribution.value }}</strong>,
                {{ form.controls.enabled.value ? 'enabled' : 'disabled' }} state, and
                <strong>{{ parameterCount }}</strong> supplied parameter(s).
              </p>
              <p>
                {{ effectiveInstantLabel() }} · reason
                <code>{{ form.controls.reasonCode.value }}</code>
              </p>
              <dl>
                @for (entry of previewParameters(); track entry[0]) {
                  <div><dt>{{ entry[0] }}</dt><dd>{{ entry[1] }}</dd></div>
                }
              </dl>
              <p class="replacement-note">
                This action creates a durable configuration version and audit record. Historical
                evaluation snapshots are not changed.
              </p>
              <div class="confirmation-actions">
                <button class="secondary-button" type="button" (click)="preview.set(false)">
                  Continue editing
                </button>
                <button
                  class="primary-button"
                  type="button"
                  [disabled]="saving()"
                  (click)="confirmSave()"
                >
                  @if (saving()) {
                    Saving…
                  } @else {
                    Confirm and save version
                  }
                </button>
              </div>
            </section>
          } @else {
            <div class="form-actions">
              <button class="secondary-button" type="button" (click)="cancelled.emit()">Cancel</button>
              <button class="primary-button" type="submit" [disabled]="saving()">
                Review changes
              </button>
            </div>
          }
        </form>
      }
    </section>
  `,
  styleUrl: './rule-editor.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RuleEditorComponent {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly rulesApi = inject(FraudRuleApiService);

  readonly configuration = input<RuleConfiguration | null>(null);
  readonly existingCodes = input<string[]>([]);
  readonly saved = output<RuleConfiguration>();
  readonly cancelled = output<void>();
  readonly definitions = RULE_DEFINITIONS;
  readonly preview = signal(false);
  readonly saving = signal(false);
  readonly saveFailed = signal(false);
  readonly validationError = signal('');
  readonly form = this.formBuilder.group({
    ruleCode: [''],
    enabled: [true],
    scoreContribution: ['', Validators.required],
    reasonCode: ['', [Validators.required, Validators.pattern(/^[A-Z][A-Z0-9_]{0,63}$/)]],
    effectiveAt: [''],
    parameters: new FormArray<ParameterRow>([]),
  });
  readonly parameters = this.form.controls.parameters;

  constructor() {
    effect(() => {
      const configuration = this.configuration();
      this.initialize(configuration);
    });
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.preview.set(false));
    this.form.controls.ruleCode.valueChanges.pipe(takeUntilDestroyed()).subscribe((ruleCode) => {
      if (!this.configuration()) {
        this.resetParameters(ruleCode);
      }
    });
  }

  selectedDefinition(): RuleDefinition | undefined {
    return this.definitions.find((definition) => definition.code === this.form.controls.ruleCode.value);
  }

  get parameterCount(): number {
    return this.parameters.controls.filter((row) => row.controls.name.value.trim()).length;
  }

  addParameter(): void {
    this.parameters.push(this.createParameterRow('', ''));
    this.validationError.set('');
  }

  removeParameter(index: number): void {
    this.parameters.removeAt(index);
  }

  preparePreview(): void {
    const ruleCode = this.form.controls.ruleCode.value;
    const score = String(this.form.controls.scoreContribution.value).trim();
    const reasonCode = this.form.controls.reasonCode.value.trim();
    const parameters = this.parameterMap();
    const effectiveAt = this.form.controls.effectiveAt.value;
    if (effectiveAt && (!Number.isFinite(new Date(effectiveAt).getTime()) || new Date(effectiveAt).getTime() < Date.now())) {
      this.validationError.set('Choose a valid effective instant that is not in the past.');
      return;
    }

    if (!ruleCode || !this.selectedDefinition()) {
      this.validationError.set('Select one of the supported rule types.');
      return;
    }
    if (!/^(?:\d+\.?\d*|\.\d+)$/.test(score) || !Number.isFinite(Number(score))) {
      this.validationError.set('Score contribution must be a valid non-negative decimal number.');
      return;
    }
    if (!/^[A-Z][A-Z0-9_]{0,63}$/.test(reasonCode)) {
      this.validationError.set('Enter a reason code using uppercase letters, digits, or underscores.');
      return;
    }
    if (this.parameters.controls.some((row) => !row.controls.name.value.trim())) {
      this.validationError.set('Complete or remove every parameter row before continuing.');
      return;
    }
    if (Object.keys(parameters).length !== this.parameterCount) {
      this.validationError.set('Parameter names must be unique.');
      return;
    }
    const parameterError = validateParameterDraft(ruleCode, parameters);
    if (parameterError) {
      this.validationError.set(parameterError);
      return;
    }

    this.validationError.set('');
    this.saveFailed.set(false);
    this.preview.set(true);
  }

  effectiveInstantLabel(): string {
    const value = this.form.controls.effectiveAt.value;
    if (!value) {
      return 'Effective immediately at the service current instant';
    }
    const instant = new Date(value);
    return `Effective ${instant.toISOString()} UTC`;
  }

  confirmSave(): void {
    if (this.saving() || !this.preview()) {
      return;
    }

    const form = this.form.getRawValue();
    const effectiveAt = form.effectiveAt ? new Date(form.effectiveAt).toISOString() : null;
    const parameters = this.parameterMap();
    this.saving.set(true);
    this.saveFailed.set(false);
    const request = this.configuration()
      ? this.rulesApi.update(this.configuration()!.ruleCode, {
          enabled: form.enabled,
          scoreContribution: Number(form.scoreContribution),
          parameters,
          effectiveAt,
          reasonCode: form.reasonCode,
        } satisfies PatchRuleRequest)
      : this.rulesApi.create({
          ruleCode: form.ruleCode,
          enabled: form.enabled,
          scoreContribution: Number(form.scoreContribution),
          parameters,
          effectiveAt,
          reasonCode: form.reasonCode,
        } satisfies CreateRuleRequest);

    request
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (configuration) => this.saved.emit(configuration),
        error: () => this.saveFailed.set(true),
      });
  }

  previewParameters(): [string, string][] {
    return Object.entries(this.parameterMap());
  }

  private initialize(configuration: RuleConfiguration | null): void {
    this.preview.set(false);
    this.validationError.set('');
    this.saveFailed.set(false);
    if (configuration) {
      this.form.patchValue({
        ruleCode: configuration.ruleCode,
        enabled: configuration.enabled,
        scoreContribution: String(configuration.scoreContribution),
        reasonCode: '',
        effectiveAt: '',
      });
      this.setParameters(configuration.parameters);
      return;
    }

    this.form.reset({
      ruleCode: '',
      enabled: true,
      scoreContribution: '',
      reasonCode: '',
      effectiveAt: '',
    });
    this.parameters.clear();
  }

  private resetParameters(ruleCode: string): void {
    this.parameters.clear();
    const definition = this.definitions.find((item) => item.code === ruleCode);
    for (const parameter of definition?.parameters.filter((item) => item.required) ?? []) {
      this.parameters.push(this.createParameterRow(parameter.name, ''));
    }
  }

  private setParameters(values: Record<string, string>): void {
    this.parameters.clear();
    for (const [name, value] of Object.entries(values)) {
      this.parameters.push(this.createParameterRow(name, value));
    }
    if (Object.keys(values).length === 0) {
      this.resetParameters(this.form.controls.ruleCode.value);
    }
  }

  private parameterMap(): Record<string, string> {
    return Object.fromEntries(
      this.parameters.controls
        .map((row) => [row.controls.name.value.trim(), row.controls.value.value] as const)
        .filter(([name]) => name.length > 0),
    );
  }

  private createParameterRow(name: string, value: string): ParameterRow {
    return this.formBuilder.group({
      name: new FormControl(name, { nonNullable: true, validators: [Validators.required] }),
      value: new FormControl(value, { nonNullable: true, validators: [Validators.maxLength(256)] }),
    });
  }
}
