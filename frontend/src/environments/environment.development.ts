/**
 * Development settings, used by `ng serve`.
 *
 * <p>The dev server runs on :4200 and the gateway on :8080, so calls really are cross-origin here.
 * That is why the gateway allows http://localhost:4200 explicitly, with credentials - and why it
 * allows an explicit origin rather than a wildcard, which the specification forbids alongside
 * credentials.
 */
export const environment = {
  production: false,
  apiBaseUrl: 'http://localhost:8080',
};
