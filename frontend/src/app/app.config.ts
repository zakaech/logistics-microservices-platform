import {
  ApplicationConfig,
  provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection,
} from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';

import { routes } from './app.routes';
import { authInterceptor } from './core/http/auth.interceptor';
import { errorInterceptor } from './core/http/error.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZonelessChangeDetection(),

    // withComponentInputBinding lets a route param arrive as a component input(), so
    // OrderDetailComponent declares `id` instead of reaching into ActivatedRoute.
    provideRouter(routes, withComponentInputBinding()),

    // Order matters. authInterceptor runs first and attaches the token; errorInterceptor
    // wraps the call so it sees the failure of the request that actually went out.
    provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
  ],
};
