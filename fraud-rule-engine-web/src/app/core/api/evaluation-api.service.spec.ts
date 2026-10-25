import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { EvaluationApiService } from './evaluation-api.service';

describe('EvaluationApiService', () => {
  let service: EvaluationApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [EvaluationApiService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(EvaluationApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sends bounded search filters, paging, and sorting to the relative API endpoint', () => {
    service
      .search({
        from: '2026-10-01T00:00:00.000Z',
        to: '2026-10-08T00:00:00.000Z',
        decision: 'REVIEW',
        status: 'COMPLETED',
        eventId: 'synthetic-event-1',
        page: 2,
        size: 50,
        sort: 'riskScore,desc',
      })
      .subscribe();

    const request = http.expectOne((candidate) => candidate.url === '/api/v1/fraud-evaluations');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('from')).toBe('2026-10-01T00:00:00.000Z');
    expect(request.request.params.get('to')).toBe('2026-10-08T00:00:00.000Z');
    expect(request.request.params.get('decision')).toBe('REVIEW');
    expect(request.request.params.get('status')).toBe('COMPLETED');
    expect(request.request.params.get('eventId')).toBe('synthetic-event-1');
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('size')).toBe('50');
    expect(request.request.params.get('sort')).toBe('riskScore,desc');
    request.flush({ content: [], page: 2, size: 50, totalElements: 0, totalPages: 0 });
  });

  it('reads a persisted evaluation detail by its evaluation ID', () => {
    service.get('b3ef5a24-3105-4908-9e30-e167329f740b').subscribe();

    const request = http.expectOne(
      '/api/v1/fraud-evaluations/b3ef5a24-3105-4908-9e30-e167329f740b',
    );
    expect(request.request.method).toBe('GET');
    request.flush({});
  });

  it('requests a summary for the selected UTC interval', () => {
    service.summary('2026-10-01T00:00:00.000Z', '2026-10-08T00:00:00.000Z').subscribe();

    const request = http.expectOne(
      (candidate) =>
        candidate.url === '/api/v1/fraud-evaluations/summary' &&
        candidate.params.get('from') === '2026-10-01T00:00:00.000Z' &&
        candidate.params.get('to') === '2026-10-08T00:00:00.000Z',
    );
    expect(request.request.method).toBe('GET');
    request.flush({
      from: '2026-10-01T00:00:00Z',
      to: '2026-10-08T00:00:00Z',
      total: 0,
      decisions: {},
      decisionPercentages: {},
    });
  });
});
