import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, expectTypeOf, it, vi } from "vitest";
import { OAUTH_TOKEN } from "@/__tests__/mocks/oauthTokenMocks";
import { server } from "@/__tests__/mswServer";
import { ApiV2SessionEndedError } from "./ApiV2ProblemError";
import { getApiV2Config } from "./generated/config/config";
import type { ApiV2Config } from "./generated/models/apiV2Config";
import { getCurrentUserProfileImage } from "./generated/users/users";
import { SessionTokenProvider, setApiV2TokenProvider } from "./tokenProvider";

it("returns a binary success from the generated client as a Blob with its headers", async () => {
  const bytes = new Uint8Array([137, 80, 78, 71]);
  server.use(
    http.get(
      "/api/v2/users/me/profile-image",
      () => new HttpResponse(bytes, { headers: { "Content-Type": "image/png", "Cache-Control": "no-store" } }),
    ),
  );

  const image = await getCurrentUserProfileImage();
  expectTypeOf(image).toEqualTypeOf<{ body: Blob; headers: Headers }>();
  expect(new Uint8Array(await image.body.arrayBuffer())).toEqual(bytes);
  expect(image.headers.get("Cache-Control")).toBe("no-store");
});

describe("with a session token provider", () => {
  const onSessionEnded = vi.fn();
  const onIdentityChanged = vi.fn();
  const config: ApiV2Config = {
    branding: { bannerImageUrl: "/banner.png" },
    deploymentDescription: null,
    deploymentHelpEmail: null,
    helpLinks: [],
    version: "1.0.0",
  };
  const requests: Request[] = [];
  const record = ({ request }: { request: Request }) => {
    requests.push(request);
  };
  /** Each request as "METHOD path credentials". */
  const sent = () =>
    requests.map(
      (request) =>
        `${request.method} ${new URL(request.url).pathname} ${request.headers.get("Authorization") ?? "anonymous"}`,
    );
  const issueToken = () =>
    http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: OAUTH_TOKEN }), { once: true });
  const refuseTokens = () => http.post("/api/v2/oauth/tokens", () => new HttpResponse(null, { status: 401 }));
  /** The public config read, which rejects any bearer token as it would once the session has ended. */
  const configRoute = () =>
    http.get("/api/v2/config", ({ request }) =>
      request.headers.has("Authorization") ? new HttpResponse(null, { status: 401 }) : HttpResponse.json(config),
    );
  let restore: () => void;

  beforeEach(() => {
    server.events.on("request:start", record);
    restore = setApiV2TokenProvider(new SessionTokenProvider({ onSessionEnded, onIdentityChanged }));
  });
  afterEach(() => {
    server.events.removeListener("request:start", record);
    requests.length = 0;
    restore();
    vi.resetAllMocks();
  });

  it("reads a public operation anonymously when the token route refuses, without ending the session", async () => {
    server.use(refuseTokens(), configRoute());
    expect(await getApiV2Config()).toEqual(config);
    expect(sent()).toEqual(["POST /api/v2/oauth/tokens anonymous", "GET /api/v2/config anonymous"]);
    expect(onSessionEnded).not.toHaveBeenCalled();
  });

  it("replays a public read anonymously when the recovery mint is refused, without ending the session", async () => {
    server.use(issueToken(), refuseTokens(), configRoute());
    expect(await getApiV2Config()).toEqual(config);
    expect(sent()).toEqual([
      "POST /api/v2/oauth/tokens anonymous",
      `GET /api/v2/config Bearer ${OAUTH_TOKEN}`,
      "POST /api/v2/oauth/tokens anonymous",
      "GET /api/v2/config anonymous",
    ]);
    expect(onSessionEnded).not.toHaveBeenCalled();
  });

  it("does not send the refused session's token with the next public read", async () => {
    server.use(issueToken(), refuseTokens(), configRoute());
    await getApiV2Config();
    requests.length = 0;
    expect(await getApiV2Config()).toEqual(config);
    expect(sent()).toEqual(["POST /api/v2/oauth/tokens anonymous", "GET /api/v2/config anonymous"]);
  });

  it("ends the session when a protected operation cannot get a token", async () => {
    server.use(refuseTokens());
    await expect(getCurrentUserProfileImage()).rejects.toBeInstanceOf(ApiV2SessionEndedError);
    expect(sent()).toEqual(["POST /api/v2/oauth/tokens anonymous"]);
    expect(onSessionEnded).toHaveBeenCalledOnce();
  });
});
