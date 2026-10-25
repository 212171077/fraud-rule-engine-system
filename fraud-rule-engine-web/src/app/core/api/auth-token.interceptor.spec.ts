import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthSessionService } from '../auth/auth-session.service';
import { authTokenInterceptor } from './auth-token.interceptor';

describe('bearer token request boundary', () => {
  it('attaches only to same-origin relative API requests', () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authTokenInterceptor])), provideHttpClientTesting(),
        { provide: AuthSessionService, useValue: { token: () => 'synthetic-token-not-a-jwt' } },
      ],
    });
    const client = TestBed.inject(HttpClient);
    const http = TestBed.inject(HttpTestingController);
    for (const url of ['/api/v1/users', '/app-config.json', 'https://example.test/api/v1/users', '//example.test/api/v1/users']) {
      client.get(url).subscribe();
      const request = http.expectOne(url);
      expect(request.request.headers.get('Authorization'))
        .toBe(url === '/api/v1/users' ? 'Bearer synthetic-token-not-a-jwt' : null);
      request.flush({});
    }
    http.verify();
  });
});
