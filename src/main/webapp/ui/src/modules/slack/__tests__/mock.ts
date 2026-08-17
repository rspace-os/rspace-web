import { HttpResponse, http } from "msw";

const RSPACE_BASE_URL = process.env.RSPACE_BASE_URL ?? "http://localhost:8080";

export const MOCK_SLACK_WORKSPACE = "Mock Workspace";
export const MOCK_SLACK_CHANNEL = "#mock-channel";

export type SlackWebhookMessage = {
  text: string;
  channel?: string;
  type?: string;
  ts?: string;
  user?: string;
  attachments: Array<{
    title: string;
    title_link: string;
    color: string;
    fields: Array<{ title: string; value: string }>;
  }>;
};

let lastReceivedPayload: SlackWebhookMessage | null = null;

export const slackHandlers = [
  http.get("/slack/oauth/authorize", ({ request }) => {
    const incomingState = new URL(request.url).searchParams.get("state");
    const target = new URL("/slack/redirect_uri", RSPACE_BASE_URL);
    target.searchParams.set("code", "mock-slack-auth-code");
    if (incomingState) target.searchParams.set("state", incomingState);
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

  http.post("/services/mock-webhook", async ({ request }) => {
    lastReceivedPayload = (await request.json()) as SlackWebhookMessage;
    return new HttpResponse("ok", { status: 200 });
  }),

  http.get("/services/mock-webhook/_lastPayload", () => {
    if (lastReceivedPayload === null) {
      return new HttpResponse(null, { status: 404 });
    }
    return HttpResponse.json(lastReceivedPayload);
  }),
];
