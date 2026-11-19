import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import {
  ApiV2NetworkError,
  ApiV2ProblemError,
  ApiV2SessionEndedError,
  ApiV2UnexpectedResponseError,
} from "./ApiV2ProblemError";
import { ApiV2IdentityChangedError, SessionTokenProvider, setApiV2SessionHandlers } from "./tokenProvider";

// Test setup imports this runtime before test mocks are installed.
const { mint } = vi.hoisted(() => {
  vi.resetModules();
  return { mint: vi.fn() };
});
vi.mock("./generated/oauth/oauth", () => ({ createUiOAuthToken: mint }));
const onSessionEnded = vi.fn();
const onIdentityChanged = vi.fn();
const token = (hash?: string, exp = Date.now() / 1000 + 1000) =>
  `e30.${btoa(JSON.stringify({ exp, refreshTokenHash: hash }))}.signature`;
const html = () => new Response("<html></html>", { headers: { "Content-Type": "text/html;charset=UTF-8" } });
const publicView = () => {
  document.body.innerHTML = '<div id="public_document_view"></div>';
};
/** Moves the client clock past the 300 s expiry buffer of a token minted with the default expiry. */
const nearExpiry = () => vi.setSystemTime(Date.now() + 701_000);

const requests: Request[] = [];
const record = ({ request }: { request: Request }) => {
  requests.push(request);
};

beforeEach(() => {
  vi.useFakeTimers({ toFake: ["Date"] });
  server.events.on("request:start", record);
});
afterEach(() => {
  server.events.removeListener("request:start", record);
  requests.length = 0;
  vi.useRealTimers();
  vi.unstubAllGlobals();
  vi.resetAllMocks();
  document.body.innerHTML = "";
});
const provider = () => new SessionTokenProvider({ onSessionEnded, onIdentityChanged });
/** Answers the public document token route with each response in turn, repeating the last. */
const publicTokenRoute = (...responses: (() => Response)[]) =>
  server.use(
    ...responses.map((response, index) =>
      http.get("/public/publicView/userform/ajax/inventoryOauthToken", response, {
        once: index < responses.length - 1,
      }),
    ),
  );
/** Answers the whoami probe that recovery sends with the token the server rejected. */
const probe = (rejected: string, status: number) =>
  server.use(
    http.get("/api/v1/userDetails/whoami", ({ request }) =>
      request.headers.get("Authorization") === `Bearer ${rejected}` ? new HttpResponse(null, { status }) : undefined,
    ),
  );

