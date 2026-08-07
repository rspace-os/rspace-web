import { HttpResponse, http } from "msw";

const BOOKING_ID = 9990001;
const EQUIPMENT_ID = 9990002;
export const MOCK_EQUIPMENT_NAME = "Mock PCR Machine";
export const MOCK_REQUESTER_NAME = "Mock Requester";

export const caliraHandlers = [
  http.get("/calira-web/oauth/authorize", ({ request }) => {
    const url = new URL(request.url);
    const redirectUri = url.searchParams.get("redirect_uri");
    if (!redirectUri) {
      return new HttpResponse("Missing redirect_uri", { status: 400 });
    }
    const target = new URL(redirectUri);
    target.searchParams.set("code", "mock-calira-auth-code");
    const state = url.searchParams.get("state");
    if (state) target.searchParams.set("state", state);
    return HttpResponse.redirect(target.toString(), 302);
  }),

  http.post("/calira-web/oauth/token", () =>
    HttpResponse.json({
      access_token: "mock-calira-access-token",
      refresh_token: "mock-calira-refresh-token",
      token_type: "bearer",
      expires_in: 3600,
      user: "mock-calira-user",
    }),
  ),

  http.get("/calira-api/bookings", () =>
    HttpResponse.json([
      {
        id: BOOKING_ID,
        start_time: "2026-01-01T09:00:00.000+00:00",
        end_time: "2026-01-01T10:00:00.000+00:00",
        status: "Booked",
        user_id: 1,
        equipment_id: EQUIPMENT_ID,
      },
    ]),
  ),

  http.get("/calira-api/bookings/:id", ({ params }) =>
    HttpResponse.json({
      id: Number(params.id),
      requester: { name: MOCK_REQUESTER_NAME },
      equipment: { id: EQUIPMENT_ID, name: MOCK_EQUIPMENT_NAME },
      booking_type: "internal",
      duration: 60,
      requester_lab: { id: 42, name: "Mock Lab" },
      last_public_note: null,
    }),
  ),

  http.get("/calira-api/equipment/:id", ({ params }) =>
    HttpResponse.json({
      id: Number(params.id),
      name: MOCK_EQUIPMENT_NAME,
      manufacturer: "Mock Manufacturer",
      model: "Mock Model 100",
    }),
  ),
];
