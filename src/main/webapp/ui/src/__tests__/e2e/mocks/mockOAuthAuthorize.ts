import { HttpResponse, http } from "msw";

/**
 * Shared `GET .../oauth/authorize` mock: redirects to the caller-supplied
 * callback URL with a fake auth code, mirroring every simple OAuth-authorize
 * endpoint in this suite.
 */
export function mockOAuthAuthorize(
  path: string,
  mockCode: string,
  opts: { redirectParam?: string; echoState?: boolean } = {},
) {
  const { redirectParam = "redirect_uri", echoState = false } = opts;
  return http.get(path, ({ request }) => {
    const url = new URL(request.url);
    const redirectUri = url.searchParams.get(redirectParam);
    if (!redirectUri) {
      return new HttpResponse(`Missing ${redirectParam}`, { status: 400 });
    }
    const target = new URL(redirectUri);
    target.searchParams.set("code", mockCode);
    if (echoState) {
      const state = url.searchParams.get("state");
      if (state) target.searchParams.set("state", state);
    }
    return HttpResponse.redirect(target.toString(), 302);
  });
}
