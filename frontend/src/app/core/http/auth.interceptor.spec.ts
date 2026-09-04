import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { environment } from '../../../environments/environment';
import { TokenStore } from '../auth/token.store';
import { authInterceptor, isOwnApiUrl } from './auth.interceptor';

const API = environment.apiBaseUrl;

/**
 * The interceptor is where a forgotten - or ineffective - guard would become a platform-wide
 * credential leak, so its rules are pinned in two layers: the decision function is tested against
 * BOTH build configurations, and the interceptor is tested end to end against the one the test
 * build resolves.
 */
describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);

    TestBed.inject(TokenStore).save({
      accessToken: 'the-access-token',
      tokenType: 'Bearer',
      expiresIn: 900,
    });
  });

  afterEach(() => backend.verify());

  function expectAuthorization(url: string, expected: string | null): void {
    http.get(url).subscribe();
    const request = backend.expectOne(url);
    expect(request.request.headers.get('Authorization')).toBe(expected);
    request.flush({});
  }

  it('attaches the bearer token to a call on our own API', () => {
    expectAuthorization(`${API}/api/v1/orders`, 'Bearer the-access-token');
  });

  it('attaches it to a relative same-origin URL', () => {
    // The production shape: the app and the API share an origin behind nginx, so the app issues
    // relative URLs and there is no absolute base to compare against.
    expectAuthorization('/api/v1/orders', 'Bearer the-access-token');
  });

  it('does not attach it to the login endpoint', () => {
    expectAuthorization(`${API}/api/v1/auth/login`, null);
  });

  it('does not leak the token to a third-party host over http', () => {
    expectAuthorization('http://third-party.example/data', null);
  });

  it('does not leak the token to a third-party host over https', () => {
    expectAuthorization('https://third-party.example/data', null);
  });

  it('sends nothing when signed out', () => {
    TestBed.inject(TokenStore).clear();
    expectAuthorization(`${API}/api/v1/products`, null);
  });
});

/**
 * The decision, tested directly against both configurations.
 *
 * <p>This is the layer that matters most. The interceptor test above can only exercise whichever
 * environment the test builder resolves - it resolves the development one - and the defect this
 * suite exists to prevent appeared only in production, where apiBaseUrl is the empty string.
 */
describe('isOwnApiUrl', () => {
  describe('development configuration (absolute base URL)', () => {
    const base = 'http://localhost:8080';

    it('accepts an absolute URL on the configured API origin', () => {
      expect(isOwnApiUrl(`${base}/api/v1/orders`, base)).toBe(true);
    });

    it('accepts a relative URL', () => {
      expect(isOwnApiUrl('/api/v1/orders', base)).toBe(true);
    });

    it('rejects a third-party host', () => {
      expect(isOwnApiUrl('https://third-party.example/data', base)).toBe(false);
      expect(isOwnApiUrl('http://third-party.example/data', base)).toBe(false);
    });

    it('rejects a host that merely starts with the configured base', () => {
      // A prefix test would accept this; comparing origins does not.
      expect(isOwnApiUrl('http://localhost:8080.evil.example/steal', base)).toBe(false);
    });

    it('rejects a protocol-relative URL to another origin', () => {
      // //evil.example resolves to a foreign origin, so it must not read as relative.
      expect(isOwnApiUrl('//evil.example/steal', base)).toBe(false);
    });
  });

  describe('production configuration (apiBaseUrl is empty)', () => {
    const base = '';

    it('accepts a relative URL, which is same-origin by definition', () => {
      expect(isOwnApiUrl('/api/v1/orders', base)).toBe(true);
    });

    it('rejects every absolute URL, since no absolute API is configured', () => {
      // The regression this whole suite exists for: startsWith('') was true for all of these.
      expect(isOwnApiUrl('https://third-party.example/data', base)).toBe(false);
      expect(isOwnApiUrl('http://third-party.example/data', base)).toBe(false);
      expect(isOwnApiUrl('//evil.example/steal', base)).toBe(false);
    });

    it('rejects an absolute URL even to our own host name', () => {
      // Same-origin traffic is relative in this configuration; an absolute URL is not something
      // the app produces, so refusing it costs nothing and closes the hole.
      expect(isOwnApiUrl('http://localhost:4200/api/v1/orders', base)).toBe(false);
    });
  });

  it('treats an unparseable URL as foreign', () => {
    expect(isOwnApiUrl('http://[malformed', 'http://localhost:8080')).toBe(false);
  });
});
