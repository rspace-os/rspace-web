import { beforeEach, describe, expect, it, vi } from "vitest";
import type { AxiosError } from "@/common/axios";

/*
 * On a 401 the interceptor renews the token via refreshToken() (which keeps the app mounted)
 * rather than authenticate() (which remounts it); a 403 triggers no re-auth at all.
 */

const authenticate = vi.fn(() => Promise.resolve());
const refreshToken = vi.fn(() => Promise.resolve());
const removeAlert = vi.fn();
const addAlert = vi.fn();
const mockRootStore = {
  authStore: { authenticate, refreshToken, isAuthenticated: false },
  uiStore: { removeAlert, addAlert },
};

vi.mock("../../stores/stores/getRootStore", () => ({
  default: () => mockRootStore,
}));

vi.mock("../JwtService", () => ({
  default: {
    getToken: () => "token",
    saveToken: vi.fn(),
    destroyToken: vi.fn(),
    secondsToExpiry: () => 100,
  },
}));

import ApiServiceBase from "../ApiServiceBase";

const makeError = (status: number): AxiosError =>
  ({
    config: { headers: {} },
    request: { responseURL: "/api/inventory/v1/files/image/somehash" },
    response: { status },
  }) as unknown as AxiosError;

describe("ApiServiceBase.on401Retry", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockRootStore.authStore.isAuthenticated = false;
  });

  it("refreshes the token silently on a 401 without tearing down the session", async () => {
    const service = new ApiServiceBase("/api/inventory/v1/");

    await expect(service.on401Retry(makeError(401))).rejects.toBeDefined();

    expect(refreshToken).toHaveBeenCalledTimes(1);
    // authenticate() would remount the whole app - it must not be used for a mid-session renewal.
    expect(authenticate).not.toHaveBeenCalled();
  });

  it("passes a 403 straight through without any re-auth", async () => {
    const service = new ApiServiceBase("/api/inventory/v1/");
    const error = makeError(403);

    await expect(service.on401Retry(error)).rejects.toBe(error);

    expect(refreshToken).not.toHaveBeenCalled();
    expect(authenticate).not.toHaveBeenCalled();
  });
});
