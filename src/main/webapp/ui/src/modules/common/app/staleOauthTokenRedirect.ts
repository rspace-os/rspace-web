type RedirectLocation = Pick<Location, "assign" | "origin" | "pathname">;

export function withStaleOauthTokenRedirect(
  request: typeof globalThis.fetch,
  location: RedirectLocation = window.location,
): typeof globalThis.fetch {
  return async (input, init) => {
    const response = await request(input, init);
    const requestUrl = new URL(input instanceof Request ? input.url : input.toString(), location.origin);
    const isBookingPage = location.pathname === "/booking" || location.pathname.startsWith("/booking/");
    if (
      response.status === 401 &&
      isBookingPage &&
      requestUrl.origin === location.origin &&
      requestUrl.pathname.startsWith("/api/v2/")
    ) {
      location.assign("/login");
    }
    return response;
  };
}
