import { HttpResponse, http } from "msw";

export const MOCK_PLAN_ID = "mock-plan-1";
export const MOCK_PLAN_LABEL = "Mock Data Management Plan";

export const argosHandlers = [
  http.post("/argos-api/public/dmps", () =>
    HttpResponse.json({
      payload: {
        data: [
          {
            id: MOCK_PLAN_ID,
            label: MOCK_PLAN_LABEL,
            grant: "Mock Grant",
            createdAt: 1735689600000,
            modifiedAt: 1735689600000,
          },
        ],
        totalCount: 1,
      },
    }),
  ),

  http.get("/argos-api/public/dmps/:id", ({ params }) =>
    HttpResponse.json({
      payload: {
        id: String(params.id),
        label: MOCK_PLAN_LABEL,
        grant: { id: "mock-grant-1", label: "Mock Grant" },
        dois: [],
      },
    }),
  ),
];
