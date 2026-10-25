import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, provideRouter, Router } from '@angular/router';
import { routes } from '../../app.routes';
import { AuthModeService } from './auth-mode.service';
import { AuthSessionService } from './auth-session.service';
import { localAuthGuard, permissionGuard } from './auth.guard';

describe('identity route guards', () => {
  let session: jasmine.SpyObj<AuthSessionService>;
  let router: Router;
  let local: boolean;

  beforeEach(() => {
    local = false;
    session = jasmine.createSpyObj<AuthSessionService>('session', ['hasPermission']);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(), provideRouter([]),
        { provide: AuthSessionService, useValue: session },
        { provide: AuthModeService, useValue: { local: () => local } },
      ],
    });
    router = TestBed.inject(Router);
  });

  it('blocks local lifecycle routes unless explicitly enabled', () => {
    const route = new ActivatedRouteSnapshot();
    const result = TestBed.runInInjectionContext(() => localAuthGuard(route, router.routerState.snapshot));
    expect(result).toEqual(router.createUrlTree(['/login']));
    local = true;
    expect(TestBed.runInInjectionContext(() => localAuthGuard(route, router.routerState.snapshot))).toBeTrue();
  });

  it('denies user administration to roles without USERS_MANAGE', () => {
    const route = new ActivatedRouteSnapshot();
    route.data = { permission: 'USERS_MANAGE' };
    session.hasPermission.and.returnValue(false);
    expect(TestBed.runInInjectionContext(() => permissionGuard(route, router.routerState.snapshot)))
      .toEqual(router.createUrlTree(['/forbidden']));
    session.hasPermission.and.returnValue(true);
    expect(TestBed.runInInjectionContext(() => permissionGuard(route, router.routerState.snapshot))).toBeTrue();
  });

  it('gates both user administration routes and keeps the non-admin profile route separate', () => {
    const children = routes.find((route) => route.path === '')?.children ?? [];
    for (const path of ['users', 'users/:id']) {
      const route = children.find((candidate) => candidate.path === path);
      expect(route?.data?.['permission']).toBe('USERS_MANAGE');
      expect(route?.canActivate).toContain(localAuthGuard);
    }
    expect(children.find((route) => route.path === 'profile')?.data?.['permission']).toBe('OWN_PROFILE_READ');
    expect(routes.find((route) => route.path === 'register')?.canActivate).toContain(localAuthGuard);
  });
});
