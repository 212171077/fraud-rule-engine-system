import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { BehaviorSubject, of, Subject } from 'rxjs';
import { EvaluationApiService } from '../../core/api/evaluation-api.service';
import { EvaluationListItem, Page } from '../../core/api/api.models';
import { EvaluationSearchPageComponent } from './evaluation-search-page.component';

describe('evaluation filters and pagination', () => {
  let params: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let api: jasmine.SpyObj<EvaluationApiService>;
  const empty = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };

  beforeEach(async () => {
    params = new BehaviorSubject(convertToParamMap({
      from: '2026-10-01', through: '2026-10-02', page: '2', size: '50', sort: 'riskScore,desc',
    }));
    api = jasmine.createSpyObj<EvaluationApiService>('api', ['search']);
    api.search.and.returnValue(of(empty));
    await TestBed.configureTestingModule({
      imports: [EvaluationSearchPageComponent],
      providers: [
        provideRouter([]), { provide: EvaluationApiService, useValue: api },
        { provide: ActivatedRoute, useValue: { queryParamMap: params } },
      ],
    }).compileComponents();
  });

  it('loads URL criteria as half-open UTC days and resets pagination when applying filters', () => {
    const fixture = TestBed.createComponent(EvaluationSearchPageComponent);
    fixture.detectChanges();
    expect(api.search).toHaveBeenCalledWith(jasmine.objectContaining({
      from: '2026-10-01T00:00:00.000Z', to: '2026-10-03T00:00:00.000Z',
      page: 2, size: 50, sort: 'riskScore,desc',
    }));
    const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    fixture.componentInstance.form.controls.eventId.setValue(' synthetic-event ');
    fixture.componentInstance.applyFilters();
    expect(navigate).toHaveBeenCalledWith([], jasmine.objectContaining({
      queryParams: jasmine.objectContaining({ eventId: 'synthetic-event', page: null }),
    }));
  });

  it('blocks invalid dates without sending another query and recovers on a valid URL', () => {
    const fixture = TestBed.createComponent(EvaluationSearchPageComponent);
    fixture.detectChanges();
    params.next(convertToParamMap({ from: '2026-01-01', through: '2026-10-01' }));
    expect(api.search).toHaveBeenCalledTimes(1);
    expect(fixture.componentInstance.filterError()).toContain('invalid');
    fixture.componentInstance.refresh();
    expect(api.search).toHaveBeenCalledTimes(1);
    params.next(convertToParamMap({ from: '2026-10-01', through: '2026-10-01' }));
    expect(fixture.componentInstance.filterError()).toBe('');
    expect(api.search).toHaveBeenCalledTimes(2);
  });

  it('cancels an in-flight request when navigating to an invalid range', () => {
    const pending = new Subject<Page<EvaluationListItem>>();
    api.search.and.returnValue(pending);
    const fixture = TestBed.createComponent(EvaluationSearchPageComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.loading()).toBeTrue();
    params.next(convertToParamMap({ from: 'invalid', through: 'invalid' }));
    pending.next(empty);
    expect(fixture.componentInstance.results()).toBeNull();
    expect(fixture.componentInstance.loading()).toBeFalse();
  });

  it('allows returning to page zero when the requested page has no results', () => {
    api.search.and.returnValue(of({ ...empty, page: 99 }));
    const fixture = TestBed.createComponent(EvaluationSearchPageComponent);
    fixture.detectChanges();
    const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    fixture.componentInstance.changePage(0);
    expect(navigate).toHaveBeenCalledWith([], jasmine.objectContaining({ queryParams: { page: 0 } }));
  });
});
