import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { CurrentUser, LoginResponse } from '../api/api.models';
import { AuthApiService } from './auth-api.service';
import { AuthSessionService } from './auth-session.service';

describe('in-memory authentication lifecycle', () => {
  let auth: jasmine.SpyObj<AuthApiService>;
  let session: AuthSessionService;
  const user: CurrentUser = {
    id: 'synthetic-user', name: 'Synthetic', surname: 'Reviewer', email: 'reviewer@example.test',
    role: 'AUDITOR', permissions: ['RULES_READ'],
  };
  beforeEach(() => {
    auth = jasmine.createSpyObj<AuthApiService>('auth', ['login', 'currentUser']);
    auth.login.and.returnValue(of({
      accessToken: 'synthetic-token-not-a-jwt', tokenType: 'Bearer', expiresIn: 300,
    } as LoginResponse));
    auth.currentUser.and.returnValue(of(user));
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthApiService, useValue: auth }],
    });
    session = TestBed.inject(AuthSessionService);
  });

  it('takes permissions from /me and clears the token on logout', () => {
    session.login({ email: user.email, password: 'synthetic-only-password' }).subscribe();
    expect(session.authenticated()).toBeTrue();
    expect(session.hasPermission('RULES_READ')).toBeTrue();
    expect(session.hasPermission('USERS_MANAGE')).toBeFalse();
    session.logout();
    expect(session.user()).toBeNull();
    expect(session.token()).toBeNull();
  });

  it('discards an issued token if loading /me fails', () => {
    auth.currentUser.and.returnValue(throwError(() => new Error('synthetic failure')));
    session.login({ email: user.email, password: 'synthetic-only-password' }).subscribe({ error: () => undefined });
    expect(session.authenticated()).toBeFalse();
    expect(session.token()).toBeNull();
  });
});
