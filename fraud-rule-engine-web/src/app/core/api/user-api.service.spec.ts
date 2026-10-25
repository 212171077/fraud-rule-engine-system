import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { UserApiService } from './user-api.service';

describe('UserApiService', () => {
  let service: UserApiService;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(UserApiService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('sends zero-based pagination to the relative API', () => {
    service.list(2, 50).subscribe();
    const req = http.expectOne('/api/v1/users?page=2&size=50');
    expect(req.request.method).toBe('GET');
    req.flush({ content: [], page: 2, size: 50, totalElements: 0, totalPages: 0 });
  });

  it('reads an encoded user ID', () => {
    service.get('synthetic/id').subscribe();
    const req = http.expectOne('/api/v1/users/synthetic%2Fid');
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('creates a credentialless service identity without a password', () => {
    const body = { name: 'Synthetic', surname: 'Service', email: 'service@example.test', role: 'SERVICE' as const };
    service.create(body).subscribe();
    const req = http.expectOne('/api/v1/users');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    expect('password' in req.request.body).toBeFalse();
    req.flush({});
  });

  it('updates only profile fields through the profile endpoint', () => {
    const body = { name: 'Synthetic', surname: 'Reviewer', email: 'reviewer@example.test' };
    service.update('user-id', body).subscribe();
    const req = http.expectOne('/api/v1/users/user-id');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual(body);
    req.flush({});
  });

  it('uses dedicated role and status endpoints', () => {
    service.updateRole('user-id', 'FRAUD_ANALYST').subscribe();
    const role = http.expectOne('/api/v1/users/user-id/role');
    expect(role.request.method).toBe('PATCH');
    expect(role.request.body).toEqual({ role: 'FRAUD_ANALYST' });
    role.flush({});
    service.updateStatus('user-id', 'ACTIVE').subscribe();
    const status = http.expectOne('/api/v1/users/user-id/status');
    expect(status.request.method).toBe('PATCH');
    expect(status.request.body).toEqual({ status: 'ACTIVE' });
    status.flush({});
  });
});
