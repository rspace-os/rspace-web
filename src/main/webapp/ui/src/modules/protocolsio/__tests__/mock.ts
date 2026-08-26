import { HttpResponse, http } from "msw";
import { mockOAuthAuthorize } from "../../../__tests__/e2e/mocks/mockOAuthAuthorize.ts";

export const MOCK_PROTOCOL_ID = 999999;
export const MOCK_PROTOCOL_TITLE = "Mock Protocol Import Test";

const MOCK_EPOCH_SECONDS = 1735689600;

export const MOCK_PROTOCOLS_IO_SEARCH_RESPONSE = {
  total_pages: 1,
  total_results: 1,
  items: [
    {
      id: MOCK_PROTOCOL_ID,
      title: MOCK_PROTOCOL_TITLE,
      doi: "dx.doi.org/10.17504/mock",
      uri: "mock-protocol",
      published_on: MOCK_EPOCH_SECONDS,
      created_on: MOCK_EPOCH_SECONDS,
      version_id: 1,
    },
  ],
};

export const MOCK_PROTOCOLS_IO_PROTOCOL_RESPONSE = {
  protocol: {
    id: MOCK_PROTOCOL_ID,
    title: MOCK_PROTOCOL_TITLE,
    created_on: MOCK_EPOCH_SECONDS,
    published_on: MOCK_EPOCH_SECONDS,
    version_id: 1,
    doi: "dx.doi.org/10.17504/mock",
    link: "https://www.protocols.io/view/mock-protocol",
    description: "A mock protocol for e2e testing.",
    creator: { name: "Mock Creator", username: "mock-creator" },
    authors: [],
    steps: [],
    materials: [],
  },
};

export const protocolsioHandlers = [
  mockOAuthAuthorize("/protocolsio/oauth/authorize", "mock-protocolsio-auth-code", {
    redirectParam: "redirect_url",
    echoState: true,
  }),

  http.post("/protocolsio/oauth/token", () =>
    HttpResponse.json({
      access_token: "mock-protocolsio-access-token",
      refresh_token: "mock-protocolsio-refresh-token",
      token_type: "bearer",
      expires_in: 3600,
      user: { name: "Mock PIO User", username: "mock-pio-user" },
    }),
  ),
];
