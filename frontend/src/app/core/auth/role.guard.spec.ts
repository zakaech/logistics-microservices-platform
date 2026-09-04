import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { authGuard } from './auth.guard';
import { roleGuard } from './role.guard';
import { TokenStore } from './token.store';

function jwt(claims: Record<string, unknown>): string {
  const encode = (value: object) =>
    btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${encode({ alg: 'RS256' })}.${encode(claims)}.signature`;
}

const inOneHour = Math.floor(Date.now() / 1000) + 3600;

/**
 * Guards decide what the app offers to show. They are convenience, not security - the API refuses
 * the call regardless - but a guard that lets the wrong screen through sends a user straight into
 * a 403, so the rules are worth pinning.
 */
describe('route guards', () => {
  let tokens: TokenStore;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    tokens = TestBed.inject(TokenStore);
  });

  const route = {} as ActivatedRouteSnapshot;
  const state = { url: '/orders' } as RouterStateSnapshot;

  function signIn(roles: string[]): void {
    tokens.save({
      accessToken: jwt({ sub: 'u1', email: 'a@b.c', roles, exp: inOneHour }),
      tokenType: 'Bearer',
      expiresIn: 900,
    });
  }

  describe('authGuard', () => {
    it('lets a signed-in user through', () => {
      signIn(['ROLE_CLIENT']);
      const result = TestBed.runInInjectionContext(() => authGuard(route, state));
      expect(result).toBe(true);
    });

    it('redirects an anonymous visitor to the login screen, remembering where they went', () => {
      const result = TestBed.runInInjectionContext(() => authGuard(route, state)) as UrlTree;

      expect(result).toBeInstanceOf(UrlTree);
      expect(result.toString()).toContain('/login');
      // returnUrl, so signing in lands where they were headed.
      expect(result.toString()).toContain('returnUrl');
    });
  });

  describe('roleGuard', () => {
    it('lets a user holding one of the required roles through', () => {
      signIn(['ROLE_WAREHOUSE_MANAGER']);
      const guard = roleGuard('ROLE_ADMIN', 'ROLE_WAREHOUSE_MANAGER');

      expect(TestBed.runInInjectionContext(() => guard(route, state))).toBe(true);
    });

    it('sends a user lacking the role to the forbidden page, not to login', () => {
      // They ARE signed in: telling them to sign in again would be misleading.
      signIn(['ROLE_CLIENT']);
      const guard = roleGuard('ROLE_ADMIN');

      const result = TestBed.runInInjectionContext(() => guard(route, state)) as UrlTree;
      expect(result.toString()).toContain('/forbidden');
    });
  });
});
