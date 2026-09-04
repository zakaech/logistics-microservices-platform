import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { TokenStore } from './token.store';
import { TokenResponse } from '../models';

/** Builds a JWT with the given claims. Unsigned: the store decodes, it never verifies. */
function jwt(claims: Record<string, unknown>): string {
  const encode = (value: object) =>
    btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${encode({ alg: 'RS256' })}.${encode(claims)}.signature`;
}

function tokens(claims: Record<string, unknown>): TokenResponse {
  return {
    accessToken: jwt(claims),
    refreshToken: 'refresh-token',
    tokenType: 'Bearer',
    expiresIn: 900,
  };
}

const inOneHour = Math.floor(Date.now() / 1000) + 3600;
const anHourAgo = Math.floor(Date.now() / 1000) - 3600;

describe('TokenStore', () => {
  let store: TokenStore;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({});
    store = TestBed.inject(TokenStore);
  });

  it('starts signed out', () => {
    expect(store.isAuthenticated()).toBe(false);
    expect(store.roles()).toEqual([]);
  });

  it('reads identity and roles out of the token claims', () => {
    store.save(
      tokens({
        sub: 'user-1',
        email: 'aya@example.com',
        roles: ['ROLE_CLIENT'],
        exp: inOneHour,
      }),
    );

    expect(store.isAuthenticated()).toBe(true);
    expect(store.user()?.email).toBe('aya@example.com');
    expect(store.roles()).toEqual(['ROLE_CLIENT']);
  });

  it('reports an expired token as expired', () => {
    store.save(tokens({ sub: 'user-1', roles: ['ROLE_CLIENT'], exp: anHourAgo }));
    expect(store.isExpired()).toBe(true);
  });

  it('discards an already-expired token when restoring a previous session', () => {
    localStorage.setItem(
      'logistics.accessToken',
      jwt({ sub: 'user-1', roles: ['ROLE_CLIENT'], exp: anHourAgo }),
    );

    // Constructed directly, not through TestBed: the injector would hand back the singleton
    // already built in beforeEach, and restore() only runs in the constructor. This is the
    // one case where a `new` is the right call in a test.
    const restored = new TokenStore();
    expect(restored.isAuthenticated()).toBe(false);
    expect(localStorage.getItem('logistics.accessToken')).toBeNull();
  });

  it('treats a malformed token as no token at all', () => {
    store.save({
      accessToken: 'not-a-jwt',
      tokenType: 'Bearer',
      expiresIn: 900,
    });
    expect(store.isAuthenticated()).toBe(false);
  });

  it('matches any one of the required roles', () => {
    store.save(tokens({ sub: 'u', roles: ['ROLE_WAREHOUSE_MANAGER'], exp: inOneHour }));

    expect(store.hasAnyRole(['ROLE_ADMIN', 'ROLE_WAREHOUSE_MANAGER'])).toBe(true);
    expect(store.hasAnyRole(['ROLE_ADMIN'])).toBe(false);
    // No requirement means no restriction.
    expect(store.hasAnyRole([])).toBe(true);
  });

  it('clears both tokens on sign-out', () => {
    store.save(tokens({ sub: 'u', roles: ['ROLE_CLIENT'], exp: inOneHour }));
    store.clear();

    expect(store.isAuthenticated()).toBe(false);
    expect(store.accessToken()).toBeNull();
    expect(store.refreshToken()).toBeNull();
  });
});