it("deduplicates minting, reuses tokens, and re-mints on demand inside the expiry buffer", async () => {
  const first = token("a");
  const second = token("a", Date.now() / 1000 + 2000);
  mint.mockResolvedValueOnce({ accessToken: first }).mockResolvedValueOnce({ accessToken: second });
  const p = provider();
  expect(await Promise.all([p.getToken(), p.getToken()])).toEqual([first, first]);
  expect(await p.getToken()).toBe(first);
  expect(mint).toHaveBeenCalledTimes(1);
  nearExpiry();
  expect(await Promise.all([p.getToken(), p.getToken()])).toEqual([second, second]);
  expect(mint).toHaveBeenCalledTimes(2);
  expect(onIdentityChanged).not.toHaveBeenCalled();
});
it("uses the public document legacy route and stores its token", async () => {
  publicView();
  publicTokenRoute(() => HttpResponse.json({ data: "public-token" }));
  expect(await provider().getToken()).toBe("public-token");
  expect(sessionStorage.getItem("id_token")).toBe("public-token");
  expect(mint).not.toHaveBeenCalled();
});
it("distinguishes permission 401 from an invalid token", async () => {
  const first = token("a");
  const second = token("a", Date.now() / 1000 + 2000);
  mint.mockResolvedValueOnce({ accessToken: first }).mockResolvedValueOnce({ accessToken: second });
  const p = provider();
  await p.getToken();
  probe(first, 200);
  expect(await p.recover(first)).toBe(first);
  expect(mint).toHaveBeenCalledTimes(1);
  probe(first, 401);
  expect(await p.recover(first)).toBe(second);
  expect(onIdentityChanged).not.toHaveBeenCalled();
});
it.each(["expiry", "401"])("blocks delivery after a different identity from a %s mint", async (trigger) => {
  const first = token("a");
  const mismatch = token("b");
  mint.mockResolvedValueOnce({ accessToken: first }).mockResolvedValueOnce({ accessToken: mismatch });
  const p = provider();
  await p.getToken();
  let calls: Promise<string>[];
  if (trigger === "expiry") {
    nearExpiry();
    calls = [p.getToken(), p.getToken()];
  } else {
    probe(first, 401);
    calls = [p.recover(first), p.recover(first)];
  }
  for (const result of await Promise.allSettled(calls)) {
    expect(result).toMatchObject({ status: "rejected", reason: expect.any(ApiV2IdentityChangedError) });
  }
  expect(mint).toHaveBeenCalledTimes(2);
  expect(onIdentityChanged).toHaveBeenCalledTimes(1);
  await expect(p.getToken()).rejects.toBeInstanceOf(ApiV2IdentityChangedError);
  await expect(p.recover(first)).rejects.toBeInstanceOf(ApiV2IdentityChangedError);
  expect(requests.map((request) => request.headers.get("Authorization"))).not.toContain(`Bearer ${mismatch}`);
});
it.each([
  [undefined, "b"],
  ["a", undefined],
])("does not reload with a missing hash: %s -> %s", async (before, after) => {
  const first = token(before);
  const second = token(after);
  mint.mockResolvedValueOnce({ accessToken: first }).mockResolvedValueOnce({ accessToken: second });
  const p = provider();
  await p.getToken();
  probe(first, 401);
  expect(await p.recover(first)).toBe(second);
  expect(onIdentityChanged).not.toHaveBeenCalled();
});
it("aborts one waiter with its own reason without cancelling the shared mint", async () => {
  let resolve!: (value: { accessToken: string }) => void;
  mint.mockReturnValue(
    new Promise((done) => {
      resolve = done;
    }),
  );
  const p = provider();
  const controller = new AbortController();
  const cancelled = p.getToken(controller.signal);
  const waiting = p.getToken();
  const reason = { cancelled: true };
  controller.abort(reason);
  await expect(cancelled).rejects.toBe(reason);
  resolve({ accessToken: "token" });
  expect(await waiting).toBe("token");
});

describe("getToken mint failures", () => {
  it.each([
    ["401", new ApiV2ProblemError(401, undefined, "Unauthorized"), 401],
    ["403", new ApiV2ProblemError(403, undefined, "Forbidden"), 403],
    ["the login page", new ApiV2UnexpectedResponseError("undeclared", { status: 200, media: "text/html" }), 200],
  ])("ends the session on %s", async (_, refusal, status) => {
    mint.mockRejectedValue(refusal);
    const p = provider();
    const [first, second] = await Promise.allSettled([p.getToken(), p.getToken()]);
    expect(first).toMatchObject({ status: "rejected", reason: expect.any(ApiV2SessionEndedError) });
    expect(second).toEqual(first);
    expect(first).toMatchObject({ reason: { status, cause: refusal } });
    expect(onSessionEnded).toHaveBeenCalledExactlyOnceWith(expect.any(ApiV2SessionEndedError));
    expect(onIdentityChanged).not.toHaveBeenCalled();
  });
  it.each([
    ["a network failure", new ApiV2NetworkError(new TypeError("offline"))],
    ["a 5xx", new ApiV2ProblemError(503, undefined, "Unavailable")],
    ["malformed JSON", new ApiV2UnexpectedResponseError("malformedJson", { status: 200, media: "application/json" })],
    ["an invalid body", new ApiV2UnexpectedResponseError("invalidBody", { status: 200, media: "application/json" })],
  ])("rejects %s unchanged and retries on the next call", async (_, failure) => {
    mint.mockRejectedValueOnce(failure).mockResolvedValueOnce({ accessToken: "token" });
    const p = provider();
    await expect(p.getToken()).rejects.toBe(failure);
    expect(await p.getToken()).toBe("token");
    expect(onSessionEnded).not.toHaveBeenCalled();
  });
  it("rejects an empty token as an invalid body", async () => {
    mint.mockResolvedValue({ accessToken: "" });
    await expect(provider().getToken()).rejects.toMatchObject({
      name: "ApiV2UnexpectedResponseError",
      reason: "invalidBody",
    });
  });
});

