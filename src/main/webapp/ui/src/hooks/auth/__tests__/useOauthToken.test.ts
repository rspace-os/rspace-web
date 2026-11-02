import { renderHook } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import useOauthToken from "../useOauthToken";

const NOW = 1_700_000_000_000;
const TOKEN_URL = "/userform/ajax/inventoryOauthToken";

const fakeJwt = (secondsToExpiry: number) => {
  const encode = (value: object) => btoa(JSON.stringify(value)).replace(/=+$/, "");
  return `${encode({ alg: "HS256" })}.${encode({ exp: NOW / 1000 + secondsToExpiry })}.signature`;
};

const serveTokens = (secondsToExpiry: number) => {
  const tokenEndpoint = vi.fn(() => HttpResponse.json({ data: fakeJwt(secondsToExpiry) }));
  server.use(http.get(TOKEN_URL, tokenEndpoint));
  return tokenEndpoint;
};

beforeEach(() => {
  vi.useFakeTimers({ now: NOW });
});

afterEach(() => {
  vi.useRealTimers();
});

describe("useOauthToken", () => {
  test("refreshes the token five minutes before it expires", async () => {
    const tokenEndpoint = serveTokens(3600);
    const { result } = renderHook(() => useOauthToken());

    await result.current.getToken();
    expect(tokenEndpoint).toHaveBeenCalledTimes(1);

    await vi.advanceTimersByTimeAsync((3600 - 300 - 1) * 1000);
    expect(tokenEndpoint).toHaveBeenCalledTimes(1);

    await vi.advanceTimersByTimeAsync(1000);
    expect(tokenEndpoint).toHaveBeenCalledTimes(2);
  });

  test("waits at least a minute when a fresh token already looks close to expiry", async () => {
    // A client clock running an hour fast sees every new one-hour token as already expired.
    const tokenEndpoint = serveTokens(0);
    const { result } = renderHook(() => useOauthToken());

    await result.current.getToken();
    await vi.advanceTimersByTimeAsync(59 * 1000);
    expect(tokenEndpoint).toHaveBeenCalledTimes(1);

    await vi.advanceTimersByTimeAsync(1000);
    expect(tokenEndpoint).toHaveBeenCalledTimes(2);
  });

  test("starts refreshing again after a failed refresh", async () => {
    serveTokens(3600);
    const { result } = renderHook(() => useOauthToken());
    await result.current.getToken();

    server.use(http.get(TOKEN_URL, () => new HttpResponse(null, { status: 403 })));
    await vi.advanceTimersByTimeAsync((3600 - 300) * 1000);

    const tokenEndpoint = serveTokens(3600);
    await result.current.getToken();
    await vi.advanceTimersByTimeAsync(60 * 1000);
    expect(tokenEndpoint).toHaveBeenCalledTimes(1);
  });
});
