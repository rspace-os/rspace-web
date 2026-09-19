import { HttpResponse, http } from "msw";

export const MOCK_ORCID_ID = "0000-0002-1825-0097";
export const MOCK_ORCID_NAME = "Mock Orcid User";

export const orcidHandlers = [
  http.post("/orcid/oauth/token", () =>
    HttpResponse.json({
      access_token: "mock-orcid-access-token",
      token_type: "bearer",
      refresh_token: "mock-orcid-refresh-token",
      expires_in: 631138518,
      scope: "/authenticate",
      orcid: MOCK_ORCID_ID,
      name: MOCK_ORCID_NAME,
    }),
  ),
];
