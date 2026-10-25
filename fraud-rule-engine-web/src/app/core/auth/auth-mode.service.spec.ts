import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthModeService } from './auth-mode.service';

describe('AuthModeService', () => {
  let service: AuthModeService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(AuthModeService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('does not enable credentials before explicit configuration', () => {
    expect(service.local()).toBeFalse();
  });

  it('enables local screens only on explicit local configuration', async () => {
    const loaded = service.load();
    http.expectOne('/app-config.json').flush({ authMode: 'local' });
    await loaded;
    expect(service.local()).toBeTrue();
    expect(service.error()).toBeFalse();
  });

  it('keeps local screens disabled for a disabled deployment', async () => {
    const loaded = service.load();
    http.expectOne('/app-config.json').flush({ authMode: 'disabled' });
    await loaded;
    expect(service.local()).toBeFalse();
    expect(service.error()).toBeFalse();
  });

  it('exposes a configuration error rather than enabling local credentials on invalid input', async () => {
    const loaded = service.load();
    http.expectOne('/app-config.json').flush({ authMode: 'external' });
    await loaded;
    expect(service.local()).toBeFalse();
    expect(service.error()).toBeTrue();
  });

  it('exposes a missing configuration error', async () => {
    const loaded = service.load();
    http.expectOne('/app-config.json').flush({}, { status: 404, statusText: 'Not Found' });
    await loaded;
    expect(service.local()).toBeFalse();
    expect(service.error()).toBeTrue();
  });
});
