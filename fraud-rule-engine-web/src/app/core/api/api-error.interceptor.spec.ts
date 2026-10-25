import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthSessionService } from '../auth/auth-session.service';
import { NotificationService } from '../notifications/notification.service';
import { apiErrorInterceptor } from './api-error.interceptor';

describe('API failure handling', () => {
  let http: HttpTestingController;
  let client: HttpClient;
  let notices: NotificationService;
  let session: jasmine.SpyObj<AuthSessionService>;

  beforeEach(() => {
    session = jasmine.createSpyObj<AuthSessionService>('session', ['authenticated', 'expireSession']);
    session.authenticated.and.returnValue(true);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]), provideHttpClient(withInterceptors([apiErrorInterceptor])),
        provideHttpClientTesting(), { provide: AuthSessionService, useValue: session },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    client = TestBed.inject(HttpClient);
    notices = TestBed.inject(NotificationService);
  });
  afterEach(() => http.verify());

  for (const status of [400, 404, 409, 500]) {
    it(`surfaces a ${status} Problem Details failure and preserves the error channel`, () => {
      const failed = jasmine.createSpy('failed');
      const success = jasmine.createSpy('success');
      client.get('/api/v1/users').subscribe({ next: success, error: failed });
      http.expectOne('/api/v1/users').flush({
        type: 'about:blank', title: 'Synthetic problem', status,
        detail: 'The synthetic request could not be completed.', code: 'SYNTHETIC_PROBLEM',
      }, { status, statusText: 'Synthetic failure' });
      expect(success).not.toHaveBeenCalled();
      expect(failed).toHaveBeenCalled();
      expect(notices.notices()[0].message).toBe('The synthetic request could not be completed.');
      expect(session.expireSession).not.toHaveBeenCalled();
    });
  }

  it('expires an active session on 401', () => {
    client.get('/api/v1/users').subscribe({ error: () => undefined });
    http.expectOne('/api/v1/users').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(session.expireSession).toHaveBeenCalledTimes(1);
    expect(notices.notices()[0].message).toContain('session has expired');
  });

  it('reports generic login failure without disclosing account status', () => {
    session.authenticated.and.returnValue(false);
    spyOnProperty(TestBed.inject(Router), 'url', 'get').and.returnValue('/login');
    client.post('/api/v1/auth/login', {}).subscribe({ error: () => undefined });
    http.expectOne('/api/v1/auth/login').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(notices.notices()[0].message).toContain('Sign-in failed');
    expect(session.expireSession).not.toHaveBeenCalled();
  });

  it('reports 403 without clearing a valid session', () => {
    client.get('/api/v1/users').subscribe({ error: () => undefined });
    http.expectOne('/api/v1/users').flush({}, { status: 403, statusText: 'Forbidden' });
    expect(notices.notices()[0].message).toContain('permission');
    expect(session.expireSession).not.toHaveBeenCalled();
  });

  it('reports an unreachable API and ignores failures outside the API boundary', () => {
    client.get('/api/v1/users').subscribe({ error: () => undefined });
    http.expectOne('/api/v1/users').error(new ProgressEvent('error'));
    expect(notices.notices()[0].message).toContain('could not be reached');
    client.get('/app-config.json').subscribe({ error: () => undefined });
    http.expectOne('/app-config.json').flush({}, { status: 404, statusText: 'Not found' });
    expect(notices.notices().length).toBe(1);
  });
});
