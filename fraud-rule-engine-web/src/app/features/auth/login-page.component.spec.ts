import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthModeService } from '../../core/auth/auth-mode.service';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { LoginPageComponent } from './login-page.component';

describe('LoginPageComponent auth-mode gating', () => {
  let local: boolean;
  let configError: boolean;
  let session: jasmine.SpyObj<AuthSessionService>;

  beforeEach(async () => {
    local = false;
    configError = false;
    session = jasmine.createSpyObj<AuthSessionService>('session', ['login']);
    await TestBed.configureTestingModule({
      imports: [LoginPageComponent],
      providers: [
        provideRouter([]),
        { provide: AuthSessionService, useValue: session },
        { provide: AuthModeService, useValue: { local: () => local, error: () => configError } },
      ],
    }).compileComponents();
  });

  it('offers neither credentials nor registration when local mode is disabled', () => {
    const fixture = TestBed.createComponent(LoginPageComponent);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('form')).toBeNull();
    expect(element.querySelector('a[href="/register"]')).toBeNull();
    fixture.componentInstance.form.setValue({ email: 'synthetic@example.test', password: 'synthetic-only-password' });
    fixture.componentInstance.submit();
    expect(session.login).not.toHaveBeenCalled();
  });

  it('shows a configuration failure explicitly', () => {
    configError = true;
    const fixture = TestBed.createComponent(LoginPageComponent);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')?.textContent)
      .toContain('could not be loaded');
  });

  it('shows the credential form and pending-account registration link when enabled', () => {
    local = true;
    const fixture = TestBed.createComponent(LoginPageComponent);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('form')).not.toBeNull();
    expect(element.querySelector('a[href="/register"]')).not.toBeNull();
  });
});
