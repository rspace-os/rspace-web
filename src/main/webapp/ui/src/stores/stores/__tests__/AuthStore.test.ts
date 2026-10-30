import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { tokenExpiringIn } from "@/__tests__/helpers/jwt";
import { server } from "@/__tests__/mswServer";
import JwtService from "@/common/JwtService";
import { resetUiTokenForTests } from "@/modules/common/utils/auth";
import AuthStore from "@/stores/stores/AuthStore";
import type { RootStore } from "@/stores/stores/RootStore";

beforeEach(() => {
  resetUiTokenForTests();
  sessionStorage.clear();
  document.getElementById("public_document_view")?.remove();
});

afterEach(() => {
  vi.unstubAllGlobals();
  document.getElementById("public_document_view")?.remove();
});

describe("AuthStore", () => {
  it.each([429, 500])("keeps the page when token minting fails with %s", async (status) => {
    const location = { href: "http://localhost:3000/inventory", origin: "http://localhost:3000" };
    vi.stubGlobal("location", location);
    server.use(http.post("/api/v2/oauth/tokens", () => new HttpResponse(null, { status })));
    const authStore = new AuthStore({} as RootStore);

    await expect(authStore.refreshAuthentication()).rejects.toMatchObject({ response: { status } });

    expect(window.location).toBe(location);
    expect(location.href).toBe("http://localhost:3000/inventory");
    expect(authStore.isSynchronizing).toBe(false);
  });

  it("redirects on an authentication failure", async () => {
    vi.stubGlobal("location", { href: "http://localhost:3000/inventory", origin: "http://localhost:3000" });
    server.use(http.post("/api/v2/oauth/tokens", () => new HttpResponse(null, { status: 401 })));
    const authStore = new AuthStore({} as RootStore);

    await authStore.refreshAuthentication();

    expect(window.location.href).toBe("/login");
  });

  it("mints a fresh session-bound token on authenticated initialization", async () => {
    JwtService.saveToken(tokenExpiringIn(3600));
    const freshToken = tokenExpiringIn(3600);
    let requestCount = 0;
    server.use(
      http.post("/api/v2/oauth/tokens", () => {
        requestCount += 1;
        return HttpResponse.json({ accessToken: freshToken });
      }),
    );
    const authStore = new AuthStore({} as RootStore);

    try {
      await authStore.refreshAuthentication();

      expect(requestCount).toBe(1);
      expect(JwtService.getToken()).toBe(freshToken);
      expect(authStore.isAuthenticated).toBe(true);
      expect(authStore.isSynchronizing).toBe(false);
    } finally {
      if (authStore.timeoutId) clearTimeout(authStore.timeoutId);
    }
  });

  it("mints a fresh legacy token on public document initialization", async () => {
    document.body.appendChild(Object.assign(document.createElement("div"), { id: "public_document_view" }));
    JwtService.saveToken(tokenExpiringIn(3600));
    let requestCount = 0;
    server.use(
      http.get("/public/publicView/userform/ajax/inventoryOauthToken", () => {
        requestCount += 1;
        return HttpResponse.json({ data: "public-legacy-token" });
      }),
    );
    const authStore = new AuthStore({} as RootStore);

    try {
      await authStore.refreshAuthentication();

      expect(requestCount).toBe(1);
      expect(JwtService.getToken()).toBe("public-legacy-token");
      expect(authStore.isAuthenticated).toBe(true);
    } finally {
      if (authStore.timeoutId) clearTimeout(authStore.timeoutId);
    }
  });
});
