import Intercom from "@intercom/messenger-js-sdk";
import { render, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { beforeEach, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import HelpDocs from "@/components/Help/HelpDocs";
import HelpMenu from "../HelpMenu";

vi.mock("@intercom/messenger-js-sdk", () => ({ default: vi.fn() }));
vi.mock("@/components/AppBar/useUiNavigationData", () => ({
  default: () => ({ tag: "loading" }),
}));

beforeEach(() => vi.mocked(Intercom).mockClear());

it("boots anonymous chat from the new shell help controls", async () => {
  render(<HelpMenu helpLinks={[]} livechatProperties={{ livechatEnabled: true, livechatServerKey: "shell-key" }} />);
  await waitFor(() => expect(Intercom).toHaveBeenCalledWith({ app_id: "shell-key", hide_default_launcher: true }));
});

it("boots anonymous chat from legacy HelpDocs even if the server includes identity data", async () => {
  server.use(
    http.get("/session/ajax/livechatProperties", () =>
      HttpResponse.json({
        livechatEnabled: true,
        livechatServerKey: "legacy-key",
        currentUser: { id: 1, email: "private@example.com" },
        livechatUserId: "private-id",
      }),
    ),
  );
  render(<HelpDocs />);
  await waitFor(() => expect(Intercom).toHaveBeenCalledWith({ app_id: "legacy-key", hide_default_launcher: true }));
});
