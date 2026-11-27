import { HttpResponse, http } from "msw/http";

export const MOCK_PLAN_ID = "6f1c2e7a-3b4d-4e5f-8a9b-0c1d2e3f4a5b";
export const MOCK_PLAN_LABEL = "Mock Data Management Plan";

const MOCK_GRANT_REFERENCE = {
  isActive: 1,
  reference: { id: "mock-grant-1", label: "Mock Grant", type: { code: "grants" } },
};

export const argosHandlers = [
  http.post("/argos-api/plan/public/query", () =>
    HttpResponse.json({
      items: [
        {
          id: MOCK_PLAN_ID,
          label: MOCK_PLAN_LABEL,
          createdAt: "2025-01-01T00:00:00Z",
          updatedAt: "2025-01-01T00:00:00Z",
          planReferences: [MOCK_GRANT_REFERENCE],
        },
      ],
      count: 1,
    }),
  ),

  http.get("/argos-api/plan/public/:id", ({ params }) =>
    HttpResponse.json({
      id: String(params.id),
      label: MOCK_PLAN_LABEL,
      entityDois: [],
      planReferences: [MOCK_GRANT_REFERENCE],
    }),
  ),
];
