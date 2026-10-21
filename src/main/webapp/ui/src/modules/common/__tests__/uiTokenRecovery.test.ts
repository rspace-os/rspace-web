import { HttpResponse, http } from "msw";
import { afterEach, expect, test, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import axios from "@/common/axios";
import { getStoredToken, saveStoredToken } from "@/modules/common/utils/auth";
import { fetchWithUiToken } from "@/modules/common/utils/fetchWithUiToken";

afterEach(() => {
  document.getElementById("public_document_view")?.remove();
  vi.unstubAllGlobals();
});

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

const jwtWithPayload = (payload: object) =>
  ["{}", JSON.stringify(payload)]
    .map((part) => btoa(part).replace(/=+$/, ""))
    .concat("signature")
    .join(".");
const jwt = (refreshTokenHash: string, issuedAt: number) => jwtWithPayload({ refreshTokenHash, iat: issuedAt });
const jwtWithoutRefreshTokenHash = (issuedAt: number) => jwtWithPayload({ iat: issuedAt });

const clients = ["fetch", "default axios", "axios.create"] as const;
type Client = (typeof clients)[number];
type Method = "GET" | "POST";

function makeRequest(client: Client) {
  const axiosClient =
    client === "axios.create" ? axios.create({ baseURL: "/api/v1" }) : client === "default axios" ? axios : undefined;

  return async (url: string, token: string, method: Method = "GET"): Promise<number> => {
    if (client === "fetch") {
      const response = await fetchWithUiToken(url, {
        method,
        headers: { Authorization: `Bearer ${token}` },
      });
      return response.status;
    }

    if (!axiosClient) throw new Error("Axios client was not created");
    const axiosUrl = client === "axios.create" ? url.replace(/^\/api\/v1/, "") : url;
    try {
      return (
        await axiosClient.request({
          url: axiosUrl,
          method,
          headers: { Authorization: `Bearer ${token}` },
        })
      ).status;
    } catch (error) {
      if (axios.isAxiosError(error) && error.response) return error.response.status;
      throw error;
    }
  };
}

test.each(clients)("%s retries a same-user GET and upgrades a later captured token", async (client) => {
  const request = makeRequest(client);
  const { reload } = stubLocation();
  const staleToken = jwt("same-user", 1);
  const freshToken = jwt("same-user", 2);
  saveStoredToken(staleToken);
  const authorizations: string[] = [];
  let tokenRequests = 0;
  let staleWhoamiRequests = 0;
  let freshWhoamiRequests = 0;
  server.use(
    http.get("/api/v1/test-read", ({ request }) => {
      const authorization = request.headers.get("Authorization") ?? "";
      authorizations.push(authorization);
      return HttpResponse.json({}, { status: authorization === `Bearer ${staleToken}` ? 401 : 200 });
    }),
    http.get("/api/v1/userDetails/whoami", ({ request }) => {
      if (request.headers.get("Authorization") === `Bearer ${staleToken}`) {
        staleWhoamiRequests++;
        return HttpResponse.json({}, { status: 401 });
      }
      freshWhoamiRequests++;
      return HttpResponse.json({ username: "current-user" });
    }),
    http.get("/userform/ajax/inventoryOauthToken", () => {
      tokenRequests++;
      return HttpResponse.json({ data: freshToken });
    }),
  );

  expect(await request("/api/v1/test-read", staleToken)).toBe(200);
  expect(await request("/api/v1/test-read", staleToken)).toBe(200);

  expect(authorizations).toEqual([`Bearer ${staleToken}`, `Bearer ${freshToken}`, `Bearer ${freshToken}`]);
  expect(staleWhoamiRequests).toBe(1);
  expect(freshWhoamiRequests).toBe(1);
  expect(tokenRequests).toBe(1);
  expect(getStoredToken()).toBe(freshToken);
  expect(reload).not.toHaveBeenCalled();
});

test.each(clients)("%s does not replay a same-user write", async (client) => {
  const request = makeRequest(client);
  const { reload } = stubLocation();
  const staleToken = jwt("same-user", 1);
  const freshToken = jwt("same-user", 2);
  saveStoredToken(staleToken);
  let writes = 0;
  let tokenRequests = 0;
  server.use(
    http.post("/api/v1/test-write", () => {
      writes++;
      return HttpResponse.json({}, { status: 401 });
    }),
    http.get("/api/v1/userDetails/whoami", ({ request }) =>
      request.headers.get("Authorization") === `Bearer ${staleToken}`
        ? HttpResponse.json({}, { status: 401 })
        : HttpResponse.json({ username: "current-user" }),
    ),
    http.get("/userform/ajax/inventoryOauthToken", () => {
      tokenRequests++;
      return HttpResponse.json({ data: freshToken });
    }),
  );

  expect(await request("/api/v1/test-write", staleToken, "POST")).toBe(401);

  expect(writes).toBe(1);
  expect(tokenRequests).toBe(1);
  expect(getStoredToken()).toBe(freshToken);
  expect(reload).not.toHaveBeenCalled();
});

test.each(clients)("%s bounds recovery when the retried GET still returns 401", async (client) => {
  const request = makeRequest(client);
  const { reload } = stubLocation();
  const staleToken = jwt("same-user", 1);
  const freshToken = jwt("same-user", 2);
  saveStoredToken(staleToken);
  const authorizations: string[] = [];
  let tokenRequests = 0;
  let whoamiRequests = 0;
  server.use(
    http.get("/api/v1/test-read", ({ request }) => {
      authorizations.push(request.headers.get("Authorization") ?? "");
      return HttpResponse.json({}, { status: 401 });
    }),
    http.get("/api/v1/userDetails/whoami", ({ request }) => {
      whoamiRequests++;
      return request.headers.get("Authorization") === `Bearer ${staleToken}`
        ? HttpResponse.json({}, { status: 401 })
        : HttpResponse.json({ username: "current-user" });
    }),
    http.get("/userform/ajax/inventoryOauthToken", () => {
      tokenRequests++;
      return HttpResponse.json({ data: freshToken });
    }),
  );

  expect(await request("/api/v1/test-read", staleToken)).toBe(401);

  expect(authorizations).toEqual([`Bearer ${staleToken}`, `Bearer ${freshToken}`]);
  expect(whoamiRequests).toBe(2);
  expect(tokenRequests).toBe(1);
  expect(reload).not.toHaveBeenCalled();
});

test.each(clients)("%s does not roll back to an older stored token", async (client) => {
  const request = makeRequest(client);
  const olderStoredToken = jwt("same-user", 1);
  const newerCallerToken = jwt("same-user", 2);
  const { reload } = stubLocation();
  saveStoredToken(olderStoredToken);
  let tokenRequests = 0;
  server.use(
    http.get("/api/v1/test-read", ({ request }) => {
      expect(request.headers.get("Authorization")).toBe(`Bearer ${newerCallerToken}`);
      return HttpResponse.json({});
    }),
    http.get("/api/v1/userDetails/whoami", () => HttpResponse.json({ username: "current-user" })),
    http.get("/userform/ajax/inventoryOauthToken", () => {
      tokenRequests++;
      return HttpResponse.json({ data: olderStoredToken });
    }),
  );

  expect(await request("/api/v1/test-read", newerCallerToken)).toBe(200);

  expect(tokenRequests).toBe(0);
  expect(reload).not.toHaveBeenCalled();
  expect(getStoredToken()).toBe(olderStoredToken);
});

test.each(clients)("%s does not upgrade a captured token to a newer token for another identity", async (client) => {
  const request = makeRequest(client);
  const callerToken = jwt("old-user", 1);
  saveStoredToken(jwt("new-user", 2));
  const authorizations: string[] = [];
  server.use(
    http.get("/api/v1/test-read", ({ request }) => {
      authorizations.push(request.headers.get("Authorization") ?? "");
      return HttpResponse.json({});
    }),
  );

  expect(await request("/api/v1/test-read", callerToken)).toBe(200);

  expect(authorizations).toEqual([`Bearer ${callerToken}`]);
});

test.each(clients)("%s does not replay a request after recovering a different identity", async (client) => {
  const request = makeRequest(client);
  const { reload } = stubLocation();
  const staleToken = jwt("old-user", 1);
  const freshToken = jwt("new-user", 2);
  saveStoredToken(staleToken);
  let protectedRequests = 0;
  let tokenRequests = 0;
  server.use(
    http.get("/api/v1/test-read", () => {
      protectedRequests++;
      return HttpResponse.json({}, { status: 401 });
    }),
    http.get("/api/v1/userDetails/whoami", ({ request }) =>
      request.headers.get("Authorization") === `Bearer ${staleToken}`
        ? HttpResponse.json({}, { status: 401 })
        : HttpResponse.json({ username: "new-user" }),
    ),
    http.get("/userform/ajax/inventoryOauthToken", () => {
      tokenRequests++;
      return HttpResponse.json({ data: freshToken });
    }),
  );

  expect(await request("/api/v1/test-read", staleToken)).toBe(401);

  expect(protectedRequests).toBe(1);
  expect(tokenRequests).toBe(1);
  expect(getStoredToken()).toBe(freshToken);
  expect(reload).toHaveBeenCalledOnce();
});

test.each(["missing", "empty"])(
  "a validated replacement for a token with a %s refresh hash reloads without replay",
  async (hashKind) => {
    const { assign, reload } = stubLocation();
    const staleToken = hashKind === "missing" ? jwtWithoutRefreshTokenHash(1) : jwt("", 1);
    const freshToken = jwt("same-user", 2);
    saveStoredToken(staleToken);
    let protectedRequests = 0;
    let tokenRequests = 0;
    server.use(
      http.get("/api/v1/test-read", () => {
        protectedRequests++;
        return HttpResponse.json({}, { status: 401 });
      }),
      http.get("/api/v1/userDetails/whoami", ({ request }) =>
        request.headers.get("Authorization") === `Bearer ${staleToken}`
          ? HttpResponse.json({}, { status: 401 })
          : HttpResponse.json({ username: "current-user" }),
      ),
      http.get("/userform/ajax/inventoryOauthToken", () => {
        tokenRequests++;
        return HttpResponse.json({ data: freshToken });
      }),
    );

    const response = await fetchWithUiToken("/api/v1/test-read", {
      headers: { Authorization: `Bearer ${staleToken}` },
    });

    expect(response.status).toBe(401);
    expect(protectedRequests).toBe(1);
    expect(tokenRequests).toBe(1);
    expect(getStoredToken()).toBe(freshToken);
    expect(reload).toHaveBeenCalledOnce();
    expect(assign).not.toHaveBeenCalled();
  },
);

test("a public document 401 clears its token and reloads without issuing a new one", async () => {
  const { assign, reload } = stubLocation();
  const staleToken = jwt("public-user", 1);
  saveStoredToken(staleToken);
  const publicDocumentMarker = document.createElement("div");
  publicDocumentMarker.id = "public_document_view";
  document.body.append(publicDocumentMarker);
  let whoamiRequests = 0;
  let tokenRequests = 0;
  server.use(
    http.get("/api/v1/test-read", () => HttpResponse.json({}, { status: 401 })),
    http.get("/api/v1/userDetails/whoami", () => {
      whoamiRequests++;
      return HttpResponse.json({}, { status: 401 });
    }),
    http.get("/userform/ajax/inventoryOauthToken", () => {
      tokenRequests++;
      return HttpResponse.json({ data: jwt("public-user", 2) });
    }),
  );

  expect(
    (await fetchWithUiToken("/api/v1/test-read", { headers: { Authorization: `Bearer ${staleToken}` } })).status,
  ).toBe(401);

  expect(whoamiRequests).toBe(1);
  expect(tokenRequests).toBe(0);
  expect(getStoredToken()).toBeNull();
  expect(reload).toHaveBeenCalledOnce();
  expect(assign).not.toHaveBeenCalled();
});

test.each(["/api/v1/userDetails/whoami", "/api/v1/userDetails/whoami/"])(
  "a 401 from whoami at %s skips the redundant identity probe",
  async (url) => {
    const { reload } = stubLocation();
    const staleToken = jwt("same-user", 1);
    const freshToken = jwt("same-user", 2);
    saveStoredToken(staleToken);
    const whoamiAuthorizations: string[] = [];
    let tokenRequests = 0;
    server.use(
      http.get(/\/api\/v1\/userDetails\/whoami\/?$/, ({ request }) => {
        const authorization = request.headers.get("Authorization") ?? "";
        whoamiAuthorizations.push(authorization);
        return HttpResponse.json(authorization === `Bearer ${staleToken}` ? {} : { username: "current-user" }, {
          status: authorization === `Bearer ${staleToken}` ? 401 : 200,
        });
      }),
      http.get("/userform/ajax/inventoryOauthToken", () => {
        tokenRequests++;
        return HttpResponse.json({ data: freshToken });
      }),
    );

    expect(
      (
        await fetchWithUiToken(url, {
          headers: { Authorization: `Bearer ${staleToken}` },
        })
      ).status,
    ).toBe(200);

    expect(whoamiAuthorizations).toEqual([`Bearer ${staleToken}`, `Bearer ${freshToken}`, `Bearer ${freshToken}`]);
    expect(tokenRequests).toBe(1);
    expect(reload).not.toHaveBeenCalled();
  },
);

test.each([
  ["fetch", "same-origin non-API", "/public/unauthorized"],
  ["fetch", "foreign API", "https://foreign.example/api/v1/unauthorized"],
  ["default axios", "same-origin non-API", "/public/unauthorized"],
  ["default axios", "foreign API", "https://foreign.example/api/v1/unauthorized"],
] as const)("%s does not recover a 401 from %s", async (client, _label, url) => {
  const request = makeRequest(client);
  const { assign, reload } = stubLocation();
  const staleToken = jwt("same-user", 1);
  saveStoredToken(staleToken);
  let recoveryRequests = 0;
  server.use(
    http.get(url, () => HttpResponse.json({}, { status: 401 })),
    http.get("/api/v1/userDetails/whoami", () => {
      recoveryRequests++;
      return HttpResponse.json({}, { status: 401 });
    }),
    http.get("/userform/ajax/inventoryOauthToken", () => {
      recoveryRequests++;
      return HttpResponse.json({ data: jwt("same-user", 2) });
    }),
  );

  expect(await request(url, staleToken)).toBe(401);

  expect(recoveryRequests).toBe(0);
  expect(getStoredToken()).toBe(staleToken);
  expect(assign).not.toHaveBeenCalled();
  expect(reload).not.toHaveBeenCalled();
});

test("an unvalidated replacement token is not stored or replayed", async () => {
  const { assign, reload } = stubLocation();
  const staleToken = jwt("same-user", 1);
  const unvalidatedToken = jwt("same-user", 2);
  saveStoredToken(staleToken);
  let protectedRequests = 0;
  let whoamiRequests = 0;
  let tokenRequests = 0;
  server.use(
    http.get("/api/v1/test-read", () => {
      protectedRequests++;
      return HttpResponse.json({}, { status: 401 });
    }),
    http.get("/api/v1/userDetails/whoami", () => {
      whoamiRequests++;
      return HttpResponse.json({}, { status: 401 });
    }),
    http.get("/userform/ajax/inventoryOauthToken", () => {
      tokenRequests++;
      return HttpResponse.json({ data: unvalidatedToken });
    }),
  );

  const response = await fetchWithUiToken("/api/v1/test-read", {
    headers: { Authorization: `Bearer ${staleToken}` },
  });

  expect(response.status).toBe(401);
  expect(protectedRequests).toBe(1);
  expect(whoamiRequests).toBe(2);
  expect(tokenRequests).toBe(1);
  expect(getStoredToken()).toBe(staleToken);
  expect(reload).not.toHaveBeenCalled();
  expect(assign).not.toHaveBeenCalled();
});

test.each(clients)("%s keeps a valid token on a permission-denied 401", async (client) => {
  const request = makeRequest(client);
  const { reload } = stubLocation();
  const validToken = jwt("same-user", 1);
  saveStoredToken(validToken);
  let tokenRequests = 0;
  const whoamiAuthorizations: string[] = [];
  server.use(
    http.get("/api/v1/test-read", () => HttpResponse.json({}, { status: 401 })),
    http.get("/api/v1/userDetails/whoami", ({ request }) => {
      whoamiAuthorizations.push(request.headers.get("Authorization") ?? "");
      return HttpResponse.json({ username: "current-user" });
    }),
    http.get("/userform/ajax/inventoryOauthToken", () => {
      tokenRequests++;
      return HttpResponse.json({ data: jwt("same-user", 2) });
    }),
  );

  expect(await request("/api/v1/test-read", validToken)).toBe(401);

  expect(whoamiAuthorizations).toEqual([`Bearer ${validToken}`]);
  expect(tokenRequests).toBe(0);
  expect(getStoredToken()).toBe(validToken);
  expect(reload).not.toHaveBeenCalled();
});

test.each([
  ["401", () => HttpResponse.json({}, { status: 401 })],
  ["403", () => HttpResponse.json({}, { status: 403 })],
  ["HTML", () => new HttpResponse("<html>login</html>", { headers: { "Content-Type": "text/html" } })],
  ["missing token", () => HttpResponse.json({})],
])("an expired session token issuance response (%s) navigates to login", async (_label, tokenResponse) => {
  const { assign, reload } = stubLocation();
  const staleToken = jwt("same-user", 1);
  saveStoredToken(staleToken);
  server.use(
    http.get("/api/v1/test-read", () => HttpResponse.json({}, { status: 401 })),
    http.get("/api/v1/userDetails/whoami", () => HttpResponse.json({}, { status: 401 })),
    http.get("/userform/ajax/inventoryOauthToken", tokenResponse),
  );

  expect(
    (await fetchWithUiToken("/api/v1/test-read", { headers: { Authorization: `Bearer ${staleToken}` } })).status,
  ).toBe(401);

  expect(assign).toHaveBeenCalledOnce();
  expect(assign).toHaveBeenCalledWith("/login");
  expect(reload).not.toHaveBeenCalled();
  expect(getStoredToken()).toBeNull();
});

test.each([
  ["JSON 500", () => HttpResponse.json({}, { status: 500 })],
  [
    "HTML 502",
    () => new HttpResponse("<html>Bad Gateway</html>", { status: 502, headers: { "Content-Type": "text/html" } }),
  ],
])("a transient token issuance failure (%s) allows a later recovery retry", async (_label, failure) => {
  const { assign, reload } = stubLocation();
  const staleToken = jwt("same-user", 1);
  const freshToken = jwt("same-user", 2);
  saveStoredToken(staleToken);
  let tokenRequests = 0;
  server.use(
    http.get("/api/v1/test-read", ({ request }) =>
      HttpResponse.json({}, { status: request.headers.get("Authorization") === `Bearer ${staleToken}` ? 401 : 200 }),
    ),
    http.get("/api/v1/userDetails/whoami", ({ request }) =>
      request.headers.get("Authorization") === `Bearer ${staleToken}`
        ? HttpResponse.json({}, { status: 401 })
        : HttpResponse.json({ username: "current-user" }),
    ),
    http.get("/userform/ajax/inventoryOauthToken", () => {
      tokenRequests++;
      return tokenRequests === 1 ? failure() : HttpResponse.json({ data: freshToken });
    }),
  );

  expect(
    (await fetchWithUiToken("/api/v1/test-read", { headers: { Authorization: `Bearer ${staleToken}` } })).status,
  ).toBe(401);
  expect(assign).not.toHaveBeenCalled();
  expect(reload).not.toHaveBeenCalled();
  expect(getStoredToken()).toBe(staleToken);

  expect(
    (await fetchWithUiToken("/api/v1/test-read", { headers: { Authorization: `Bearer ${staleToken}` } })).status,
  ).toBe(200);
  expect(tokenRequests).toBe(2);
  expect(reload).not.toHaveBeenCalled();
  expect(getStoredToken()).toBe(freshToken);
});

test("concurrent rejected writes share recovery and are not replayed", async () => {
  const { reload } = stubLocation();
  const staleToken = jwt("same-user", 1);
  const freshToken = jwt("same-user", 2);
  saveStoredToken(staleToken);
  let writes = 0;
  let tokenRequests = 0;
  const bothWrites = Promise.withResolvers<void>();
  server.use(
    http.post("/api/v1/test-write", () => {
      writes++;
      if (writes === 2) bothWrites.resolve();
      return HttpResponse.json({}, { status: 401 });
    }),
    http.get("/api/v1/userDetails/whoami", async ({ request }) => {
      if (request.headers.get("Authorization") === `Bearer ${staleToken}`) {
        await bothWrites.promise;
        return HttpResponse.json({}, { status: 401 });
      }
      return HttpResponse.json({ username: "current-user" });
    }),
    http.get("/userform/ajax/inventoryOauthToken", () => {
      tokenRequests++;
      return HttpResponse.json({ data: freshToken });
    }),
  );

  const responses = await Promise.all(
    [1, 2].map(() =>
      fetchWithUiToken("/api/v1/test-write", {
        method: "POST",
        headers: { Authorization: `Bearer ${staleToken}` },
      }),
    ),
  );

  expect(responses.map((response) => response.status)).toEqual([401, 401]);
  expect(writes).toBe(2);
  expect(tokenRequests).toBe(1);
  expect(getStoredToken()).toBe(freshToken);
  expect(reload).not.toHaveBeenCalled();
});

test("a valid-token identity check in flight does not suppress another token's recovery", async () => {
  const { reload } = stubLocation();
  const staleToken = jwt("same-user", 1);
  const validToken = jwt("same-user", 2);
  const freshToken = jwt("same-user", 3);
  saveStoredToken(staleToken);
  const validWhoamiStarted = Promise.withResolvers<void>();
  const staleWhoamiStarted = Promise.withResolvers<void>();
  const releaseValidWhoami = Promise.withResolvers<void>();
  let tokenRequests = 0;
  const authorizations: string[] = [];
  server.use(
    http.get("/api/v1/test-read", ({ request }) => {
      authorizations.push(request.headers.get("Authorization") ?? "");
      return HttpResponse.json({}, { status: 401 });
    }),
    http.get("/api/v1/userDetails/whoami", async ({ request }) => {
      const authorization = request.headers.get("Authorization");
      if (authorization === `Bearer ${validToken}`) {
        validWhoamiStarted.resolve();
        await releaseValidWhoami.promise;
        return HttpResponse.json({ username: "current-user" });
      }
      if (authorization === `Bearer ${staleToken}`) {
        staleWhoamiStarted.resolve();
        return HttpResponse.json({}, { status: 401 });
      }
      expect(authorization).toBe(`Bearer ${freshToken}`);
      return HttpResponse.json({ username: "current-user" });
    }),
    http.get("/userform/ajax/inventoryOauthToken", () => {
      tokenRequests++;
      return HttpResponse.json({ data: freshToken });
    }),
  );

  const validRequest = fetchWithUiToken("/api/v1/test-read", {
    headers: { Authorization: `Bearer ${validToken}` },
  });
  await validWhoamiStarted.promise;
  const staleRequest = fetchWithUiToken("/api/v1/test-read", {
    headers: { Authorization: `Bearer ${staleToken}` },
  });
  try {
    await staleWhoamiStarted.promise;
  } finally {
    releaseValidWhoami.resolve();
  }

  const [validResponse, staleResponse] = await Promise.all([validRequest, staleRequest]);
  expect(validResponse.status).toBe(401);
  expect(staleResponse.status).toBe(401);
  expect(authorizations).toEqual([`Bearer ${validToken}`, `Bearer ${staleToken}`, `Bearer ${freshToken}`]);
  expect(tokenRequests).toBe(1);
  expect(getStoredToken()).toBe(freshToken);
  expect(reload).not.toHaveBeenCalled();
});
