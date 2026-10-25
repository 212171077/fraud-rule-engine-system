import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { UserProfile } from '../../core/api/api.models';
import { UserApiService } from '../../core/api/user-api.service';
import { AuthApiService } from '../../core/auth/auth-api.service';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { UserDetailPageComponent } from './user-detail-page.component';

const account: UserProfile = {
  id: 'synthetic-user', name: 'Synthetic', surname: 'Reviewer', email: 'reviewer@example.test',
  role: 'AUDITOR', status: 'PENDING',
  createdAt: '2026-10-22T12:00:00Z', updatedAt: '2026-10-22T12:00:00Z',
};

describe('UserDetailPageComponent', () => {
  let fixture: ComponentFixture<UserDetailPageComponent>;
  let component: UserDetailPageComponent;
  let users: jasmine.SpyObj<UserApiService>;
  const currentUser = signal({ id: 'synthetic-admin' });
  let logout: jasmine.Spy;

  beforeEach(async () => {
    currentUser.set({ id: 'synthetic-admin' });
    logout = jasmine.createSpy('logout');
    users = jasmine.createSpyObj<UserApiService>('users', ['get', 'update', 'updateRole', 'updateStatus']);
    users.get.and.returnValue(of(account));
    users.updateRole.and.returnValue(of({ ...account, role: 'FRAUD_ANALYST' }));
    users.updateStatus.and.returnValue(of({ ...account, status: 'ACTIVE' }));
    const paramMap = convertToParamMap({ id: account.id });
    await TestBed.configureTestingModule({
      imports: [UserDetailPageComponent],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap: of(paramMap), snapshot: { paramMap } } },
        { provide: UserApiService, useValue: users },
        { provide: AuthApiService, useValue: {} },
        { provide: AuthSessionService, useValue: { user: currentUser, logout } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(UserDetailPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('makes no access mutation until explicit confirmation', () => {
    component.status.setValue('ACTIVE');
    component.reviewStatus();
    expect(users.updateStatus).not.toHaveBeenCalled();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Existing tokens will be revoked');
    component.confirm();
    expect(users.updateStatus).toHaveBeenCalledOnceWith(account.id, 'ACTIVE');
    expect(component.user()?.status).toBe('ACTIVE');
    expect(component.pending()).toBeNull();
  });

  it('does not mutate after cancelling review', () => {
    component.role.setValue('FRAUD_ANALYST');
    component.reviewRole();
    component.pending.set(null);
    component.confirm();
    expect(users.updateRole).not.toHaveBeenCalled();
  });

  it('blocks self role/status changes and signs out after a self profile edit', () => {
    currentUser.set({ id: account.id });
    component.role.setValue('ADMIN');
    component.reviewRole();
    component.reviewStatus();
    component.confirm();
    expect(users.updateRole).not.toHaveBeenCalled();
    expect(users.updateStatus).not.toHaveBeenCalled();
    component.profileSaved(account);
    expect(logout).toHaveBeenCalledTimes(1);
  });

  it('does not offer a role transition for SERVICE identities', () => {
    users.get.and.returnValue(of({ ...account, role: 'SERVICE' }));
    component.load();
    fixture.detectChanges();
    component.reviewRole();
    expect(component.pending()).toBeNull();
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Review role change');
  });

  it('keeps the persisted state on a server denial and exposes the failure', () => {
    users.updateStatus.and.returnValue(throwError(() => new Error('synthetic denial')));
    component.status.setValue('ACTIVE');
    component.reviewStatus();
    component.confirm();
    expect(component.saveFailed()).toBeTrue();
    expect(component.user()?.status).toBe('PENDING');
  });
});
