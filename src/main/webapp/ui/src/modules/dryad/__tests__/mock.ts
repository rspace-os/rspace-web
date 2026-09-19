import { HttpResponse, http } from "msw";
import { mockOAuthAuthorize } from "../../../__tests__/e2e/mocks/mockOAuthAuthorize.ts";

export const dryadHandlers = [
  mockOAuthAuthorize("/dryad/oauth/authorize", "mock-dryad-auth-code", { echoState: true }),

  http.post("/dryad/oauth/token", () =>
    HttpResponse.json({
      access_token: "mock-dryad-access-token",
      token_type: "bearer",
      expires_in: 3600,
    }),
  ),

  http.get("/dryad/api/v2/test", () => HttpResponse.json({ message: "Test connection OK", user_id: 424242 })),

  http.post("/dryad/api/v2/datasets", () =>
    HttpResponse.json({
      identifier: "doi:10.5061/dryad.mock1",
      editLink: "/stash/edit/dataset/doi%3A10.5061%2Fdryad.mock1",
    }),
  ),

  http.put("/dryad/api/v2/datasets/:doi/files/:filename", () => HttpResponse.json({})),
];
