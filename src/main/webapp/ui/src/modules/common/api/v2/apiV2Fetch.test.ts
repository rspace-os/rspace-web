import * as v from "valibot";
import { afterEach, beforeEach, describe, expect, expectTypeOf, it, vi } from "vitest";
import { ApiV2ProblemError } from "./ApiV2ProblemError";
import { apiV2Fetch } from "./apiV2Fetch";
import { apiV2BlobFetch, apiV2HeadersFetch } from "./apiV2HeadersFetch";
import { ApiV2IdentityChangedError, getApiV2TokenProvider, setApiV2TokenProvider } from "./tokenProvider";

// Test setup imports this runtime before test mocks are installed.
vi.hoisted(() => vi.resetModules());

vi.mock("./generated/valibot/operations", async () => {
  const v = await import("valibot");
  const response = { "200": { "application/json": v.object({ id: v.number() }) } };
  return {
    apiV2Operations: {
      read: { responses: response },
      publicRead: { public: true, responses: response },
      sessionRead: { session: true, responses: response },
      write: { request: v.object({ name: v.string() }), responses: response },
      createUiOAuthToken: {
        session: true,
        responses: { "200": { "application/json": v.object({ accessToken: v.string() }) } },
      },
    },
  };
});
vi.mock("./generated/valibot/schemas", async () => {
  const v = await import("valibot");
  return {
    ApiV2ProblemSchema: v.looseObject({
      status: v.number(),
      title: v.string(),
      code: v.string(),
      detail: v.optional(v.string()),
    }),
  };
});

const fetchMock = vi.fn<typeof fetch>();
const getToken = vi.fn(async () => "old");
const recover = vi.fn(async () => "new");
let restore: () => void;
const options = (operationId = "read", init: RequestInit = {}) => ({ ...init, operationId });

beforeEach(() => {
  vi.stubGlobal("fetch", fetchMock);
  getToken.mockResolvedValue("old");
  recover.mockResolvedValue("new");
  restore = setApiV2TokenProvider({ getToken, recover });
});
afterEach(() => {
  restore();
  vi.unstubAllGlobals();
  vi.resetAllMocks();
});