describe("public document token route", () => {
  it.each([
    ["401", () => new Response(null, { status: 401 })],
    ["403", () => new Response(null, { status: 403 })],
    ["the login page", html],
  ])("reloads on %s once it held a token", async (_, refusal) => {
    publicView();
    publicTokenRoute(() => HttpResponse.json({ data: token("a", Date.now() / 1000 + 100) }), refusal);
    const p = provider();
    await p.getToken();
    await expect(p.getToken()).rejects.toBeInstanceOf(ApiV2IdentityChangedError);
    expect(onIdentityChanged).toHaveBeenCalledTimes(1);
    expect(onSessionEnded).not.toHaveBeenCalled();
  });
  it.each([
    ["401", () => new Response(null, { status: 401 }), ApiV2ProblemError],
    ["the login page", html, ApiV2UnexpectedResponseError],
  ])("rejects an initial %s without a handler, so a refused session does not loop", async (_, refusal, type) => {
    publicView();
    publicTokenRoute(refusal);
    await expect(provider().getToken()).rejects.toBeInstanceOf(type);
    expect(onIdentityChanged).not.toHaveBeenCalled();
    expect(onSessionEnded).not.toHaveBeenCalled();
  });
  it.each([
    ["malformed JSON", () => new Response("{", { headers: { "Content-Type": "application/json" } }), "malformedJson"],
    ["an invalid token body", () => Response.json({ data: 1 }), "invalidBody"],
  ])("normalises %s", async (_, response, reason) => {
    publicView();
    publicTokenRoute(response);
    const error = await provider()
      .getToken()
      .catch((caught: unknown) => caught);
    expect(error).toBeInstanceOf(ApiV2UnexpectedResponseError);
    expect(error).toMatchObject({ reason });
  });
  it("wraps a network failure", async () => {
    publicView();
    publicTokenRoute(() => HttpResponse.error());
    await expect(provider().getToken()).rejects.toMatchObject({
      name: "ApiV2NetworkError",
      cause: expect.any(TypeError),
    });
  });
  it("reloads when the session expires during recovery, without minting", async () => {
    publicView();
    publicTokenRoute(() => HttpResponse.json({ data: "public-token" }));
    probe("public-token", 401);
    const p = provider();
    await p.getToken();
    await expect(p.recover("public-token")).rejects.toBeInstanceOf(ApiV2IdentityChangedError);
    expect(onIdentityChanged).toHaveBeenCalledTimes(1);
    expect(requests).toHaveLength(2);
  });
});

describe("session handlers", () => {
  it("defaults to clearing the stored token and navigating, or reloading", async () => {
    const location = { assign: vi.fn(), reload: vi.fn() };
    vi.stubGlobal("location", location);
    sessionStorage.setItem("id_token", "stale");
    mint.mockRejectedValueOnce(new ApiV2ProblemError(401, undefined, "Unauthorized"));
    const p = new SessionTokenProvider();
    await expect(p.getToken()).rejects.toBeInstanceOf(ApiV2SessionEndedError);
    expect(location.assign).toHaveBeenCalledWith("/login");
    expect(sessionStorage.getItem("id_token")).toBeNull();

    sessionStorage.setItem("id_token", "stale");
    mint.mockResolvedValueOnce({ accessToken: token("a", Date.now() / 1000 + 100) });
    mint.mockResolvedValueOnce({ accessToken: token("b") });
    await p.getToken();
    await expect(p.getToken()).rejects.toBeInstanceOf(ApiV2IdentityChangedError);
    expect(location.reload).toHaveBeenCalledTimes(1);
    expect(sessionStorage.getItem("id_token")).toBeNull();
  });
  it("reads installed handlers at call time until restored", async () => {
    const installed = { onSessionEnded: vi.fn(), onIdentityChanged: vi.fn() };
    const location = { assign: vi.fn(), reload: vi.fn() };
    vi.stubGlobal("location", location);
    const p = new SessionTokenProvider();
    const restore = setApiV2SessionHandlers(installed);
    mint.mockRejectedValue(new ApiV2ProblemError(403, undefined, "Forbidden"));
    await expect(p.getToken()).rejects.toBeInstanceOf(ApiV2SessionEndedError);
    expect(installed.onSessionEnded).toHaveBeenCalledTimes(1);
    expect(location.assign).not.toHaveBeenCalled();
    restore();
    await expect(p.getToken()).rejects.toBeInstanceOf(ApiV2SessionEndedError);
    expect(installed.onSessionEnded).toHaveBeenCalledTimes(1);
    expect(location.assign).toHaveBeenCalledWith("/login");
  });
});
