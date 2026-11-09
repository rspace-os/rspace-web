import { afterEach, beforeEach, expect, it, vi } from "vitest";

const { mint } = vi.hoisted(() => ({ mint: vi.fn() }));
vi.mock("./generated/oauth/oauth", () => ({ createUiOAuthToken: mint }));
const fetchMock = vi.fn<typeof fetch>();
const reload = vi.fn();
const token = (hash?: string, exp = Date.now() / 1000 + 1000) =>
  `e30.${btoa(JSON.stringify({ exp, refreshTokenHash: hash }))}.signature`;

beforeEach(() => {
  vi.resetModules();
  vi.useFakeTimers();
  vi.stubGlobal("fetch", fetchMock);
  vi.stubGlobal("location", { reload, assign: vi.fn() });
});
afterEach(() => {
  vi.clearAllTimers();
  vi.useRealTimers();
  vi.unstubAllGlobals();
  vi.resetAllMocks();
  document.body.innerHTML = "";
});
const provider = async () => (await import("./tokenProvider")).getApiV2TokenProvider();

it("deduplicates minting, reuses tokens, and refreshes before expiry", async () => {
  const first = token("a");
  const second = token("a");
  mint.mockResolvedValueOnce({ accessToken: first }).mockResolvedValueOnce({ accessToken: second });
  const p = await provider();
  expect(await Promise.all([p.getToken(), p.getToken()])).toEqual([first, first]);
  expect(await p.getToken()).toBe(first);
  expect(mint).toHaveBeenCalledTimes(1);
  await vi.advanceTimersByTimeAsync(701_000);
  expect(mint).toHaveBeenCalledTimes(2);
  expect(reload).not.toHaveBeenCalled();
});
it("uses the public document legacy route", async () => {
  document.body.innerHTML = '<div id="public_document_view"></div>';
  fetchMock.mockResolvedValue(Response.json({ data: "public-token" }));
  expect(await (await provider()).getToken()).toBe("public-token");
  expect(fetchMock).toHaveBeenCalledWith("/public/publicView/userform/ajax/inventoryOauthToken", expect.any(Object));
  expect(mint).not.toHaveBeenCalled();
});
it("distinguishes permission 401 from an invalid token", async () => {
  const first = token("a");
  const second = token("a", Date.now() / 1000 + 2000);
  mint.mockResolvedValueOnce({ accessToken: first }).mockResolvedValueOnce({ accessToken: second });
  const p = await provider();
  await p.getToken();
  fetchMock.mockResolvedValueOnce(new Response(null, { status: 200 }));
  expect(await p.recover(first)).toBe(first);
  expect(mint).toHaveBeenCalledTimes(1);
  fetchMock.mockResolvedValueOnce(new Response(null, { status: 401 }));
  expect(await p.recover(first)).toBe(second);
  expect(reload).not.toHaveBeenCalled();
});
it.each(["scheduled", "401"])("blocks delivery after a different identity from %s refresh", async (trigger) => {
  const { ApiV2IdentityChangedError } = await import("./tokenProvider");
  const first = token("a");
  const mismatch = token("b");
  mint.mockResolvedValueOnce({ accessToken: first }).mockResolvedValueOnce({ accessToken: mismatch });
  const p = await provider();
  await p.getToken();
  if (trigger === "scheduled") await vi.advanceTimersByTimeAsync(701_000);
  else {
    fetchMock.mockResolvedValue(new Response(null, { status: 401 }));
    const settled = await Promise.allSettled([p.recover(first), p.recover(first)]);
    for (const result of settled) {
      expect(result).toMatchObject({ status: "rejected", reason: expect.any(ApiV2IdentityChangedError) });
    }
  }
  expect(reload).toHaveBeenCalledTimes(1);
  await expect(p.getToken()).rejects.toBeInstanceOf(ApiV2IdentityChangedError);
  await expect(p.recover(first)).rejects.toBeInstanceOf(ApiV2IdentityChangedError);
  expect(
    fetchMock.mock.calls.every(([, init]) => new Headers(init?.headers).get("Authorization") !== `Bearer ${mismatch}`),
  ).toBe(true);
});
it.each([
  [undefined, "b"],
  ["a", undefined],
])("does not reload with a missing hash: %s -> %s", async (before, after) => {
  const first = token(before);
  const second = token(after);
  mint.mockResolvedValueOnce({ accessToken: first }).mockResolvedValueOnce({ accessToken: second });
  const p = await provider();
  await p.getToken();
  fetchMock.mockResolvedValue(new Response(null, { status: 401 }));
  expect(await p.recover(first)).toBe(second);
  expect(reload).not.toHaveBeenCalled();
});
it("aborts one waiter without cancelling the shared mint", async () => {
  let resolve!: (value: { accessToken: string }) => void;
  mint.mockReturnValue(
    new Promise((done) => {
      resolve = done;
    }),
  );
  const p = await provider();
  const controller = new AbortController();
  const cancelled = p.getToken(controller.signal);
  const waiting = p.getToken();
  controller.abort();
  await expect(cancelled).rejects.toMatchObject({ name: "AbortError" });
  resolve({ accessToken: "token" });
  expect(await waiting).toBe("token");
});

it("rejects all waiters sharing a scheduled mint for another identity", async () => {
  const { ApiV2IdentityChangedError } = await import("./tokenProvider");
  const first = token("a");
  let finish!: (value: { accessToken: string }) => void;
  mint.mockResolvedValueOnce({ accessToken: first }).mockImplementationOnce(
    () =>
      new Promise((resolve) => {
        finish = resolve;
      }),
  );
  const p = await provider();
  await p.getToken();
  await vi.advanceTimersByTimeAsync(701_000);
  const waiters = Promise.allSettled([p.getToken(), p.getToken()]);
  finish({ accessToken: token("b") });
  for (const result of await waiters) {
    expect(result).toMatchObject({ status: "rejected", reason: expect.any(ApiV2IdentityChangedError) });
  }
  expect(reload).toHaveBeenCalledTimes(1);
});

it("rejects public-session expiry with the identity-change error after reloading", async () => {
  const { ApiV2IdentityChangedError } = await import("./tokenProvider");
  document.body.innerHTML = '<div id="public_document_view"></div>';
  fetchMock
    .mockResolvedValueOnce(Response.json({ data: "public-token" }))
    .mockResolvedValueOnce(new Response(null, { status: 401 }));
  const p = await provider();
  await p.getToken();
  await expect(p.recover("public-token")).rejects.toBeInstanceOf(ApiV2IdentityChangedError);
  expect(reload).toHaveBeenCalledTimes(1);
  expect(mint).not.toHaveBeenCalled();
});
