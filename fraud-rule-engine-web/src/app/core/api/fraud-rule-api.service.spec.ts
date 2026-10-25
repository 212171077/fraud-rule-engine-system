import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  CreateRuleRequest,
  DeactivateRuleRequest,
  PatchRuleRequest,
  RuleConfiguration,
} from './api.models';
import { FraudRuleApiService } from './fraud-rule-api.service';

describe('FraudRuleApiService', () => {
  let service: FraudRuleApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [FraudRuleApiService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(FraudRuleApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists configurations and supports an effective instant filter', () => {
    service.list('2026-10-22T12:00:00.000Z').subscribe();
    const request = http.expectOne(
      (candidate) =>
        candidate.url === '/api/v1/fraud-rules' &&
        candidate.params.get('effectiveAt') === '2026-10-22T12:00:00.000Z',
    );
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('loads immutable history for an encoded rule code', () => {
    service.history('RULE/EXAMPLE').subscribe();
    const request = http.expectOne('/api/v1/fraud-rules/RULE%2FEXAMPLE/history');
    expect(request.request.method).toBe('GET');
    request.flush({ ruleCode: 'RULE/EXAMPLE', history: [] });
  });

  it('creates a configuration with the complete requested payload', () => {
    const body: CreateRuleRequest = {
      ruleCode: 'THRESHOLD_LIMIT',
      enabled: true,
      scoreContribution: 2.5,
      parameters: { amount_threshold_zar: '5000' },
      effectiveAt: '2026-10-22T12:00:00.000Z',
      reasonCode: 'ASSESSMENT_CREATE',
    };
    service.create(body).subscribe();
    const request = http.expectOne('/api/v1/fraud-rules');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(body);
    request.flush(configuration);
  });

  it('updates a version with replacement parameters', () => {
    const body: PatchRuleRequest = {
      enabled: false,
      scoreContribution: 1,
      parameters: { required: 'true' },
      effectiveAt: null,
      reasonCode: 'ASSESSMENT_UPDATE',
    };
    service.update('INTERNAL_WATCHLIST', body).subscribe();
    const request = http.expectOne('/api/v1/fraud-rules/INTERNAL_WATCHLIST');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual(body);
    request.flush(configuration);
  });

  it('sends the deactivation audit payload as the DELETE body', () => {
    const body: DeactivateRuleRequest = {
      effectiveAt: null,
      reasonCode: 'ASSESSMENT_DEACTIVATE',
    };
    service.deactivate('COUNTRY_RISK', body).subscribe();
    const request = http.expectOne('/api/v1/fraud-rules/COUNTRY_RISK');
    expect(request.request.method).toBe('DELETE');
    expect(request.request.body).toEqual(body);
    request.flush(configuration);
  });
});

const configuration: RuleConfiguration = {
  ruleCode: 'THRESHOLD_LIMIT',
  version: 1,
  enabled: true,
  effectiveFrom: '2026-10-22T12:00:00.000Z',
  effectiveUntil: null,
  scoreContribution: 2.5,
  parameters: { amount_threshold_zar: '5000' },
};
