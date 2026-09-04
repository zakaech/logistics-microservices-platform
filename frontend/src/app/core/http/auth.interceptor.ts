import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { environment } from '../../../environments/environment';
import { TokenStore } from '../auth/token.store';

/** Endpoints that must never carry a token: sending one to /login is meaningless. */
const ANONYMOUS_PATHS = [
  '/api/v1/auth/login',
  '/api/v1/auth/register',
  '/api/v1/auth/refresh',
  '/api/v1/auth/token',
];

/**
 * Matches an absolute URL.
 *
 * <p>The optional scheme is not an oversight: {@code //evil.example/x} is protocol-relative, which
 * the browser resolves to a different origin just as surely as {@code https://evil.example/x}.
 * Treating it as relative would send the token off-site.
 */
const ABSOLUTE_URL = /^(https?:)?\/\//i;

/**
 * Decides whether a request is aimed at our own API.
 *
 * <p>Exported and pure so it can be tested against both build configurations - which matters,
 * because the two differ in exactly the way that used to break this check.
 *
 * <p><b>Why not simply {@code url.startsWith(apiBaseUrl)}.</b> In the production build
 * {@code apiBaseUrl} is the empty string, since the app and the API share an origin behind nginx -
 * and every string starts with the empty string, so that test was true for every URL, including a
 * third party's. The guard existed only in development.
 *
 * <p>Origins are compared rather than prefixes, for a second reason: with an absolute base of
 * {@code http://localhost:8080}, a prefix test also matches {@code http://localhost:8080.evil.com}.
 */
export function isOwnApiUrl(url: string, apiBaseUrl: string): boolean {
  // A relative URL is same-origin by definition: it can only reach the server that served the app.
  if (!ABSOLUTE_URL.test(url)) {
    return true;
  }

  // Absolute URL, and no absolute API is configured: nothing can legitimately match.
  if (!apiBaseUrl) {
    return false;
  }

  try {
    // The base resolves a protocol-relative URL against the page's own scheme, so
    // //evil.example is compared as the foreign origin it really is.
    const target = new URL(url, apiBaseUrl);
    return target.origin === new URL(apiBaseUrl).origin;
  } catch {
    // An unparseable URL is not our API.
    return false;
  }
}

/**
 * Attaches the bearer token to every outgoing call.
 *
 * <p>Doing it here rather than in each service means no service can forget, and no component has to
 * know a token exists at all.
 *
 * <p>Two guards on where the token goes. It is attached only to requests aimed at our own API, so a
 * call to a third-party host cannot carry the credential; and it is withheld from the anonymous auth
 * endpoints, where it would be noise at best.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const tokens = inject(TokenStore);
  const token = tokens.accessToken();

  const isAnonymous = ANONYMOUS_PATHS.some((path) => request.url.includes(path));

  if (!token || isAnonymous || !isOwnApiUrl(request.url, environment.apiBaseUrl)) {
    return next(request);
  }

  return next(
    request.clone({
      setHeaders: { Authorization: `Bearer ${token}` },
    }),
  );
};
