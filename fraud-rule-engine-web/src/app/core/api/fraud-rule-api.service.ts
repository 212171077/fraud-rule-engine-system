import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE } from './api-base';
import {
  CreateRuleRequest,
  DeactivateRuleRequest,
  PatchRuleRequest,
  RuleConfiguration,
  RuleHistory,
} from './api.models';

@Injectable({ providedIn: 'root' })
export class FraudRuleApiService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = `${API_BASE}/fraud-rules`;

  list(effectiveAt?: string): Observable<RuleConfiguration[]> {
    const params = effectiveAt ? new HttpParams().set('effectiveAt', effectiveAt) : undefined;
    return this.http.get<RuleConfiguration[]>(this.endpoint, { params });
  }

  history(ruleCode: string): Observable<RuleHistory> {
    return this.http.get<RuleHistory>(`${this.endpoint}/${encodeURIComponent(ruleCode)}/history`);
  }

  create(request: CreateRuleRequest): Observable<RuleConfiguration> {
    return this.http.post<RuleConfiguration>(this.endpoint, request);
  }

  update(ruleCode: string, request: PatchRuleRequest): Observable<RuleConfiguration> {
    return this.http.patch<RuleConfiguration>(
      `${this.endpoint}/${encodeURIComponent(ruleCode)}`,
      request,
    );
  }

  deactivate(ruleCode: string, request: DeactivateRuleRequest): Observable<RuleConfiguration> {
    return this.http.delete<RuleConfiguration>(
      `${this.endpoint}/${encodeURIComponent(ruleCode)}`,
      { body: request },
    );
  }
}
