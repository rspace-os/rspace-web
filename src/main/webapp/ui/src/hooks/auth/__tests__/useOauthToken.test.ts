import { renderHook } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { afterEach, describe, expect, test, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import useOauthToken from "../useOauthToken";

const NOW = 1_700_000_000_000;

const fakeJwt = (secondsToExpiry: number) => {
  const encode = (value: object) => btoa(JSON.stringify(value)).replace(/=+$/, "");
  return `${encode({ alg: "HS256" })}.${encode({ exp: NOW / 1000 + secondsToExpiry })}.signature`;
};

const countTokenRequests = (secondsToExpiry: number) => {
  const requests = { count: 0 };
  server.use(
    http.get("/userform/ajax/inventoryOauthToken", () => {
      requests.count++;
      return HttpResponse.json({ data: fakeJwt(secondsToExpiry) });
    }),
  );
  return requests;
};

afterEach(() => {
  vi.clearAllTimers();
  vi.useRealTimers();
  sessionStorage.clear();
});

describe("useOauthToken", () => {
  test("refreshes the token five minutes before it expires", async () => {
    vi.useFakeTimers({ now: NOW, toFake: ["setTimeout", "clearTimeout", "Date"] });
    const requests = countTokenRequests(3600);
    const { result } = renderHook(() => useOauthToken());

    await result.current.getToken();
    expect(requests.count).toBe(1);

    await vi.advanceTimersByTimeAsync((3600 - 300 - 1) * 1000);
    expect(requests.count).toBe(1);

    await vi.advanceTimersByTimeAsync(1000);
    await vi.waitFor(() => expect(requests.count).toBe(2));
  });

  test("waits at least a minute when a fresh token already looks close to expiry", async () => {
    vi.useFakeTimers({ now: NOW, toFake: ["setTimeout", "clearTimeout", "Date"] });
    // A client clock running an hour fast sees every new one-hour token as already expired.
    const requests = countTokenRequests(0);
    const { result } = renderHook(() => useOauthToken());

    await result.current.getToken();
    await vi.advanceTimersByTimeAsync(59 * 1000);
    expect(requests.count).toBe(1);

    await vi.advanceTimersByTimeAsync(1000);
    await vi.waitFor(() => expect(requests.count).toBe(2));
  });
});
