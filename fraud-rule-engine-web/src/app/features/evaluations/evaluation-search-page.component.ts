import { DatePipe, DecimalPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize, Subscription } from 'rxjs';
import {
  EvaluationListItem,
  FraudDecision,
  Page,
  ProcessingStatus,
} from '../../core/api/api.models';
import { EvaluationApiService, EvaluationSearch } from '../../core/api/evaluation-api.service';
import { defaultDateRange, toUtcInterval } from './evaluation-date-range';

const DECISIONS: readonly FraudDecision[] = ['CLEAR', 'REVIEW', 'HIGH_RISK', 'UNDETERMINED'];
const STATUSES: readonly ProcessingStatus[] = ['RECEIVED', 'EVALUATING', 'COMPLETED', 'FAILED'];
const SORTS = [
  'evaluatedAt,desc',
  'evaluatedAt,asc',
  'riskScore,desc',
  'riskScore,asc',
] as const;
type EvaluationSort = (typeof SORTS)[number];
const PAGE_SIZES = [20, 50, 100] as const;

@Component({
  selector: 'app-evaluation-search-page',
  imports: [DatePipe, DecimalPipe, ReactiveFormsModule, RouterLink],
  template: `
    <section class="page-heading">
      <p class="eyebrow">ASSESSMENT WORKSPACE</p>
      <h1>Evaluations</h1>
      <p class="page-description">
        Search persisted outcomes and open an evaluation to review its recorded evidence.
      </p>
    </section>

    <form class="filters" [formGroup]="form" (ngSubmit)="applyFilters()">
      <div class="filters-heading">
        <div>
          <h2>Search filters</h2>
          <p>Dates are interpreted as UTC calendar days; maximum interval is 90 days.</p>
        </div>
        <button class="text-button" type="button" (click)="clearFilters()">Clear filters</button>
      </div>
      <div class="filter-grid">
        <label>
          <span>From (UTC)</span>
          <input type="date" formControlName="from" required />
        </label>
        <label>
          <span>Through (UTC)</span>
          <input type="date" formControlName="through" required />
        </label>
        <label>
          <span>Decision</span>
          <select formControlName="decision">
            <option value="">All decisions</option>
            @for (decision of decisions; track decision) {
              <option [value]="decision">{{ decisionLabel(decision) }}</option>
            }
          </select>
        </label>
        <label>
          <span>Processing status</span>
          <select formControlName="status">
            <option value="">All statuses</option>
            @for (status of statuses; track status) {
              <option [value]="status">{{ statusLabel(status) }}</option>
            }
          </select>
        </label>
        <label class="event-filter">
          <span>Event ID</span>
          <input
            type="search"
            formControlName="eventId"
            maxlength="128"
            autocomplete="off"
            placeholder="Enter an event ID"
          />
        </label>
        <label>
          <span>Sort by</span>
          <select formControlName="sort">
            <option value="evaluatedAt,desc">Evaluation time · newest</option>
            <option value="evaluatedAt,asc">Evaluation time · oldest</option>
            <option value="riskScore,desc">Service score · highest</option>
            <option value="riskScore,asc">Service score · lowest</option>
          </select>
        </label>
        <label>
          <span>Rows per page</span>
          <select formControlName="size">
            @for (size of pageSizes; track size) {
              <option [value]="size">{{ size }}</option>
            }
          </select>
        </label>
        <button class="primary-button apply-button" type="submit" [disabled]="loading()">
          Apply filters
        </button>
      </div>
      @if (filterError()) {
        <p class="inline-error" role="alert">{{ filterError() }}</p>
      }
      <p class="interval-note">
        Query interval: [{{ queryInterval().from }}, {{ queryInterval().to }}) UTC.
      </p>
    </form>

    <section class="results-section" aria-labelledby="results-heading">
      <div class="results-heading">
        <div>
          <h2 id="results-heading">Persisted evaluations</h2>
          @if (results(); as page) {
            <p>{{ page.totalElements }} result{{ page.totalElements === 1 ? '' : 's' }}</p>
          }
        </div>
        <button class="secondary-button" type="button" (click)="refresh()" [disabled]="loading()">
          @if (loading()) {
            Refreshing…
          } @else {
            Refresh results
          }
        </button>
      </div>

      @if (loading() && !results()) {
        <div class="state-card" role="status">Loading evaluations…</div>
      } @else if (loadFailed()) {
        <div class="state-card" role="status">
          <h3>Evaluations unavailable</h3>
          <p>The search request could not be completed. Check the service and retry.</p>
          <button class="secondary-button" type="button" (click)="refresh()">Try again</button>
        </div>
      } @else if (results(); as page) {
        @if (page.content.length === 0) {
          <div class="state-card empty-state">
            <h3>{{ page.page > 0 ? 'No evaluations on this page' : 'No evaluations found' }}</h3>
            <p>Return to the first page, widen the date range, or remove filters.</p>
            @if (page.page > 0) {
              <button class="secondary-button" type="button" (click)="changePage(0)">First page</button>
            }
          </div>
        } @else {
          <div class="table-scroll" tabindex="0" role="region" aria-label="Evaluation results">
            <table>
              <thead>
                <tr>
                  <th scope="col">Decision / status</th>
                  <th scope="col">Category</th>
                  <th scope="col">Event ID</th>
                  <th scope="col">Occurred (UTC)</th>
                  <th scope="col">Evaluated (UTC)</th>
                  <th scope="col">Service score</th>
                  <th scope="col">Details</th>
                </tr>
              </thead>
              <tbody>
                @for (item of page.content; track item.evaluationId) {
                  <tr>
                    <td>
                      <span class="decision-label">{{ decisionLabel(item.decision) }}</span>
                      <span class="status-label">{{ statusLabel(item.status) }}</span>
                    </td>
                    <td>{{ item.category }}</td>
                    <td class="event-id">{{ item.eventId }}</td>
                    <td>{{ item.occurredAt | date: 'yyyy-MM-dd HH:mm:ss' : 'UTC' }}</td>
                    <td>{{ item.evaluatedAt | date: 'yyyy-MM-dd HH:mm:ss' : 'UTC' }}</td>
                    <td class="score">{{ item.riskScore | number: '1.0-2' }}</td>
                    <td>
                      <a
                        class="details-link"
                        [routerLink]="['/evaluations', item.evaluationId]"
                        queryParamsHandling="preserve"
                        [attr.aria-label]="'Open evaluation for event ' + item.eventId"
                        >View</a
                      >
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <div class="mobile-results">
            @for (item of page.content; track item.evaluationId) {
              <article class="result-card">
                <div class="result-card-heading">
                  <div>
                    <strong>{{ decisionLabel(item.decision) }}</strong>
                    <span class="status-label">{{ statusLabel(item.status) }}</span>
                  </div>
                  <span class="score">Score {{ item.riskScore | number: '1.0-2' }}</span>
                </div>
                <dl>
                  <div><dt>Category</dt><dd>{{ item.category }}</dd></div>
                  <div><dt>Event ID</dt><dd class="event-id">{{ item.eventId }}</dd></div>
                  <div>
                    <dt>Occurred (UTC)</dt>
                    <dd>{{ item.occurredAt | date: 'yyyy-MM-dd HH:mm:ss' : 'UTC' }}</dd>
                  </div>
                  <div>
                    <dt>Evaluated (UTC)</dt>
                    <dd>{{ item.evaluatedAt | date: 'yyyy-MM-dd HH:mm:ss' : 'UTC' }}</dd>
                  </div>
                </dl>
                <a
                  class="details-link"
                  [routerLink]="['/evaluations', item.evaluationId]"
                  queryParamsHandling="preserve"
                  [attr.aria-label]="'Open evaluation for event ' + item.eventId"
                  >View evaluation details</a
                >
              </article>
            }
          </div>
        }

        <nav class="pagination" aria-label="Evaluation result pages">
          <button
            class="secondary-button"
            type="button"
            (click)="changePage(page.page - 1)"
            [disabled]="loading() || page.page <= 0"
          >
            Previous
          </button>
          <span>Page {{ page.page + 1 }} of {{ page.totalPages || 1 }}</span>
          <button
            class="secondary-button"
            type="button"
            (click)="changePage(page.page + 1)"
            [disabled]="loading() || page.page + 1 >= page.totalPages"
          >
            Next
          </button>
        </nav>
      }
    </section>
    <p class="assessment-note">
      Service scores are not fraud probabilities or payment decisions. Only fields returned by the
      service are shown; event IDs are opaque identifiers.
    </p>
  `,
  styleUrl: './evaluation-search-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EvaluationSearchPageComponent implements OnInit {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly evaluations = inject(EvaluationApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly decisions = DECISIONS;
  readonly statuses = STATUSES;
  readonly pageSizes = PAGE_SIZES;
  readonly form = this.formBuilder.group({
    from: ['', Validators.required],
    through: ['', Validators.required],
    decision: [''],
    status: [''],
    eventId: ['', Validators.maxLength(128)],
    sort: ['evaluatedAt,desc'],
    size: ['20'],
  });
  readonly results = signal<Page<EvaluationListItem> | null>(null);
  readonly loading = signal(false);
  readonly loadFailed = signal(false);
  readonly filterError = signal('');
  readonly queryInterval = signal({ from: '', to: '' });
  private latestSearch: EvaluationSearch | null = null;
  private searchSubscription: Subscription | null = null;

  ngOnInit(): void {
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.searchSubscription?.unsubscribe();
      this.latestSearch = null;
      this.filterError.set('');
      const defaultRange = defaultDateRange();
      const range = {
        from: params.get('from') ?? defaultRange.from,
        through: params.get('through') ?? defaultRange.through,
      };
      const interval = toUtcInterval(range);
      if (!interval) {
        this.form.patchValue(range);
        this.filterError.set('The saved date range is invalid. Choose a range no longer than 90 days.');
        this.results.set(null);
        return;
      }

      const decision = params.get('decision') ?? '';
      const status = params.get('status') ?? '';
      const sort = params.get('sort') ?? 'evaluatedAt,desc';
      const size = params.get('size') ?? '20';
      const page = Number(params.get('page') ?? 0);
      const eventId = params.get('eventId') ?? '';
      const criteria: EvaluationSearch = {
        ...interval,
        ...(this.isDecision(decision) ? { decision } : {}),
        ...(this.isStatus(status) ? { status } : {}),
        ...(eventId ? { eventId } : {}),
        page: Number.isInteger(page) && page >= 0 ? page : 0,
        size: this.isPageSize(size) ? Number(size) : 20,
        sort: this.isSort(sort) ? sort : 'evaluatedAt,desc',
      };
      this.form.patchValue(
        {
          ...range,
          decision: this.isDecision(decision) ? decision : '',
          status: this.isStatus(status) ? status : '',
          eventId,
          sort: criteria.sort,
          size: String(criteria.size),
        },
        { emitEvent: false },
      );
      this.latestSearch = criteria;
      this.queryInterval.set(interval);
      this.loadResults(criteria);
    });
  }

  decisionLabel(decision: FraudDecision): string {
    return decision.replaceAll('_', ' ');
  }

  statusLabel(status: ProcessingStatus): string {
    return status.replaceAll('_', ' ');
  }

  applyFilters(): void {
    const formValue = this.form.getRawValue();
    const interval = toUtcInterval({ from: formValue.from, through: formValue.through });
    if (!interval) {
      this.filterError.set('Choose valid UTC dates in order and within a 90-day interval.');
      return;
    }
    if (formValue.eventId.trim().length > 128) {
      this.filterError.set('Event ID must be 128 characters or fewer.');
      return;
    }

    this.filterError.set('');
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        from: formValue.from,
        through: formValue.through,
        decision: formValue.decision || null,
        status: formValue.status || null,
        eventId: formValue.eventId.trim() || null,
        sort: this.isSort(formValue.sort) ? formValue.sort : 'evaluatedAt,desc',
        size: this.isPageSize(formValue.size) ? formValue.size : '20',
        page: null,
      },
    });
  }

  clearFilters(): void {
    this.form.reset({
      ...defaultDateRange(),
      decision: '',
      status: '',
      eventId: '',
      sort: 'evaluatedAt,desc',
      size: '20',
    });
    this.applyFilters();
  }

  refresh(): void {
    if (this.latestSearch) {
      this.loadResults(this.latestSearch);
    }
  }

  changePage(page: number): void {
    if (page < 0 || !this.latestSearch || (page !== 0 && page >= (this.results()?.totalPages ?? 0))) {
      return;
    }
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { page },
      queryParamsHandling: 'merge',
    });
  }

  private loadResults(criteria: EvaluationSearch): void {
    this.searchSubscription?.unsubscribe();
    this.results.set(null);
    this.loading.set(true);
    this.loadFailed.set(false);
    this.searchSubscription = this.evaluations
      .search(criteria)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (results) => this.results.set(results),
        error: () => this.loadFailed.set(true),
      });
  }

  private isDecision(value: string): value is FraudDecision {
    return DECISIONS.includes(value as FraudDecision);
  }

  private isStatus(value: string): value is ProcessingStatus {
    return STATUSES.includes(value as ProcessingStatus);
  }

  private isSort(value: string): value is EvaluationSort {
    return SORTS.includes(value as EvaluationSort);
  }

  private isPageSize(value: string): boolean {
    return PAGE_SIZES.some((size) => String(size) === value);
  }
}
