import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

/*
 * refreshToken() must renew the OAuth token without tearing down the mounted app, and a 401 from the
 * token endpoint (the RSpace session itself has expired) must unwind to /login rather than deadlock.
 * The deadlock this guards against: routing the token fetch through the shared 401 interceptor made a
 * 401 re-enter refreshToken(), which returned the in-flight refreshPromise that was itself blocked on
 * that interceptor. The fetch therefore uses an interceptor-free client (see AuthStore.tokenClient).
 */

const { getMock, saveToken, destroyToken } = vi.hoisted(() => ({
  getMock: vi.fn(),
  saveToken: vi.fn(),
  destroyToken: vi.fn(),
}));

vi.mock("@/common/axios", () => ({
  default: {
    create: vi.fn(() => ({ get: getMock })),
    interceptors: { response: { use: vi.fn() } },
  },
}));

vi.mock("@/common/JwtService", () => ({
  default: {
    getToken: () => "token",
    saveToken,
    destroyToken,
    secondsToExpiry: () => 3600,
  },
}));

vi.mock("@/common/InvApiService", () => ({ default: { setAuthorizationHeader: vi.fn() } }));
vi.mock("@/common/ElnApiService", () => ({ default: { setAuthorizationHeader: vi.fn() } }));

import AuthStore from "../AuthStore";

describe("AuthStore.refreshToken", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.useFakeTimers();
    vi.stubGlobal("location", { href: "" });
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it("ends the session and redirects to /login when the token endpoint 401s, rather than deadlocking", async () => {
    getMock.mockRejectedValueOnce({ response: { status: 401 } });
    const store = new AuthStore({} as never);

    // Resolves (does not hang): the interceptor-free fetch rejects straight through to endSession().
    await store.refreshToken();

    expect(destroyToken).toHaveBeenCalledTimes(1);
    expect(store.isAuthenticated).toBe(false);
    expect(window.location.href).toBe("/login");
  });

  it("renews the token in place on success without tearing down the mounted session", async () => {
    getMock.mockResolvedValueOnce({ data: { data: "new.jwt.token" } });
    const store = new AuthStore({} as never);

    await store.refreshToken();

    expect(saveToken).toHaveBeenCalledWith("new.jwt.token");
    expect(store.isAuthenticated).toBe(true);
    expect(destroyToken).not.toHaveBeenCalled();
  });
});
