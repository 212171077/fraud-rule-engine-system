import { inject } from '@angular/core';
import { CanActivateFn, CanActivateChildFn, Router } from '@angular/router';
import { AuthSessionService } from './auth-session.service';
import { AuthModeService } from './auth-mode.service';

export const localAuthGuard: CanActivateFn = () =>
  inject(AuthModeService).local() ? true : inject(Router).createUrlTree(['/login']);

export const authenticatedGuard: CanActivateFn = () => {
  const session = inject(AuthSessionService);
  return session.authenticated() ? true : inject(Router).createUrlTree(['/login']);
};

export const permissionGuard: CanActivateChildFn = (route) => {
  const requiredPermission = route.data['permission'] as string | undefined;
  if (!requiredPermission) {
    return true;
  }
  const session = inject(AuthSessionService);
  return session.hasPermission(requiredPermission) ? true : inject(Router).createUrlTree(['/forbidden']);
};
