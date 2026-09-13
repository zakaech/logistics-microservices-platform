import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { TokenStore } from './token.store';

/**
 * Refuses a route to anyone not signed in, and remembers where they were going.
 *
 * <p>A functional guard rather than a class: it is the Angular idiom since v15, and {@code inject}
 * makes it testable without a TestBed.
 */
export const authGuard: CanActivateFn = (_route, state) => {
  const tokens = inject(TokenStore);
  const router = inject(Router);

  if (tokens.isAuthenticated() && !tokens.isExpired()) {
    return true;
  }

  // An expired token left behind would make every request 401; clearing it here keeps the app
  // honest about being signed out.
  tokens.clear();

  // returnUrl, so signing in lands where the user was headed rather than on a home page.
  return router.createUrlTree(['/login'], {
    queryParams: { returnUrl: state.url },
  });
};
