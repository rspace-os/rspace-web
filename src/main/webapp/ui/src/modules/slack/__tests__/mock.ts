import { HttpResponse, http } from "msw";

const RSPACE_BASE_URL = process.env.RSPACE_BASE_URL ?? "http://localhost:8080";

export const MOCK_SLACK_WORKSPACE = "Mock Workspace";
export const MOCK_SLACK_CHANNEL = "#mock-channel";

export const slackHandlers = [
  http.get("/slack/oauth/authorize", () => {
    const target = new URL("/slack/redirect_uri", RSPACE_BASE_URL);
    target.searchParams.set("code", "mock-slack-auth-code");
    return HttpResponse.redirect(target.toString(), 302);
  }),

  http.get("/slack-api/oauth.access", ({ request }) => {
    const origin = new URL(request.url).origin;
    return HttpResponse.json({
      ok: true,
      access_token: "mock-slack-access-token",
      scope: "incoming-webhook,commands,channels:history,users:read,files:read,groups:history,im:history,mpim:history",
      user_id: "U_MOCK_USER",
      team_name: MOCK_SLACK_WORKSPACE,
      team_id: "T_MOCK_TEAM",
      incoming_webhook: {
        channel: MOCK_SLACK_CHANNEL,
        channel_id: "C_MOCK_CHANNEL",
        configuration_url: `${origin}/services/mock-configuration`,
        url: `${origin}/services/mock-webhook`,
      },
    });
  }),
];
