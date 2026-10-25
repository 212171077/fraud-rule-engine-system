import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { FraudRuleApiService } from '../../core/api/fraud-rule-api.service';
import { RuleEditorComponent } from './rule-editor.component';

describe('rule review and confirmation', () => {
  let fixture: ComponentFixture<RuleEditorComponent>;
  let component: RuleEditorComponent;
  let api: jasmine.SpyObj<FraudRuleApiService>;
  beforeEach(async () => {
    api = jasmine.createSpyObj<FraudRuleApiService>('api', ['create', 'update']);
    api.create.and.returnValue(of({
      ruleCode: 'COUNTRY_RISK', version: 1, enabled: true, scoreContribution: 2.5,
      parameters: { required: 'true' }, effectiveFrom: '2026-10-22T10:00:00Z', effectiveUntil: null,
    }));
    await TestBed.configureTestingModule({
      imports: [RuleEditorComponent], providers: [{ provide: FraudRuleApiService, useValue: api }],
    }).compileComponents();
    fixture = TestBed.createComponent(RuleEditorComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    component.form.patchValue({ ruleCode: 'COUNTRY_RISK', scoreContribution: '2.5', reasonCode: 'ASSESSMENT_TEST' });
  });

  it('accepts numeric input values from a native number control and requires explicit preview', () => {
    component.confirmSave();
    expect(api.create).not.toHaveBeenCalled();
    fixture.detectChanges();
    const input: HTMLInputElement = fixture.nativeElement.querySelector('input[type="number"]');
    input.value = '3.5';
    input.dispatchEvent(new Event('input'));
    component.preparePreview();
    expect(component.preview()).toBeTrue();
    component.confirmSave();
    expect(api.create).toHaveBeenCalledWith(jasmine.objectContaining({ scoreContribution: 3.5 }));
  });

  it('invalidates a reviewed draft when parameters or score change', () => {
    component.preparePreview();
    component.form.controls.scoreContribution.setValue('4');
    expect(component.preview()).toBeFalse();
    component.confirmSave();
    expect(api.create).not.toHaveBeenCalled();
  });

  it('renders the full parameter map in the preview and rejects past effective instants', () => {
    component.addParameter();
    component.parameters.at(0).setValue({ name: 'required', value: 'true' });
    component.preparePreview();
    fixture.detectChanges();
    const preview = (fixture.nativeElement as HTMLElement).querySelector('.confirmation-panel');
    expect(preview?.textContent).toContain('required');
    expect(preview?.textContent).toContain('true');
    component.form.controls.effectiveAt.setValue('2000-01-01T00:00');
    component.preparePreview();
    expect(component.validationError()).toContain('not in the past');
    expect(component.preview()).toBeFalse();
  });
});
