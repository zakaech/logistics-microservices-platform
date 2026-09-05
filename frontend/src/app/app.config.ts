import {
  ApplicationConfig,
  LOCALE_ID,
  provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection,
} from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';

import { routes } from './app.routes';
import { authInterceptor } from './core/http/auth.interceptor';
import { errorInterceptor } from './core/http/error.interceptor';

// Sans cet enregistrement, les pipes date, currency et number formatent en en-US :
// « 9/5/26, 12:03 AM » et « €349.90 ». Le code devise continue de venir de l'API ;
// seule la mise en forme change.
registerLocaleData(localeFr);

export const appConfig: ApplicationConfig = {
  providers: [
    { provide: LOCALE_ID, useValue: 'fr-FR' },

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
