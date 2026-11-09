import { renderHook } from "@testing-library/react";
import { expect, it, onTestFinished, vi } from "vitest";
import useOauthToken from "@/hooks/auth/useOauthToken";
import { setApiV2TokenProvider } from "@/modules/common/api/v2/tokenProvider";

it("reads the provider again so callers receive its refreshed token", async () => {
  const getToken = vi.fn().mockResolvedValueOnce("first-token").mockResolvedValue("refreshed-token");
  onTestFinished(setApiV2TokenProvider({ getToken, recover: getToken }));
  const { result, unmount } = renderHook(() => useOauthToken());
  onTestFinished(unmount);
  await expect(result.current.getToken()).resolves.toBe("first-token");
  await expect(result.current.getToken()).resolves.toBe("refreshed-token");
});

it("allows another attempt after the provider rejects", async () => {
  const getToken = vi.fn().mockRejectedValueOnce(new TypeError("offline")).mockResolvedValue("retried-token");
  onTestFinished(setApiV2TokenProvider({ getToken, recover: getToken }));
  const { result, unmount } = renderHook(() => useOauthToken());
  onTestFinished(unmount);
  await expect(result.current.getToken()).rejects.toThrow("offline");
  await expect(result.current.getToken()).resolves.toBe("retried-token");
});
