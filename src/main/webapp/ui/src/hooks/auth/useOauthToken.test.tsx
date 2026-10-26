import { act, renderHook } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import useOauthToken from "@/hooks/auth/useOauthToken";
import { getStoredToken, saveStoredToken } from "@/modules/common/utils/auth";

const MAX_SET_TIMEOUT_DELAY_MS = 2_147_483_647;
let cleanupSetTimeoutSpy = () => {};

beforeEach(() => {
  sessionStorage.clear();
  document.getElementById("public_document_view")?.remove();
  const setTimeoutSpy = vi.spyOn(globalThis, "setTimeout");
  cleanupSetTimeoutSpy = () => {
    setTimeoutSpy.mock.results.forEach((result, index) => {
      if (setTimeoutSpy.mock.calls[index]?.[1] === MAX_SET_TIMEOUT_DELAY_MS) {
        clearTimeout(result.value as ReturnType<typeof setTimeout>);
      }
    });
    setTimeoutSpy.mockRestore();
  };
});

afterEach(() => {
  cleanupSetTimeoutSpy();
  cleanupSetTimeoutSpy = () => {};
  document.getElementById("public_document_view")?.remove();
});

describe("useOauthToken", () => {
  it("mints a fresh REST API v2 token on authenticated pages", async () => {
    saveStoredToken("token-from-an-earlier-session-context");
    let requestCount = 0;
    server.use(
      http.post("/api/v2/oauth/tokens", () => {
        requestCount += 1;
        return HttpResponse.json({ accessToken: "current-session-token" });
      }),
    );
    const { result, unmount } = renderHook(() => useOauthToken());

    try {
      let token: string | undefined;
      await act(async () => {
        token = await result.current.getToken();
      });

      expect(token).toBe("current-session-token");
      expect(requestCount).toBe(1);
      expect(getStoredToken()).toBe("token-from-an-earlier-session-context");
    } finally {
      unmount();
    }
  });

  it("keeps the legacy token endpoint and storage on public document pages", async () => {
    document.body.appendChild(Object.assign(document.createElement("div"), { id: "public_document_view" }));
    saveStoredToken("token-from-authenticated-session-context");
    server.use(
      http.get("/public/publicView/userform/ajax/inventoryOauthToken", () =>
        HttpResponse.json({ data: "public-token" }),
      ),
    );
    const { result, unmount } = renderHook(() => useOauthToken());

    try {
      let token: string | undefined;
      await act(async () => {
        token = await result.current.getToken();
      });

      expect(token).toBe("public-token");
      expect(getStoredToken()).toBe("public-token");
    } finally {
      unmount();
    }
  });
});
