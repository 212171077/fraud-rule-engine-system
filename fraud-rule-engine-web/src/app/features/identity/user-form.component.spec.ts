import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { UserProfile } from '../../core/api/api.models';
import { UserApiService } from '../../core/api/user-api.service';
import { AuthApiService } from '../../core/auth/auth-api.service';
import { UserFormComponent } from './user-form.component';

const user: UserProfile = {
  id: 'synthetic-user', name: 'Synthetic', surname: 'Reviewer', email: 'reviewer@example.test',
  role: 'AUDITOR', status: 'PENDING',
  createdAt: '2026-10-22T12:00:00Z', updatedAt: '2026-10-22T12:00:00Z',
};

describe('UserFormComponent', () => {
  let fixture: ComponentFixture<UserFormComponent>;
  let component: UserFormComponent;
  let users: jasmine.SpyObj<UserApiService>;
  let auth: jasmine.SpyObj<AuthApiService>;

  beforeEach(async () => {
    users = jasmine.createSpyObj<UserApiService>('UserApiService', ['create', 'update']);
    auth = jasmine.createSpyObj<AuthApiService>('AuthApiService', ['register']);
    users.create.and.returnValue(of(user));
    users.update.and.returnValue(of(user));
    auth.register.and.returnValue(of(user));
    await TestBed.configureTestingModule({
      imports: [UserFormComponent],
      providers: [{ provide: UserApiService, useValue: users }, { provide: AuthApiService, useValue: auth }],
    }).compileComponents();
    fixture = TestBed.createComponent(UserFormComponent);
    component = fixture.componentInstance;
  });

  function fill(mode: 'register' | 'create'): void {
    fixture.componentRef.setInput('mode', mode);
    fixture.detectChanges();
    component.form.patchValue({
      name: user.name, surname: user.surname, email: user.email,
      password: 'synthetic-password-only', confirmPassword: 'synthetic-password-only',
    });
  }

  it('registers only safe fields and clears password inputs on success', () => {
    fill('register');
    component.submit();
    expect(auth.register).toHaveBeenCalledOnceWith({
      name: user.name, surname: user.surname, email: user.email, password: 'synthetic-password-only',
    });
    expect(users.create).not.toHaveBeenCalled();
    expect(component.form.controls.password.value).toBe('');
    expect(component.form.controls.confirmPassword.value).toBe('');
  });

  it('rejects mismatched, short, and over-72-byte passwords', () => {
    fill('register');
    component.form.controls.confirmPassword.setValue('different-synthetic-only');
    component.submit();
    expect(auth.register).not.toHaveBeenCalled();
    for (const password of ['short', 'é'.repeat(40)]) {
      component.form.patchValue({ password, confirmPassword: password });
      component.submit();
      expect(auth.register).not.toHaveBeenCalled();
    }
  });

  it('requires review and confirmation before admin creation', () => {
    fill('create');
    component.submit();
    expect(component.preview()).toBeTrue();
    expect(users.create).not.toHaveBeenCalled();
    component.save();
    expect(users.create).toHaveBeenCalledOnceWith({
      name: user.name, surname: user.surname, email: user.email,
      role: 'AUDITOR', password: 'synthetic-password-only',
    });
  });

  it('omits passwords when creating SERVICE identities', () => {
    fill('create');
    component.form.controls.role.setValue('SERVICE');
    component.submit();
    component.save();
    expect(users.create).toHaveBeenCalledOnceWith({
      name: user.name, surname: user.surname, email: user.email, role: 'SERVICE',
    });
  });

  it('preserves a legacy empty surname by omitting it from an otherwise valid update', () => {
    fixture.componentRef.setInput('user', { ...user, surname: '' });
    fixture.detectChanges();
    component.form.controls.name.setValue('Synthetic Updated');
    component.submit();
    expect(users.update).not.toHaveBeenCalled();
    component.save();
    expect(users.update).toHaveBeenCalledOnceWith(user.id, {
      name: 'Synthetic Updated', email: user.email,
    });
  });

  it('does not allow whitespace-only names', () => {
    fill('register');
    component.form.controls.name.setValue('   ');
    component.submit();
    expect(auth.register).not.toHaveBeenCalled();
  });

  it('shows a failure without emitting a saved account on conflict', () => {
    fill('register');
    auth.register.and.returnValue(throwError(() => new Error('synthetic conflict')));
    const saved = jasmine.createSpy('saved');
    component.saved.subscribe(saved);
    component.submit();
    fixture.detectChanges();
    expect(component.failed()).toBeTrue();
    expect(saved).not.toHaveBeenCalled();
    expect((fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')?.textContent)
      .toContain('did not save');
  });
});
