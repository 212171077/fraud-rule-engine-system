import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE } from '../api/api-base';
import { CurrentUser, LoginRequest, LoginResponse, UserProfile } from '../api/api.models';

export interface RegisterRequest {
  name: string;
  surname: string;
  email: string;
  password: string;
}

@Injectable({ providedIn: 'root' })
export class AuthApiService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = `${API_BASE}/auth`;

  login(request: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.endpoint}/login`, request);
  }

  currentUser(): Observable<CurrentUser> {
    return this.http.get<CurrentUser>(`${this.endpoint}/me`);
  }

  register(request: RegisterRequest): Observable<UserProfile> {
    return this.http.post<UserProfile>(`${this.endpoint}/register`, request);
  }

  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.patch<void>(`${this.endpoint}/password`, {
      currentPassword,
      newPassword,
    });
  }
}
