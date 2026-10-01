import { describe, expect, it, vi } from "vitest";
import { withStaleOauthTokenRedirect } from "@/modules/common/app/staleOauthTokenRedirect";

describe("stale OAuth UI token redirect", () => {
  it.each([
    [401, "/booking/calendar", "/api/v2/bookings", 1],
    [403, "/booking/calendar", "/api/v2/bookings", 0],
    [401, "/about", "/api/v2/bookings", 0],
    [401, "/booking/calendar", "https://example.com/api/v2/bookings", 0],
  ])("redirects only an unauthorized RSpace API response in booking", async (status, pathname, url, redirects) => {
    const request = vi.fn<typeof globalThis.fetch>().mockResolvedValue(new Response(null, { status }));
    const assign = vi.fn();
    const fetchWithRedirect = withStaleOauthTokenRedirect(request, {
      assign,
      origin: "http://localhost",
      pathname,
    });

    const response = await fetchWithRedirect(url);

    expect(response.status).toBe(status);
    expect(assign).toHaveBeenCalledTimes(redirects);
    if (redirects === 1) expect(assign).toHaveBeenCalledWith("/login");
  });
});
