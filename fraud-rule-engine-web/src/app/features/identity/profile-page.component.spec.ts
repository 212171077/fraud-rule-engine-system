import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AuthApiService } from '../../core/auth/auth-api.service';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { UserApiService } from '../../core/api/user-api.service';
import { UserProfile } from '../../core/api/api.models';
import { ProfilePageComponent } from './profile-page.component';

describe('ProfilePageComponent', () => {
  it('requests only the current identity and signs out after an actual profile update', async () => {
    const user: UserProfile = {
      id: 'own-synthetic-id', name: 'Synthetic', surname: 'Reviewer', email: 'own@example.test',
      role: 'AUDITOR', status: 'ACTIVE',
      createdAt: '2026-10-22T12:00:00Z', updatedAt: '2026-10-22T12:00:00Z',
    };
    const users = jasmine.createSpyObj<UserApiService>('users', ['get']);
    users.get.and.returnValue(of(user));
    const logout = jasmine.createSpy('logout');
    await TestBed.configureTestingModule({
      imports: [ProfilePageComponent],
      providers: [
        { provide: UserApiService, useValue: users },
        { provide: AuthApiService, useValue: {} },
        { provide: AuthSessionService, useValue: {
          user: () => user, hasPermission: () => true, logout,
        } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(ProfilePageComponent);
    fixture.detectChanges();
    expect(users.get).toHaveBeenCalledOnceWith(user.id);
    expect(logout).not.toHaveBeenCalled();
    fixture.componentInstance.saved(user);
    expect(logout).toHaveBeenCalledTimes(1);
  });
});
