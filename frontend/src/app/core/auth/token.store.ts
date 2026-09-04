import { Injectable, computed, signal } from '@angular/core';
import { AuthenticatedUser, Role, TokenResponse } from '../models';

const ACCESS_TOKEN_KEY = 'logistics.accessToken';
const REFRESH_TOKEN_KEY = 'logistics.refreshToken';

/**
 * Holds the credentials and exposes who is signed in.
 *
 * <p><b>Where the tokens live is a deliberate decision, and a defensible one.</b> The ideal is an
 * access token in memory only and a refresh token in an httpOnly cookie, which puts both out of
 * reach of injected JavaScript. That needs the backend to set the cookie, which this platform does
 * not do yet, so both are in localStorage for now.
 *
 * <p>The trade-off, stated plainly: localStorage is readable by any script running on the origin,
 * so a successful XSS steals the session. It buys a session that survives a page reload. The
 * mitigations that make it acceptable here are short-lived access tokens (15 minutes), refresh
 * tokens that rotate on every use so a stolen one is single-use, and Angular escaping interpolated
 * values by default. Isolating it in this one class is what makes the eventual move to a cookie a
 * change to a single file.
 */
@Injectable({ providedIn: 'root' })
export class TokenStore {
  /** Signal rather than a subject: the whole app reads identity reactively from one source. */
  private readonly currentUser = signal<AuthenticatedUser | null>(this.restore());

  readonly user = this.currentUser.asReadonly();
  readonly isAuthenticated = computed(() => this.currentUser() !== null);
  readonly roles = computed<Role[]>(() => this.currentUser()?.roles ?? []);

  accessToken(): string | null {
    return localStorage.getItem(ACCESS_TOKEN_KEY);
  }

  refreshToken(): string | null {
    return localStorage.getItem(REFRESH_TOKEN_KEY);
  }

  save(tokens: TokenResponse): void {
    localStorage.setItem(ACCESS_TOKEN_KEY, tokens.accessToken);
    if (tokens.refreshToken) {
      localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken);
    }
    this.currentUser.set(this.decode(tokens.accessToken));
  }

  clear(): void {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    this.currentUser.set(null);
  }

  hasAnyRole(required: readonly Role[]): boolean {
    if (required.length === 0) {
      return true;
    }
    const held = this.roles();
    return required.some((role) => held.includes(role));
  }

  /** True when the token has expired according to its own exp claim. */
  isExpired(): boolean {
    const user = this.currentUser();
    return user === null || user.expiresAt <= Date.now();
  }

  private restore(): AuthenticatedUser | null {
    const token = localStorage.getItem(ACCESS_TOKEN_KEY);
    if (!token) {
      return null;
    }
    const user = this.decode(token);
    // A token restored from a previous session may already have expired; treating it as signed in
    // would show a shell that 401s on every request.
    if (!user || user.expiresAt <= Date.now()) {
      localStorage.removeItem(ACCESS_TOKEN_KEY);
      localStorage.removeItem(REFRESH_TOKEN_KEY);
      return null;
    }
    return user;
  }

  /**
   * Reads the claims out of the JWT payload.
   *
   * <p>Decoding, never verifying. The signature can only be checked by something holding the key,
   * and that is the server's job on every request. What is read here drives what the UI offers to
   * show - hiding a button is convenience, not security, and the API refuses the call regardless.
   */
  private decode(token: string): AuthenticatedUser | null {
    try {
      const payload = token.split('.')[1];
      if (!payload) {
        return null;
      }
      const normalised = payload.replace(/-/g, '+').replace(/_/g, '/');
      const claims = JSON.parse(atob(normalised)) as {
        sub: string;
        email?: string;
        roles?: Role[];
        exp: number;
      };
      return {
        id: claims.sub,
        email: claims.email ?? '',
        roles: claims.roles ?? [],
        expiresAt: claims.exp * 1000,
      };
    } catch {
      // A malformed token is treated as no token at all.
      return null;
    }
  }
}
