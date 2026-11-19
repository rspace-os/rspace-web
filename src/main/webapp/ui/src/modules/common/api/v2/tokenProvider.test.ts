import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import {
  ApiV2NetworkError,
  ApiV2ProblemError,
  ApiV2SessionEndedError,
  ApiV2UnexpectedResponseError,
} from "./ApiV2ProblemError";
import {
  ApiV2IdentityChangedError,
  defaultApiV2SessionHandlers,
  getApiV2TokenProvider,
  SessionTokenProvider,
  setApiV2TokenProvider,
} from "./tokenProvider";

// Test setup imports this runtime and installs a provider before test mocks are installed; reset to a fresh module.
const { mint } = vi.hoisted(() => {
  vi.resetModules();
  return { mint: vi.fn() };
});
vi.mock("./generated/oauth/oauth", () => ({ createUiOAuthToken: mint }));
const onSessionEnded = vi.fn();
const onIdentityChanged = vi.fn();
const token = (hash?: string, exp = Date.now() / 1000 + 1000) =>
  `e30.${btoa(JSON.stringify({ exp, refreshTokenHash: hash }))}.signature`;
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
});
const provider = () => new SessionTokenProvider({ onSessionEnded, onIdentityChanged });

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
it("replaces a rejected token once and hands later callers the replacement", async () => {
  const first = token("a");
  const second = token("a", Date.now() / 1000 + 2000);
  mint.mockResolvedValueOnce({ accessToken: first }).mockResolvedValueOnce({ accessToken: second });
  const p = provider();
  await p.getToken();
  expect(await Promise.all([p.recover(first), p.recover(first)])).toEqual([second, second]);
  expect(await p.recover(first)).toBe(second);
  expect(mint).toHaveBeenCalledTimes(2);
  expect(requests).toHaveLength(0);
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

describe("defaultApiV2SessionHandlers", () => {
  it("navigates to the login page when the session ends, and reloads when the identity changes", async () => {
    const location = { assign: vi.fn(), reload: vi.fn() };
    vi.stubGlobal("location", location);
    mint.mockRejectedValueOnce(new ApiV2ProblemError(401, undefined, "Unauthorized"));
    const p = new SessionTokenProvider(defaultApiV2SessionHandlers);
    await expect(p.getToken()).rejects.toBeInstanceOf(ApiV2SessionEndedError);
    expect(location.assign).toHaveBeenCalledWith("/login");

    mint.mockResolvedValueOnce({ accessToken: token("a", Date.now() / 1000 + 100) });
    mint.mockResolvedValueOnce({ accessToken: token("b") });
    await p.getToken();
    await expect(p.getToken()).rejects.toBeInstanceOf(ApiV2IdentityChangedError);
    expect(location.reload).toHaveBeenCalledTimes(1);
  });
});

it("fails fast until an entry point installs a provider, and restores the previous one", () => {
  expect(() => getApiV2TokenProvider()).toThrow(/setApiV2TokenProvider/);
  const installed = provider();
  const restore = setApiV2TokenProvider(installed);
  expect(getApiV2TokenProvider()).toBe(installed);
  restore();
  expect(() => getApiV2TokenProvider()).toThrow(/setApiV2TokenProvider/);
});
