import { HttpResponse, http } from "msw";
import { mockOAuthAuthorize } from "@/__tests__/e2e/mocks/mockOAuthAuthorize";

const BOOKING_ID = 9990001;
const EQUIPMENT_ID = 9990002;
export const MOCK_EQUIPMENT_NAME = "Mock PCR Machine";
export const MOCK_REQUESTER_NAME = "Mock Requester";

export const caliraHandlers = [
  mockOAuthAuthorize("/calira-web/oauth/authorize", "mock-calira-auth-code", { echoState: true }),

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
