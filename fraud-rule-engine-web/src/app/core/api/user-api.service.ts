import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE } from './api-base';
import { Page, UserProfile, UserRole, UserStatus } from './api.models';

export interface CreateUserRequest {
  name: string;
  surname: string;
  email: string;
  role: UserRole;
  password?: string | null;
}

export interface PatchUserProfileRequest {
  name?: string | null;
  surname?: string | null;
  email?: string | null;
}

@Injectable({ providedIn: 'root' })
export class UserApiService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = `${API_BASE}/users`;

  list(page = 0, size = 20): Observable<Page<UserProfile>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<UserProfile>>(this.endpoint, { params });
  }

  get(id: string): Observable<UserProfile> {
    return this.http.get<UserProfile>(`${this.endpoint}/${encodeURIComponent(id)}`);
  }

  create(request: CreateUserRequest): Observable<UserProfile> {
    return this.http.post<UserProfile>(this.endpoint, request);
  }

  update(id: string, request: PatchUserProfileRequest): Observable<UserProfile> {
    return this.http.patch<UserProfile>(
      `${this.endpoint}/${encodeURIComponent(id)}`,
      request,
    );
  }

  updateRole(id: string, role: UserRole): Observable<UserProfile> {
    return this.http.patch<UserProfile>(`${this.endpoint}/${encodeURIComponent(id)}/role`, {
      role,
    });
  }

  updateStatus(id: string, status: UserStatus): Observable<UserProfile> {
    return this.http.patch<UserProfile>(`${this.endpoint}/${encodeURIComponent(id)}/status`, {
      status,
    });
  }
}
