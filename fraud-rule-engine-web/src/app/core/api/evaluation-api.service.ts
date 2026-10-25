import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE } from './api-base';
import { Evaluation, EvaluationListItem, EvaluationSummary, FraudDecision, Page, ProcessingStatus } from './api.models';

export interface EvaluationSearch {
  from: string;
  to: string;
  decision?: FraudDecision;
  status?: ProcessingStatus;
  eventId?: string;
  page?: number;
  size?: number;
  sort?: 'evaluatedAt,asc' | 'evaluatedAt,desc' | 'riskScore,asc' | 'riskScore,desc';
}

@Injectable({ providedIn: 'root' })
export class EvaluationApiService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = `${API_BASE}/fraud-evaluations`;

  search(criteria: EvaluationSearch): Observable<Page<EvaluationListItem>> {
    let params = new HttpParams().set('from', criteria.from).set('to', criteria.to);
    for (const key of ['decision', 'status', 'eventId', 'page', 'size', 'sort'] as const) {
      const value = criteria[key];
      if (value !== undefined && value !== '') {
        params = params.set(key, value);
      }
    }
    return this.http.get<Page<EvaluationListItem>>(this.endpoint, { params });
  }

  get(evaluationId: string): Observable<Evaluation> {
    return this.http.get<Evaluation>(`${this.endpoint}/${encodeURIComponent(evaluationId)}`);
  }

  summary(from: string, to: string): Observable<EvaluationSummary> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http.get<EvaluationSummary>(`${this.endpoint}/summary`, { params });
  }
}
