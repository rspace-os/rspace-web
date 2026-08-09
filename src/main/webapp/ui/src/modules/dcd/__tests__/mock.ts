import { HttpResponse, http } from "msw";
import { mockOAuthAuthorize } from "@/__tests__/e2e/mocks/mockOAuthAuthorize";

export const MOCK_DATASET_ID = "mock-dataset-1";

export const dcdHandlers = [
  mockOAuthAuthorize("/dcd/oauth2/authorize", "mock-dcd-auth-code", { echoState: true }),

  http.post("/dcd/oauth2/token", () =>
    HttpResponse.json({
      access_token: "mock-dcd-access-token",
      refresh_token: "mock-dcd-refresh-token",
      token_type: "bearer",
      expires_in: 3600,
    }),
  ),

  http.delete("/dcd/active-data-entities/datasets/drafts/FAKE_ID", () =>
    HttpResponse.json({ message: "Draft dataset 'FAKE_ID' not found" }, { status: 404 }),
  ),

  http.post("/dcd/active-data-entities/datasets/drafts", () =>
    HttpResponse.json({
      id: MOCK_DATASET_ID,
      name: "Mock DCD Dataset",
    }),
  ),

  http.post("/dcd/uploads", () =>
    HttpResponse.json({
      id: "mock-dcd-file-1",
    }),
  ),

  http.post("/dcd/active-data-entities/datasets/drafts/:datasetId/files", ({ params }) =>
    HttpResponse.json({
      id: "mock-dcd-binding-1",
      ticket_id: "mock-dcd-file-1",
      filename: `export-${String(params.datasetId)}.pdf`,
    }),
  ),
];
