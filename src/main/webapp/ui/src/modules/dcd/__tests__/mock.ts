import { HttpResponse, http } from "msw";

export const MOCK_DATASET_ID = "mock-dataset-1";

export const dcdHandlers = [
  http.get("/dcd/oauth2/authorize", ({ request }) => {
    const url = new URL(request.url);
    const redirectUri = url.searchParams.get("redirect_uri");
    if (!redirectUri) {
      return new HttpResponse("Missing redirect_uri", { status: 400 });
    }
    const target = new URL(redirectUri);
    target.searchParams.set("code", "mock-dcd-auth-code");
    const state = url.searchParams.get("state");
    if (state) target.searchParams.set("state", state);
    return HttpResponse.redirect(target.toString(), 302);
  }),

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
