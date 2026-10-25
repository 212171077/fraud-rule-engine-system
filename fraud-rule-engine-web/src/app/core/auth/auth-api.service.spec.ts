import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthApiService } from './auth-api.service';

describe('AuthApiService account lifecycle', () => {
  let service: AuthApiService;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(AuthApiService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('registers without client-assigned role or status', () => {
    const body = { name: 'Synthetic', surname: 'Reviewer', email: 'reviewer@example.test', password: 'synthetic-test-only-42' };
    service.register(body).subscribe();
    const req = http.expectOne('/api/v1/auth/register');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    expect('role' in req.request.body).toBeFalse();
    expect('status' in req.request.body).toBeFalse();
    req.flush({ status: 'PENDING', role: 'AUDITOR' });
  });

  it('sends current and new password without a confirmation field', () => {
    service.changePassword('synthetic-old-only-42', 'synthetic-new-only-42').subscribe();
    const req = http.expectOne('/api/v1/auth/password');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({
      currentPassword: 'synthetic-old-only-42',
      newPassword: 'synthetic-new-only-42',
    });
    req.flush(null);
  });
});
