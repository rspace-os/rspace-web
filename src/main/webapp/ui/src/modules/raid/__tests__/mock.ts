import { HttpResponse, http } from "msw";
import raidsFixture from "./fixtures/raids.json" with { type: "json" };
import servicePointFixture from "./fixtures/servicePoint.json" with { type: "json" };

type RaidFixture = (typeof raidsFixture)[number];

function pathSegments(identifierUrl: string): [prefix: string, suffix: string] {
  const segments = new URL(identifierUrl).pathname.split("/").filter(Boolean);
  return [segments[segments.length - 2], segments[segments.length - 1]];
}

function findRaidByPrefixSuffix(prefix: string, suffix: string): RaidFixture | undefined {
  return raidsFixture.find((raid) => {
    const [raidPrefix, raidSuffix] = pathSegments(raid.identifier.id);
    return raidPrefix === prefix && raidSuffix === suffix;
  });
}

export const raidHandlers = [
  http.get("/raid-auth/auth", ({ request }) => {
    const url = new URL(request.url);
    const redirectUri = url.searchParams.get("redirect_uri");
    if (!redirectUri) {
      return new HttpResponse("Missing redirect_uri", { status: 400 });
    }
    const target = new URL(redirectUri);
    target.searchParams.set("code", "mock-raid-auth-code");
    const state = url.searchParams.get("state");
    if (state) target.searchParams.set("state", state);
    return HttpResponse.redirect(target.toString(), 302);
  }),

  http.post("/raid-auth/token", () =>
    HttpResponse.json({
      access_token: "mock-raid-access-token",
      refresh_token: "mock-raid-refresh-token",
      token_type: "bearer",
      expires_in: 3600,
    }),
  ),

  http.get("/raid-api/service-point/:id", () => HttpResponse.json(servicePointFixture)),

  http.get("/raid-api/raid/", () => HttpResponse.json(raidsFixture)),

  http.get("/raid-api/raid/:prefix/:suffix", ({ params }) => {
    const raid = findRaidByPrefixSuffix(String(params.prefix), String(params.suffix));
    if (!raid) {
      return new HttpResponse("Not found", { status: 404 });
    }
    return HttpResponse.json({ ...raid, relatedObject: [] });
  }),

  http.put("/raid-api/raid/:prefix/:suffix", async ({ request }) => {
    const body = await request.json();
    return HttpResponse.json(body);
  }),
];
