import { HttpResponse, http } from "msw";
import { mockOAuthAuthorize } from "@/__tests__/e2e/mocks/mockOAuthAuthorize";
import plansFixture from "./fixtures/plans.json" with { type: "json" };

type PlanFixture = {
  title: string;
  created: string;
  modified: string;
  contact?: { name: string; affiliation: { name: string } };
};

const PLANS: Record<string, PlanFixture> = plansFixture;

function dmpFor(id: string, origin: string) {
  const plan = PLANS[id];
  return {
    ...plan,
    dmp_id: { identifier: `${origin}/dmpassistant/api/v2/plans/${id}` },
  };
}

function planListResponse(origin: string, ids: string[]) {
  return {
    page: 1,
    per_page: ids.length,
    total_items: ids.length,
    items: ids.map((id) => ({ dmp: dmpFor(id, origin) })),
  };
}

export const dmpassistantHandlers = [
  mockOAuthAuthorize("/dmpassistant/oauth/authorize", "mock-dmpassistant-auth-code", { echoState: true }),

  http.post("/dmpassistant/oauth/token", () =>
    HttpResponse.json({
      access_token: "mock-dmpassistant-access-token",
      refresh_token: "mock-dmpassistant-refresh-token",
      token_type: "bearer",
      expires_in: 3600,
    }),
  ),

  http.get("/dmpassistant/api/v2/plans", ({ request }) => {
    const origin = new URL(request.url).origin;
    return HttpResponse.json(planListResponse(origin, Object.keys(PLANS)));
  }),

  http.get("/dmpassistant/api/v2/plans/:id", ({ request, params }) => {
    const id = String(params.id);
    if (!(id in PLANS)) {
      return new HttpResponse("Not found", { status: 404 });
    }
    const origin = new URL(request.url).origin;
    return HttpResponse.json({ dmp: dmpFor(id, origin) });
  }),
];
