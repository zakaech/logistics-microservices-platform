import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { beforeEach, describe, expect, it } from 'vitest';
import { routes } from './app.routes';
import { TokenStore } from './core/auth/token.store';

function jwt(claims: Record<string, unknown>): string {
  const encode = (value: object) =>
    btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${encode({ alg: 'RS256' })}.${encode(claims)}.signature`;
}

const inOneHour = Math.floor(Date.now() / 1000) + 3600;

/**
 * Which screens live inside the application frame, and which stand on their own.
 *
 * <p>The distinction is carried by the shape of the route tree - authentication at the top level,
 * everything else under {@code ShellComponent} - and that is exactly the kind of structure a
 * refactor silently undoes. Asserting on the rendered DOM rather than on the route objects is
 * deliberate: it is the rendering that was wrong, so it is the rendering that has to be pinned.
 */
describe('layouts de route', () => {
  let tokens: TokenStore;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    tokens = TestBed.inject(TokenStore);
  });

  function signIn(): void {
    tokens.save({
      accessToken: jwt({
        sub: 'u-1',
        email: 'staff@logistics.local',
        roles: ['ROLE_ADMIN'],
        exp: inOneHour,
      }),
      refreshToken: 'refresh',
      tokenType: 'Bearer',
      expiresIn: 3600,
    });
  }

  it('rend /login sans le cadre applicatif', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/login');
    const page: HTMLElement = harness.routeNativeElement!.ownerDocument.body;

    // La page de connexion elle-même est bien là...
    expect(page.querySelector('form')).not.toBeNull();
    expect(page.textContent).toContain('Se connecter');

    // ...et rien du cadre applicatif ne l'entoure.
    expect(page.querySelector('app-shell')).toBeNull();
    expect(page.querySelector('.sidebar')).toBeNull();
    expect(page.querySelector('.topbar')).toBeNull();
  });

  it('rend les écrans applicatifs dans le cadre', async () => {
    signIn();
    const harness = await RouterTestingHarness.create();

    for (const url of ['/catalog', '/dashboard', '/orders', '/inventory', '/orders/new']) {
      await harness.navigateByUrl(url);
      const page: HTMLElement = harness.routeNativeElement!.ownerDocument.body;
      expect(page.querySelector('.sidebar'), `${url} doit garder la barre latérale`).not.toBeNull();
      expect(page.querySelector('.topbar'), `${url} doit garder la barre supérieure`).not.toBeNull();
    }
  });

  it("renvoie vers /login en conservant l'URL demandée", async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/orders');

    expect(TestBed.inject(Router).url).toBe('/login?returnUrl=%2Forders');
  });
});
