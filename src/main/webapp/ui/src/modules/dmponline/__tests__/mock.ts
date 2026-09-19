import { HttpResponse, http } from "msw";
import { mockOAuthAuthorize } from "../../../__tests__/e2e/mocks/mockOAuthAuthorize.ts";
import plansFixture from "./fixtures/plans.json" with { type: "json" };

type PlanFixture = {
  title: string;
  description: string;
  created: string;
  modified: string;
};

const PLANS: Record<string, PlanFixture> = plansFixture;

function dmpFor(id: string, origin: string) {
  const plan = PLANS[id];
  const link = `${origin}/dmponline/api/v2/plans/${id}`;
  return {
    ...plan,
    dmp_id: { identifier: link, type: "url" },
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

export const dmponlineHandlers = [
  mockOAuthAuthorize("/dmponline/oauth/authorize", "mock-dmponline-auth-code"),

  http.post("/dmponline/oauth/token", () =>
    HttpResponse.json({
      access_token: "mock-dmponline-access-token",
      refresh_token: "mock-dmponline-refresh-token",
      token_type: "bearer",
      expires_in: 3600,
    }),
  ),

  http.get("/dmponline/oauth/token/info", () => HttpResponse.json({ expires_in: 3600 })),

  http.get("/dmponline/api/v2/plans", ({ request }) => {
    const origin = new URL(request.url).origin;
    return HttpResponse.json(planListResponse(origin, Object.keys(PLANS)));
  }),

  http.get("/dmponline/api/v2/plans/:id", ({ request, params }) => {
    const id = String(params.id);
    if (!(id in PLANS)) {
      return new HttpResponse("Not found", { status: 404 });
    }
    const origin = new URL(request.url).origin;
    return HttpResponse.json(planListResponse(origin, [id]));
  }),
];
