import { HttpResponse, http } from "msw";
import { setupServer } from "msw/node";
import { afterAll, afterEach, beforeAll, describe, expect, it } from "vitest";
import { getLivechatProperties } from "@/modules/common/app/queries/livechatProperties";

const server = setupServer();

beforeAll(() => {
  fetchMock.disableMocks();
  server.listen({ onUnhandledRequest: "error" });
});

afterEach(() => server.resetHandlers());

afterAll(() => {
  server.close();
  fetchMock.enableMocks();
});

describe("getLivechatProperties", () => {
  it("parses the enabled backend response", async () => {
    server.use(
      http.get("/session/ajax/livechatProperties", () =>
        HttpResponse.json({
          livechatEnabled: true,
          livechatServerKey: "server-key",
          currentUser: "user-id",
        }),
      ),
    );

    await expect(getLivechatProperties()).resolves.toEqual({
      livechatEnabled: true,
      livechatServerKey: "server-key",
      currentUser: "user-id",
    });
  });

  it("parses the disabled backend response without optional server configuration", async () => {
    server.use(
      http.get("/session/ajax/livechatProperties", () =>
        HttpResponse.json({ livechatEnabled: false, currentUser: "user-id" }),
      ),
    );

    await expect(getLivechatProperties()).resolves.toEqual({
      livechatEnabled: false,
      currentUser: "user-id",
    });
  });
});
