import { HttpResponse, http } from "msw";
import { afterEach, expect, test, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import axios from "@/common/axios";
import { getStoredToken, saveStoredToken } from "@/modules/common/utils/auth";
import { fetchWithUiToken } from "@/modules/common/utils/fetchWithUiToken";
import { getShareListing } from "@/modules/share/queries";

afterEach(() => vi.unstubAllGlobals());

function stubLocation() {
  const assign = vi.fn();
  const reload = vi.fn();
  vi.stubGlobal("location", {
    href: "http://localhost/",
    origin: "http://localhost",
    assign,
    reload,
  });
  return { assign, reload };
}

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
  expect(getStoredToken()).toBe("stale-token");
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

async function expectAxiosRequestToRecoverWithoutReplay(request: () => Promise<unknown>) {
  const { reload } = stubLocation();
  saveStoredToken("stale-token");
  let writes = 0;
  server.use(
    http.delete("/api/v1/test-write", () => {
      writes++;
      return HttpResponse.json({}, { status: 401 });
    }),
    http.get("/api/v1/userDetails/whoami", ({ request }) => {
      if (request.headers.get("Authorization") === "Bearer stale-token") {
        return HttpResponse.json({}, { status: 401 });
      }
      expect(request.headers.get("Authorization")).toBe("Bearer fresh-token");
      return HttpResponse.json({ username: "current-user" });
    }),
    http.get("/userform/ajax/inventoryOauthToken", () => HttpResponse.json({ data: "fresh-token" })),
  );

  await expect(request()).rejects.toMatchObject({ response: { status: 401 } });

  expect(writes).toBe(1);
  expect(reload).toHaveBeenCalledOnce();
  expect(getStoredToken()).toBe("fresh-token");
}

test("default axios recovers a stale bearer token without replaying DELETE", async () => {
  await expectAxiosRequestToRecoverWithoutReplay(() =>
    axios.delete("/api/v1/test-write", {
      headers: { Authorization: "Bearer stale-token" },
    }),
  );
});

test("axios.create instances recover a stale bearer token without replaying DELETE", async () => {
  const client = axios.create({ baseURL: "/api/v1" });
  await expectAxiosRequestToRecoverWithoutReplay(() =>
    client.delete("/test-write", {
      headers: { Authorization: "Bearer stale-token" },
    }),
  );
});

test("a valid token recovery in flight does not suppress recovery for an invalid token", async () => {
  const { reload } = stubLocation();
  saveStoredToken("stale-token");
  const validWhoamiStarted = Promise.withResolvers<void>();
  const staleWhoamiStarted = Promise.withResolvers<void>();
  const releaseValidWhoami = Promise.withResolvers<void>();
  let tokenRequests = 0;
  server.use(
    http.get("/api/v1/test-read", () => HttpResponse.json({}, { status: 401 })),
    http.get("/api/v1/userDetails/whoami", async ({ request }) => {
      const authorization = request.headers.get("Authorization");
      if (authorization === "Bearer valid-token") {
        validWhoamiStarted.resolve();
        await releaseValidWhoami.promise;
        return HttpResponse.json({ username: "current-user" });
      }
      if (authorization === "Bearer stale-token") {
        staleWhoamiStarted.resolve();
        return HttpResponse.json({}, { status: 401 });
      }
      expect(authorization).toBe("Bearer fresh-token");
      return HttpResponse.json({ username: "current-user" });
    }),
    http.get("/userform/ajax/inventoryOauthToken", () => {
      tokenRequests++;
      return HttpResponse.json({ data: "fresh-token" });
    }),
  );

  const validRequest = fetchWithUiToken("/api/v1/test-read", {
    headers: { Authorization: "Bearer valid-token" },
  });
  await validWhoamiStarted.promise;
  const staleRequest = fetchWithUiToken("/api/v1/test-read", {
    headers: { Authorization: "Bearer stale-token" },
  });
  try {
    await staleWhoamiStarted.promise;
  } finally {
    releaseValidWhoami.resolve();
  }

  const [validResponse, staleResponse] = await Promise.all([validRequest, staleRequest]);
  expect(validResponse.status).toBe(401);
  expect(staleResponse.status).toBe(401);
  expect(tokenRequests).toBe(1);
  expect(reload).toHaveBeenCalledOnce();
});

test("an Axios permission-denied 401 keeps a valid token and does not reload", async () => {
  const { assign, reload } = stubLocation();
  saveStoredToken("valid-token");
  server.use(
    http.get("/api/v1/test-read", () => HttpResponse.json({}, { status: 401 })),
    http.get("/api/v1/userDetails/whoami", ({ request }) => {
      expect(request.headers.get("Authorization")).toBe("Bearer valid-token");
      return HttpResponse.json({ username: "current-user" });
    }),
  );

  await expect(
    axios.get("/api/v1/test-read", { headers: { Authorization: "Bearer valid-token" } }),
  ).rejects.toMatchObject({ response: { status: 401 } });

  expect(assign).not.toHaveBeenCalled();
  expect(reload).not.toHaveBeenCalled();
  expect(getStoredToken()).toBe("valid-token");
});

test.each([
  ["same-origin non-API", "/public/unauthorized"],
  ["foreign API", "https://foreign.example/api/v1/unauthorized"],
])("an Axios 401 outside the same-origin API scope does not recover (%s)", async (_label, url) => {
  const { assign, reload } = stubLocation();
  saveStoredToken("stale-token");
  server.use(http.get(url, () => HttpResponse.json({}, { status: 401 })));

  await expect(axios.get(url, { headers: { Authorization: "Bearer stale-token" } })).rejects.toMatchObject({
    response: { status: 401 },
  });

  expect(assign).not.toHaveBeenCalled();
  expect(reload).not.toHaveBeenCalled();
  expect(getStoredToken()).toBe("stale-token");
});

test.each([
  ["401", () => HttpResponse.json({}, { status: 401 })],
  ["403", () => HttpResponse.json({}, { status: 403 })],
  ["HTML", () => new HttpResponse("<html>login</html>", { headers: { "Content-Type": "text/html" } })],
  ["missing token", () => HttpResponse.json({})],
])("an expired session token issuance response (%s) navigates to login", async (_label, tokenResponse) => {
  const { assign, reload } = stubLocation();
  saveStoredToken("stale-token");
  server.use(
    http.get("/api/v1/test-read", () => HttpResponse.json({}, { status: 401 })),
    http.get("/api/v1/userDetails/whoami", () => HttpResponse.json({}, { status: 401 })),
    http.get("/userform/ajax/inventoryOauthToken", tokenResponse),
  );

  const response = await fetchWithUiToken("/api/v1/test-read", {
    headers: { Authorization: "Bearer stale-token" },
  });

  expect(response.status).toBe(401);
  expect(assign).toHaveBeenCalledOnce();
  expect(assign).toHaveBeenCalledWith("/login");
  expect(reload).not.toHaveBeenCalled();
  expect(getStoredToken()).toBeNull();
});

test("a transient token issuance failure allows a later recovery retry", async () => {
  const { assign, reload } = stubLocation();
  saveStoredToken("stale-token");
  let tokenRequests = 0;
  server.use(
    http.get("/api/v1/test-read", () => HttpResponse.json({}, { status: 401 })),
    http.get("/api/v1/userDetails/whoami", ({ request }) => {
      if (request.headers.get("Authorization") === "Bearer stale-token") {
        return HttpResponse.json({}, { status: 401 });
      }
      return HttpResponse.json({ username: "current-user" });
    }),
    http.get("/userform/ajax/inventoryOauthToken", () => {
      tokenRequests++;
      return tokenRequests === 1 ? HttpResponse.json({}, { status: 500 }) : HttpResponse.json({ data: "fresh-token" });
    }),
  );

  expect(
    (await fetchWithUiToken("/api/v1/test-read", { headers: { Authorization: "Bearer stale-token" } })).status,
  ).toBe(401);
  expect(assign).not.toHaveBeenCalled();
  expect(reload).not.toHaveBeenCalled();
  expect(getStoredToken()).toBe("stale-token");

  expect(
    (await fetchWithUiToken("/api/v1/test-read", { headers: { Authorization: "Bearer stale-token" } })).status,
  ).toBe(401);
  expect(tokenRequests).toBe(2);
  expect(reload).toHaveBeenCalledOnce();
  expect(getStoredToken()).toBe("fresh-token");
});
