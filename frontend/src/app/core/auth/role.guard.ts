import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Role } from '../models';
import { TokenStore } from './token.store';

/**
 * Refuses a route to anyone lacking one of the required roles.
 *
 * <p>A factory, so a route declares what it needs: {@code canActivate: [authGuard,
 * roleGuard('ROLE_ADMIN')]}. Reading the requirement at the route makes the access rule visible
 * next to the thing it protects.
 *
 * <p><b>This is convenience, not security.</b> The roles come from a token this app decodes but
 * cannot verify, and anyone can edit their own localStorage. What actually protects the data is
 * every service validating the signature and re-checking the role on each call. The guard exists so
 * a user is not shown a screen that would only 403 on them.
 */
export function roleGuard(...required: Role[]): CanActivateFn {
  return () => {
    const tokens = inject(TokenStore);
    const router = inject(Router);

    if (tokens.hasAnyRole(required)) {
      return true;
    }
    return router.createUrlTree(['/forbidden']);
  };
}
