import { HttpResponse, http } from "msw";
import { afterEach, expect, test, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import { getStoredToken, saveStoredToken } from "@/modules/common/utils/auth";
import { fetchWithUiToken } from "@/modules/common/utils/fetchWithUiToken";
import { getShareListing } from "@/modules/share/queries";

afterEach(() => vi.unstubAllGlobals());

test("a stale native-fetch token reloads the tab with a validated replacement without replaying the request", async () => {
  const reload = vi.fn();
  vi.stubGlobal("location", { href: "http://localhost/", origin: "http://localhost", reload });
  saveStoredToken("stale-token");
  let shareRequests = 0;
  server.use(
    http.get("/api/v1/share", () => {
      shareRequests++;
      return HttpResponse.json({}, { status: 401 });
    }),
    http.get("/userform/ajax/inventoryOauthToken", () => HttpResponse.json({ data: "fresh-token" })),
    http.get("/api/v1/userDetails/whoami", ({ request }) => {
      if (request.headers.get("Authorization") === "Bearer stale-token") {
        return HttpResponse.json({}, { status: 401 });
      }
      expect(request.headers.get("Authorization")).toBe("Bearer fresh-token");
      return HttpResponse.json({ username: "current-session-user" });
    }),
  );

  await expect(getShareListing({}, { token: "stale-token" })).rejects.toThrow();

  expect(reload).toHaveBeenCalledOnce();
  expect(getStoredToken()).toBe("fresh-token");
  expect(shareRequests).toBe(1);
});

test("a permission-denied 401 keeps the valid token and does not reload", async () => {
  const reload = vi.fn();
  vi.stubGlobal("location", { href: "http://localhost/", origin: "http://localhost", reload });
  saveStoredToken("valid-token");
  server.use(
    http.get("/api/v1/share", () => HttpResponse.json({}, { status: 401 })),
    http.get("/api/v1/userDetails/whoami", () => HttpResponse.json({ username: "current-user" })),
  );

  await expect(getShareListing({}, { token: "valid-token" })).rejects.toThrow();

  expect(reload).not.toHaveBeenCalled();
  expect(getStoredToken()).toBe("valid-token");
});

test("persistent authentication failure does not cause a reload loop", async () => {
  const reload = vi.fn();
  vi.stubGlobal("location", { href: "http://localhost/", origin: "http://localhost", reload });
  saveStoredToken("stale-token");
  server.use(
    http.get("/api/v1/share", () => HttpResponse.json({}, { status: 401 })),
    http.get("/userform/ajax/inventoryOauthToken", () => HttpResponse.json({ data: "fresh-token" })),
    http.get("/api/v1/userDetails/whoami", () => HttpResponse.json({}, { status: 401 })),
  );

  await expect(getShareListing({}, { token: "stale-token" })).rejects.toThrow();

  expect(reload).not.toHaveBeenCalled();
  expect(getStoredToken()).toBeNull();
});

test("concurrent rejected writes share recovery and are not replayed", async () => {
  const reload = vi.fn();
  vi.stubGlobal("location", { href: "http://localhost/", origin: "http://localhost", reload });
  let writes = 0;
  let tokenRequests = 0;
  const bothWrites = Promise.withResolvers<void>();
  server.use(
    http.post("/api/v1/test-write", () => {
      writes++;
      if (writes === 2) bothWrites.resolve();
      return HttpResponse.json({}, { status: 401 });
    }),
    http.get("/userform/ajax/inventoryOauthToken", () => {
      tokenRequests++;
      return HttpResponse.json({ data: "fresh-token" });
    }),
    http.get("/api/v1/userDetails/whoami", async ({ request }) => {
      if (request.headers.get("Authorization") === "Bearer stale-token") {
        await bothWrites.promise;
        return HttpResponse.json({}, { status: 401 });
      }
      return HttpResponse.json({ username: "current-user" });
    }),
  );

  const responses = await Promise.all(
    [1, 2].map(() =>
      fetchWithUiToken("/api/v1/test-write", {
        method: "POST",
        headers: { Authorization: "Bearer stale-token" },
      }),
    ),
  );

  expect(responses.map((response) => response.status)).toEqual([401, 401]);
  expect(writes).toBe(2);
  expect(tokenRequests).toBe(1);
  expect(reload).toHaveBeenCalledOnce();
});
