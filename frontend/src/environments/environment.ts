/**
 * Production settings, used by `ng build`.
 *
 * <p>An empty base URL means same-origin: the nginx container serving these files proxies
 * {@code /api/v1} to the gateway. That removes CORS from the production path entirely - the browser
 * never makes a cross-origin request, so there is no preflight to configure and no origin list to
 * keep in step with a deployment.
 */
export const environment = {
  production: true,
  apiBaseUrl: '',
};
