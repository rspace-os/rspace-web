import * as v from "valibot";
import { afterEach, beforeEach, describe, expect, expectTypeOf, it, vi } from "vitest";
import {
  ApiV2NetworkError,
  ApiV2ProblemError,
  ApiV2SessionEndedError,
  ApiV2UnexpectedResponseError,
} from "./ApiV2ProblemError";
import { apiV2BlobFetch, apiV2Fetch, apiV2HeadersFetch } from "./apiV2Fetch";
import { ApiV2IdentityChangedError, setApiV2TokenProvider } from "./tokenProvider";

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
  it("wraps a body that fails its schema as invalidBody", async () => {
    fetchMock.mockResolvedValue(Response.json({ id: "bad" }));
    const error = await apiV2Fetch("/resource", options()).catch((caught: unknown) => caught);
    expect(error).toBeInstanceOf(ApiV2UnexpectedResponseError);
    expect(error).toMatchObject({ reason: "invalidBody", status: 200, media: "application/json" });
    expect(error).toHaveProperty("cause", expect.any(v.ValiError));
  });
  it("rejects an undeclared status or media type", async () => {
    fetchMock.mockResolvedValue(new Response("<html></html>", { headers: { "Content-Type": "text/html" } }));
    await expect(apiV2Fetch("/resource", options())).rejects.toMatchObject({
      name: "ApiV2UnexpectedResponseError",
      reason: "undeclared",
      status: 200,
      media: "text/html",
    });
  });
  it("wraps malformed success JSON", async () => {
    fetchMock.mockResolvedValue(new Response("{", { headers: { "Content-Type": "application/json" } }));
    const error = await apiV2Fetch("/resource", options()).catch((caught: unknown) => caught);
    expect(error).toMatchObject({ name: "ApiV2UnexpectedResponseError", reason: "malformedJson" });
    expect(error).toHaveProperty("cause", expect.any(SyntaxError));
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
  it("sets problem only for valid problems, preserves extensions and prefers the detail", async () => {
    const body = { status: 409, title: "Conflict", code: "conflict", conflict: { id: 9 } };
    fetchMock.mockResolvedValueOnce(Response.json(body, { status: 409 }));
    await expect(apiV2Fetch("/resource", options())).rejects.toMatchObject({ problem: body, message: "Conflict" });
    fetchMock.mockResolvedValueOnce(Response.json({ ...body, detail: "Conflict with 9" }, { status: 409 }));
    await expect(apiV2Fetch("/resource", options())).rejects.toMatchObject({ message: "Conflict with 9" });
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
  it("propagates an abort before sending", async () => {
    const controller = new AbortController();
    controller.abort();
    await expect(apiV2Fetch("/resource", options("read", { signal: controller.signal }))).rejects.toMatchObject({
      name: "AbortError",
    });
    expect(fetchMock).not.toHaveBeenCalled();
  });
  it("wraps network failures in fetch and while reading the body", async () => {
    const error = new TypeError("network");
    fetchMock.mockRejectedValueOnce(error);
    await expect(apiV2Fetch("/resource", options())).rejects.toMatchObject({ name: "ApiV2NetworkError", cause: error });
    const broken = Response.json({ id: 1 });
    vi.spyOn(broken, "json").mockRejectedValue(error);
    fetchMock.mockResolvedValueOnce(broken);
    await expect(apiV2Fetch("/resource", options())).rejects.toBeInstanceOf(ApiV2NetworkError);
    const brokenBlob = new Response("bytes");
    vi.spyOn(brokenBlob, "blob").mockRejectedValue(error);
    fetchMock.mockResolvedValueOnce(brokenBlob);
    await expect(apiV2BlobFetch("/resource", options())).rejects.toBeInstanceOf(ApiV2NetworkError);
  });
  it("keeps a custom abort reason from fetch", async () => {
    const controller = new AbortController();
    const reason = { cancelled: true };
    fetchMock.mockImplementation(async () => {
      controller.abort(reason);
      throw reason;
    });
    await expect(apiV2Fetch("/resource", options("read", { signal: controller.signal }))).rejects.toBe(reason);
  });
  it("keeps a custom abort reason during a retried GET", async () => {
    const controller = new AbortController();
    const reason = { cancelled: true };
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 401 }));
    recover.mockImplementation(async () => {
      controller.abort(reason);
      return "new";
    });
    await expect(apiV2Fetch("/resource", options("read", { signal: controller.signal }))).rejects.toBe(reason);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
  it.each([
    ["a response body", () => Response.json({ id: 1 })],
    ["an HTTP-error body", () => Response.json({ status: 500 }, { status: 500 })],
  ])("keeps a custom abort reason while reading %s", async (_, response) => {
    const controller = new AbortController();
    const reason = { cancelled: true };
    const pending = response();
    // A body read rejects with the signal's reason once the request is aborted.
    vi.spyOn(pending, "json").mockImplementation(async () => {
      controller.abort(reason);
      throw reason;
    });
    fetchMock.mockResolvedValue(pending);
    await expect(apiV2Fetch("/resource", options("read", { signal: controller.signal }))).rejects.toBe(reason);
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
});

it.each([
  ["an identity change", new ApiV2IdentityChangedError()],
  ["an ended session", new ApiV2SessionEndedError(401, undefined)],
])("rethrows %s from recovery without replaying", async (_, failure) => {
  recover.mockRejectedValue(failure);
  fetchMock.mockResolvedValue(new Response(null, { status: 401 }));
  await expect(apiV2Fetch("/resource", options())).rejects.toBe(failure);
  expect(fetchMock).toHaveBeenCalledTimes(1);
});

it("keeps the original 401 when recovery fails otherwise", async () => {
  recover.mockRejectedValue(new ApiV2NetworkError(new TypeError("offline")));
  fetchMock.mockResolvedValue(new Response(null, { status: 401 }));
  await expect(apiV2Fetch("/resource", options())).rejects.toMatchObject({ name: "ApiV2ProblemError", status: 401 });
});

const unauthenticatedOperations = ["publicRead", "sessionRead"];

it.each(unauthenticatedOperations)("%s ignores mint failures and removes Authorization", async (operationId) => {
  getToken.mockRejectedValue(new Error("Logged out: token mint failed"));
  fetchMock.mockResolvedValue(Response.json({ id: 1 }));
  await apiV2Fetch("/public", options(operationId, { headers: { Authorization: "Bearer stale" } }));
  expect(getToken).not.toHaveBeenCalled();
  expect(recover).not.toHaveBeenCalled();
  expect(fetchMock).toHaveBeenCalledTimes(1);
  const headers = new Headers(fetchMock.mock.calls[0][1]?.headers);
  expect(headers.has("Authorization")).toBe(false);
  expect(headers.get("X-Requested-With")).toBe("XMLHttpRequest");
});

it.each(unauthenticatedOperations)("%s 401 remains an HTTP error without recovery", async (operationId) => {
  fetchMock.mockResolvedValue(new Response(null, { status: 401 }));
  await expect(apiV2Fetch("/public", options(operationId))).rejects.toBeInstanceOf(ApiV2ProblemError);
  expect(getToken).not.toHaveBeenCalled();
  expect(recover).not.toHaveBeenCalled();
  expect(fetchMock).toHaveBeenCalledTimes(1);
});

it("protected operations still require a token", async () => {
  const error = new ApiV2SessionEndedError(401, undefined);
  getToken.mockRejectedValue(error);
  await expect(apiV2Fetch("/resource", options())).rejects.toBe(error);
  expect(getToken).toHaveBeenCalledTimes(1);
  expect(fetchMock).not.toHaveBeenCalled();
});