describe("API v2 transport", () => {
  it("keeps original JSON extension data and sets protected headers", async () => {
    fetchMock.mockResolvedValue(Response.json({ id: 1, extension: { value: 2 } }));
    expect(await apiV2Fetch("/resource", options())).toEqual({ id: 1, extension: { value: 2 } });
    const headers = new Headers(fetchMock.mock.calls[0][1]?.headers);
    expect(headers.get("Authorization")).toBe("Bearer old");
    expect(headers.get("X-Requested-With")).toBe("XMLHttpRequest");
    expect(fetchMock.mock.calls[0][1]).not.toHaveProperty("operationId");
  });
  it("rejects unregistered operations before minting or sending", async () => {
    await expect(apiV2Fetch("/resource", options("missing"))).rejects.toThrow("Unregistered");
    expect(getToken).not.toHaveBeenCalled();
    expect(fetchMock).not.toHaveBeenCalled();
  });
  it("rejects invalid requests with invalidParams before sending", async () => {
    const request = apiV2Fetch("/resource", options("write", { method: "POST", body: JSON.stringify({ name: 2 }) }));
    await expect(request).rejects.toMatchObject({
      status: 400,
      code: "errors.api.v2.invalidRequest",
      problem: { invalidParams: [{ name: "name", reason: expect.any(String) }] },
    });
    await request.catch((error: unknown) => {
      expect(error).toBeInstanceOf(ApiV2ProblemError);
      expect(error).toHaveProperty("problem");
      expect(error).not.toHaveProperty("problem.detail");
    });
    expect(fetchMock).not.toHaveBeenCalled();
  });
  it("validates success responses", async () => {
    fetchMock.mockResolvedValue(Response.json({ id: "bad" }));
    await expect(apiV2Fetch("/resource", options())).rejects.toBeInstanceOf(v.ValiError);
  });
  it.each(["GET", "POST"])("recovers a 401 and replays only GET: %s", async (method) => {
    fetchMock
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(Response.json({ id: 1 }));
    const result = apiV2Fetch("/resource", options("read", { method }));
    if (method === "GET") {
      expect(await result).toEqual({ id: 1 });
      expect(fetchMock).toHaveBeenCalledTimes(2);
    } else {
      await expect(result).rejects.toMatchObject({ status: 401 });
      expect(fetchMock).toHaveBeenCalledTimes(1);
    }
    expect(recover).toHaveBeenCalledWith("old");
  });
  it("preserves permission 401 when recovery retains the token", async () => {
    recover.mockResolvedValue("old");
    fetchMock.mockResolvedValue(new Response(null, { status: 401 }));
    await expect(apiV2Fetch("/resource", options())).rejects.toMatchObject({ status: 401 });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
  it("sets problem only for valid problems and preserves extensions", async () => {
    const body = { status: 409, title: "Conflict", code: "conflict", conflict: { id: 9 } };
    fetchMock.mockResolvedValueOnce(Response.json(body, { status: 409 }));
    await expect(apiV2Fetch("/resource", options())).rejects.toMatchObject({ problem: body, message: "Conflict" });
    fetchMock.mockResolvedValueOnce(Response.json({ status: "bad" }, { status: 400 }));
    await expect(apiV2Fetch("/resource", options())).rejects.toMatchObject({ status: 400, problem: undefined });
  });
  it("wraps non-JSON and malformed JSON HTTP errors", async () => {
    for (const response of [
      new Response("html", { status: 500 }),
      new Response("{", { status: 500, headers: { "Content-Type": "application/problem+json" } }),
    ]) {
      fetchMock.mockResolvedValueOnce(response);
      await expect(apiV2Fetch("/resource", options())).rejects.toBeInstanceOf(ApiV2ProblemError);
    }
  });
  it("returns undefined for 204", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    expect(await apiV2Fetch("/resource", options())).toBeUndefined();
  });
  it("propagates abort and network failures", async () => {
    const controller = new AbortController();
    controller.abort();
    await expect(apiV2Fetch("/resource", options("read", { signal: controller.signal }))).rejects.toMatchObject({
      name: "AbortError",
    });
    expect(fetchMock).not.toHaveBeenCalled();
    const error = new TypeError("network");
    fetchMock.mockRejectedValue(error);
    await expect(apiV2Fetch("/resource", options())).rejects.toBe(error);
  });
  it("retains headers in JSON and Blob mutators", async () => {
    fetchMock.mockResolvedValueOnce(Response.json({ id: 1 }, { headers: { ETag: "version" } }));
    const json = await apiV2HeadersFetch("/resource", options());
    expect(json.body).toEqual({ id: 1 });
    expect(json.headers.get("ETag")).toBe("version");
    fetchMock.mockResolvedValueOnce(new Response("calendar", { headers: { "Content-Disposition": "attachment" } }));
    const binary = await apiV2BlobFetch<string>("/resource", options());
    expectTypeOf(binary.body).toEqualTypeOf<Blob>();
    expect(await binary.body.text()).toBe("calendar");
    expect(binary.headers.get("Content-Disposition")).toBe("attachment");
  });
  it("does not request a token for token minting", async () => {
    fetchMock.mockResolvedValue(Response.json({ accessToken: "minted" }));
    await apiV2Fetch(
      "/api/v2/oauth/tokens",
      options("createUiOAuthToken", { headers: { Authorization: "untrusted" } }),
    );
    expect(getToken).not.toHaveBeenCalled();
    expect(new Headers(fetchMock.mock.calls[0][1]?.headers).has("Authorization")).toBe(false);
  });
  it("restores nested provider overrides", () => {
    const previous = getApiV2TokenProvider();
    const next = { getToken: async () => "next", recover: async () => "next" };
    const undo = setApiV2TokenProvider(next);
    expect(getApiV2TokenProvider()).toBe(next);
    undo();
    expect(getApiV2TokenProvider()).toBe(previous);
  });
});

it("exposes request options without operationId to generated callers", () => {
  expectTypeOf<Parameters<typeof apiV2Fetch>[1]>().toEqualTypeOf<RequestInit | undefined>();
  expectTypeOf<Parameters<typeof apiV2HeadersFetch>[1]>().toEqualTypeOf<RequestInit | undefined>();
  expectTypeOf<Parameters<typeof apiV2BlobFetch>[1]>().toEqualTypeOf<RequestInit | undefined>();
});

it("never replays a request when the provider blocks an identity change", async () => {
  recover.mockRejectedValue(new ApiV2IdentityChangedError());
  fetchMock.mockResolvedValue(new Response(null, { status: 401 }));
  await expect(apiV2Fetch("/resource", options())).rejects.toMatchObject({ status: 401 });
  expect(fetchMock).toHaveBeenCalledTimes(1);
});

const mutators = [
  { name: "JSON", mutator: apiV2Fetch },
  { name: "headers", mutator: apiV2HeadersFetch },
  { name: "Blob", mutator: apiV2BlobFetch },
];
const unauthenticatedCases = ["publicRead", "sessionRead"].flatMap((operationId) =>
  mutators.map((entry) => ({ ...entry, operationId })),
);

it.each(unauthenticatedCases)(
  "$name $operationId ignores mint failures and removes Authorization",
  async ({ mutator, operationId }) => {
    getToken.mockRejectedValue(new Error("Logged out: token mint failed"));
    fetchMock.mockResolvedValue(Response.json({ id: 1 }));
    await mutator("/public", options(operationId, { headers: { Authorization: "Bearer stale" } }));
    expect(getToken).not.toHaveBeenCalled();
    expect(recover).not.toHaveBeenCalled();
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const headers = new Headers(fetchMock.mock.calls[0][1]?.headers);
    expect(headers.has("Authorization")).toBe(false);
    expect(headers.get("X-Requested-With")).toBe("XMLHttpRequest");
  },
);

it.each(unauthenticatedCases)(
  "$name $operationId 401 remains an HTTP error without recovery",
  async ({ mutator, operationId }) => {
    fetchMock.mockResolvedValue(new Response(null, { status: 401 }));
    await expect(mutator("/public", options(operationId))).rejects.toBeInstanceOf(ApiV2ProblemError);
    expect(getToken).not.toHaveBeenCalled();
    expect(recover).not.toHaveBeenCalled();
    expect(fetchMock).toHaveBeenCalledTimes(1);
  },
);

it("protected operations still require a token", async () => {
  const error = new Error("Token mint failed");
  getToken.mockRejectedValue(error);
  await expect(apiV2Fetch("/resource", options())).rejects.toBe(error);
  expect(getToken).toHaveBeenCalledTimes(1);
  expect(fetchMock).not.toHaveBeenCalled();
});

it("prefers an occurrence detail over the stable problem title", async () => {
  fetchMock.mockResolvedValueOnce(
    Response.json({ status: 400, title: "Limit exceeded", code: "limit", detail: "Limit: 3" }, { status: 400 }),
  );
  await expect(apiV2Fetch("/resource", options())).rejects.toMatchObject({ message: "Limit: 3" });
});
