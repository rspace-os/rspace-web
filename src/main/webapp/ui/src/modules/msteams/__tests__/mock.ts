import { HttpResponse, http } from "msw";

/**
 * Shape of the payload `MsTeamsMessageSender` posts to a Teams Workflows incoming webhook.
 * See `com.researchspace.extmessages.msteams.AdaptiveCardMessage` / `AdaptiveCard` / `Attachment`.
 */
export type MsTeamsAdaptiveCardMessage = {
  type: string;
  attachments: Array<{
    contentType: string;
    contentUrl: string | null;
    content: {
      $schema: string;
      type: string;
      version: string;
      body: unknown[];
      msteams: { width: string };
    };
  }>;
};

let lastReceivedPayload: MsTeamsAdaptiveCardMessage | null = null;

export const msteamsHandlers = [
  http.post("/msteams/webhook", async ({ request }) => {
    lastReceivedPayload = (await request.json()) as MsTeamsAdaptiveCardMessage;
    return new HttpResponse(null, { status: 200 });
  }),

  http.get("/msteams/webhook/_lastPayload", () => {
    if (lastReceivedPayload === null) {
      return new HttpResponse(null, { status: 404 });
    }
    return HttpResponse.json(lastReceivedPayload);
  }),
];
