import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, Observable, switchMap, tap, throwError } from 'rxjs';
import { CurrentUser, LoginRequest } from '../api/api.models';
import { AuthApiService } from './auth-api.service';

@Injectable({ providedIn: 'root' })
export class AuthSessionService {
  private readonly api = inject(AuthApiService);
  private readonly router = inject(Router);
  private readonly tokenState = signal<string | null>(null);
  private readonly userState = signal<CurrentUser | null>(null);

  readonly user = computed(() => this.userState());
  readonly authenticated = computed(() => this.userState() !== null);
  readonly token = computed(() => this.tokenState());

  login(request: LoginRequest): Observable<CurrentUser> {
    return this.api.login(request).pipe(
      tap((response) => this.tokenState.set(response.accessToken)),
      switchMap(() => this.api.currentUser()),
      tap((user) => this.userState.set(user)),
      catchError((error: unknown) => {
        this.clear();
        return throwError(() => error);
      }),
    );
  }

  hasPermission(permission: string): boolean {
    return this.userState()?.permissions.includes(permission) ?? false;
  }

  logout(): void {
    this.clear();
    void this.router.navigateByUrl('/login');
  }

  expireSession(): void {
    this.clear();
    if (this.router.url !== '/login') {
      void this.router.navigate(['/login'], { queryParams: { expired: 'true' } });
    }
  }

  private clear(): void {
    this.tokenState.set(null);
    this.userState.set(null);
  }
}
