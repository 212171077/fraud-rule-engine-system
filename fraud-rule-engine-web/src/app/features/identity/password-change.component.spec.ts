import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { AuthApiService } from '../../core/auth/auth-api.service';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { PasswordChangeComponent } from './password-change.component';

describe('PasswordChangeComponent', () => {
  let auth: jasmine.SpyObj<AuthApiService>;
  let session: jasmine.SpyObj<AuthSessionService>;
  let component: PasswordChangeComponent;
  beforeEach(async () => {
    auth = jasmine.createSpyObj<AuthApiService>('auth', ['changePassword']);
    session = jasmine.createSpyObj<AuthSessionService>('session', ['logout']);
    auth.changePassword.and.returnValue(of(undefined));
    await TestBed.configureTestingModule({
      imports: [PasswordChangeComponent],
      providers: [{ provide: AuthApiService, useValue: auth }, { provide: AuthSessionService, useValue: session }],
    }).compileComponents();
    const fixture = TestBed.createComponent(PasswordChangeComponent);
    component = fixture.componentInstance;
    component.form.setValue({
      currentPassword: 'synthetic-current-only',
      password: 'synthetic-new-only',
      confirmPassword: 'synthetic-new-only',
    });
  });

  it('requires confirmation and signs out after actual success', () => {
    component.submit();
    expect(auth.changePassword).not.toHaveBeenCalled();
    component.confirm();
    expect(auth.changePassword).toHaveBeenCalledOnceWith('synthetic-current-only', 'synthetic-new-only');
    expect(session.logout).toHaveBeenCalledTimes(1);
    expect(component.form.controls.password.value).toBe('');
    expect(component.form.controls.currentPassword.value).toBe('');
  });

  it('does not claim success or sign out on a failed API mutation', () => {
    auth.changePassword.and.returnValue(throwError(() => new Error('synthetic failure')));
    component.submit();
    component.confirm();
    expect(component.failed()).toBeTrue();
    expect(session.logout).not.toHaveBeenCalled();
    expect(component.form.controls.password.value).toBe('');
  });
});
