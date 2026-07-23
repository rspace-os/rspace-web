import { HttpResponse, http, type RequestHandler } from "msw";

/*
 * The RAiD integration is disabled-but-available by default; components that
 * merely render the integration status (rather than testing RAiD itself) can
 * register this so `/integration/integrationInfo` doesn't 404.
 */
export const raidIntegrationInfoHandler = (): RequestHandler =>
  http.get("/integration/integrationInfo", () =>
    HttpResponse.json({
      success: true,
      data: {
        name: "RAID",
        displayName: "RAiD",
        available: true,
        enabled: false,
        oauthConnected: false,
        options: { RAID_CONFIGURED_SERVERS: [] },
      },
    }),
  );

/*
 * An empty share listing for components that fetch `/api/v1/share` on mount
 * but aren't under test for sharing behaviour themselves.
 */
export const emptyShareListingHandler = (): RequestHandler =>
  http.get("/api/v1/share", () => HttpResponse.json({ totalHits: 0, pageNumber: 0, shares: [], _links: [] }));
