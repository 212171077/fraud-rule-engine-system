import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
  provideZoneChangeDetection,
} from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';

import { apiErrorInterceptor } from './core/api/api-error.interceptor';
import { authTokenInterceptor } from './core/api/auth-token.interceptor';
import { routes } from './app.routes';
import { AuthModeService } from './core/auth/auth-mode.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideHttpClient(withInterceptors([authTokenInterceptor, apiErrorInterceptor])),
    provideRouter(routes),
    provideAppInitializer(() => inject(AuthModeService).load()),
  ],
};
