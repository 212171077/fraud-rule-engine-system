import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { isProblemDetails } from './api.models';
import { AuthSessionService } from '../auth/auth-session.service';
import { NotificationService } from '../notifications/notification.service';

export const apiErrorInterceptor: HttpInterceptorFn = (request, next) => {
  const session = inject(AuthSessionService);
  const notifications = inject(NotificationService);
  const router = inject(Router);

  return next(request).pipe(
    catchError((error: unknown) => {
      if (request.url.startsWith('/api/') && error instanceof HttpErrorResponse) {
        if (error.status === 401) {
          if (session.authenticated()) {
            session.expireSession();
            notifications.show('Your session has expired. Sign in again to continue.', 'error');
          } else if (router.url.startsWith('/login')) {
            notifications.show(
              'Sign-in failed. Check your details or contact an administrator if your account is pending.',
              'error',
            );
          }
        } else if (error.status === 403) {
          notifications.show('You do not have permission to perform this action.', 'error');
        } else if (error.status === 0) {
          notifications.show('The service could not be reached. Check your connection and retry.', 'error');
        } else {
          const problem = isProblemDetails(error.error) ? error.error : null;
          notifications.show(
            problem?.detail || 'The request could not be completed. Please try again.',
            'error',
          );
        }
      }
      return throwError(() => error);
    }),
  );
};
