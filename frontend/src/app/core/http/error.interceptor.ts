import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { ProblemDetail } from '../models';
import { TokenStore } from '../auth/token.store';

/**
 * Turns every failure into one predictable shape, and handles the one case components should not
 * have to.
 *
 * <p>The backend answers RFC 7807 everywhere, including from the gateway, so a component can rely
 * on receiving a {@link ProblemDetail}. This interceptor guarantees that even for the cases the
 * backend never sees - a dead network, a CORS refusal - which otherwise arrive as an
 * {@code HttpErrorResponse} with a status of 0 and no body.
 *
 * <p>A 401 signs the user out. Anything else is passed on: only the component knows whether a 409
 * means "show the shortage" or "the order already shipped".
 */
export const errorInterceptor: HttpInterceptorFn = (request, next) => {
  const tokens = inject(TokenStore);
  const router = inject(Router);

  return next(request).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && !request.url.includes('/api/v1/auth/login')) {
        // The token is gone, expired or revoked. Staying on the page would show a shell whose every
        // request fails; the login screen is the honest answer.
        tokens.clear();
        void router.navigate(['/login'], {
          queryParams: { returnUrl: router.url, reason: 'session-expired' },
        });
      }
      return throwError(() => toProblemDetail(error));
    }),
  );
};

/** Normalises anything the browser or the server produced into a ProblemDetail. */
export function toProblemDetail(error: HttpErrorResponse): ProblemDetail {
  // Status 0 means the request never reached a server: offline, DNS failure, CORS refusal.
  if (error.status === 0) {
    return {
      type: 'about:client',
      title: 'Network unreachable',
      status: 0,
      detail:
        'The server could not be reached. Check your connection and whether the platform is running.',
    };
  }

  const body = error.error as Partial<ProblemDetail> | string | null;

  if (body && typeof body === 'object' && typeof body.status === 'number') {
    return body as ProblemDetail;
  }

  // A non-problem+json error body: keep whatever the server said rather than inventing a message.
  return {
    type: 'about:blank',
    title: error.statusText || 'Request failed',
    status: error.status,
    detail: typeof body === 'string' && body ? body : error.message,
  };
}
