import { HttpResponse, http } from "msw";
import * as v from "valibot";
import { afterEach, beforeEach, describe, expect, expectTypeOf, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import i18n from "@/modules/common/i18n";
import {
  ApiV2NetworkError,
  ApiV2ProblemError,
  ApiV2SessionEndedError,
  ApiV2UnexpectedResponseError,
} from "./ApiV2ProblemError";
import { apiV2BlobFetch, apiV2Fetch, apiV2HeadersFetch } from "./apiV2Fetch";
import { ApiV2IdentityChangedError, type ApiV2TokenProvider, setApiV2TokenProvider } from "./tokenProvider";

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

const getToken = vi.fn(async () => "old");
const recover = vi.fn<ApiV2TokenProvider["recover"]>(async () => "new");
let restore: () => void;
const options = (operationId = "read", init: RequestInit = {}) => ({ ...init, operationId });
const requests: Request[] = [];
const record = ({ request }: { request: Request }) => {
  requests.push(request);
};
/** Answers `path` with each response in turn, repeating the last. */
const respondAt = (path: string, ...responses: (() => Response | Promise<Response>)[]) =>
  server.use(...responses.map((response, index) => http.all(path, response, { once: index < responses.length - 1 })));
const respond = (...responses: (() => Response | Promise<Response>)[]) => respondAt("/resource", ...responses);
const unauthorized = () => new HttpResponse(null, { status: 401 });
/**
 * Faults below HTTP (a dropped connection mid-body, an abort during fetch or a body read) cannot be expressed as MSW
 * handlers, so these cases replace the next `fetch` call only; later calls still reach MSW.
 */
const nextFetch = (implementation: () => Promise<Response>) =>
  vi.spyOn(globalThis, "fetch").mockImplementationOnce(implementation);
/** A response whose body read fails with `failure()`. */
const failingRead = (response: Response, method: "json" | "blob", failure: () => unknown) => {
  vi.spyOn(response, method).mockImplementation(async () => {
    throw failure();
  });
  return response;
};

beforeEach(() => {
  server.events.on("request:start", record);
  getToken.mockResolvedValue("old");
  recover.mockResolvedValue("new");
  restore = setApiV2TokenProvider({ getToken, recover });
});
afterEach(() => {
  server.events.removeListener("request:start", record);
  requests.length = 0;
  restore();
  vi.resetAllMocks();
});

describe("API v2 transport", () => {
  it("keeps original JSON extension data and sets protected headers", async () => {
    respond(() => HttpResponse.json({ id: 1, extension: { value: 2 } }));
    expect(await apiV2Fetch("/resource", options())).toEqual({ id: 1, extension: { value: 2 } });
    expect(requests[0].headers.get("Authorization")).toBe("Bearer old");
    expect(requests[0].headers.get("X-Requested-With")).toBe("XMLHttpRequest");
  });
  it("rejects unregistered operations before minting or sending", async () => {
    await expect(apiV2Fetch("/resource", options("missing"))).rejects.toThrow("Unregistered");
    expect(getToken).not.toHaveBeenCalled();
    expect(requests).toHaveLength(0);
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
    expect(requests).toHaveLength(0);
  });
  it("wraps a body that fails its schema as invalidBody", async () => {
    respond(() => HttpResponse.json({ id: "bad" }));
    const error = await apiV2Fetch("/resource", options()).catch((caught: unknown) => caught);
    expect(error).toBeInstanceOf(ApiV2UnexpectedResponseError);
    expect(error).toMatchObject({ reason: "invalidBody", status: 200, media: "application/json" });
    expect(error).toHaveProperty("cause", expect.any(v.ValiError));
  });
  it("rejects an undeclared status or media type", async () => {
    respond(() => HttpResponse.html("<html></html>"));
    await expect(apiV2Fetch("/resource", options())).rejects.toMatchObject({
      name: "ApiV2UnexpectedResponseError",
      reason: "undeclared",
      status: 200,
      media: "text/html",
    });
  });
  it("wraps malformed success JSON", async () => {
    respond(() => new HttpResponse("{", { headers: { "Content-Type": "application/json" } }));
    const error = await apiV2Fetch("/resource", options()).catch((caught: unknown) => caught);
    expect(error).toMatchObject({ name: "ApiV2UnexpectedResponseError", reason: "malformedJson" });
    expect(error).toHaveProperty("cause", expect.any(SyntaxError));
  });
  it.each(["GET", "POST"])("recovers a 401 and replays only GET: %s", async (method) => {
    respond(unauthorized, () => HttpResponse.json({ id: 1 }));
    const result = apiV2Fetch("/resource", options("read", { method }));
    if (method === "GET") {
      expect(await result).toEqual({ id: 1 });
      expect(requests.map((request) => request.headers.get("Authorization"))).toEqual(["Bearer old", "Bearer new"]);
    } else {
      await expect(result).rejects.toMatchObject({ status: 401 });
      expect(requests).toHaveLength(1);
    }
    expect(recover).toHaveBeenCalledWith("old", { optional: false });
  });
  it("preserves permission 401 when recovery retains the token", async () => {
    vi.when(recover).calledWith("old", { optional: false }).thenResolve("old");
    respond(unauthorized);
    await expect(apiV2Fetch("/resource", options())).rejects.toMatchObject({ status: 401 });
    expect(requests).toHaveLength(1);
  });
  it("sets problem only for valid problems, preserves extensions and prefers the detail", async () => {
    const body = { status: 409, title: "Conflict", code: "conflict", conflict: { id: 9 } };
    respond(
      () => HttpResponse.json(body, { status: 409 }),
      () => HttpResponse.json({ ...body, detail: "Conflict with 9" }, { status: 409 }),
      () => HttpResponse.json({ status: "bad" }, { status: 400 }),
    );
    await expect(apiV2Fetch("/resource", options())).rejects.toMatchObject({ problem: body, message: "Conflict" });
    await expect(apiV2Fetch("/resource", options())).rejects.toMatchObject({ message: "Conflict with 9" });
    await expect(apiV2Fetch("/resource", options())).rejects.toMatchObject({ status: 400, problem: undefined });
  });
  it("wraps non-JSON and malformed JSON HTTP errors", async () => {
    respond(
      () => new HttpResponse("html", { status: 500 }),
      () => new HttpResponse("{", { status: 500, headers: { "Content-Type": "application/problem+json" } }),
    );
    await i18n.changeLanguage("en-US");
    for (let attempt = 0; attempt < 2; attempt++) {
      const error = await apiV2Fetch("/resource", options()).catch((caught: unknown) => caught);
      expect(error).toBeInstanceOf(ApiV2ProblemError);
      expect(error).toHaveProperty("message", "The request failed with status 500.");
    }
  });
  it("returns undefined for 204", async () => {
    respond(() => new HttpResponse(null, { status: 204 }));
    expect(await apiV2Fetch("/resource", options())).toBeUndefined();
  });
  it("propagates an abort before sending", async () => {
    const controller = new AbortController();
    controller.abort();
    await expect(apiV2Fetch("/resource", options("read", { signal: controller.signal }))).rejects.toMatchObject({
      name: "AbortError",
    });
    expect(requests).toHaveLength(0);
  });
  it("wraps network failures in fetch and while reading the body", async () => {
    respond(() => HttpResponse.error());
    await expect(apiV2Fetch("/resource", options())).rejects.toMatchObject({
      name: "ApiV2NetworkError",
      cause: expect.any(TypeError),
    });
    const dropped = new TypeError("network");
    nextFetch(async () => failingRead(HttpResponse.json({ id: 1 }), "json", () => dropped));
    await expect(apiV2Fetch("/resource", options())).rejects.toMatchObject({
      name: "ApiV2NetworkError",
      cause: dropped,
    });
    nextFetch(async () => failingRead(HttpResponse.text("bytes"), "blob", () => dropped));
    await expect(apiV2BlobFetch("/resource", options())).rejects.toMatchObject({
      name: "ApiV2NetworkError",
      cause: dropped,
    });
  });
  it("keeps a custom abort reason from fetch", async () => {
    const controller = new AbortController();
    const reason = { cancelled: true };
    nextFetch(async () => {
      controller.abort(reason);
      throw reason;
    });
    await expect(apiV2Fetch("/resource", options("read", { signal: controller.signal }))).rejects.toBe(reason);
  });
  it("keeps a custom abort reason during a retried GET", async () => {
    const controller = new AbortController();
    const reason = { cancelled: true };
    respond(unauthorized);
    recover.mockImplementation(async () => {
      controller.abort(reason);
      return "new";
    });
    await expect(apiV2Fetch("/resource", options("read", { signal: controller.signal }))).rejects.toBe(reason);
    expect(requests).toHaveLength(1);
  });
  it.each([200, 500])("keeps a custom abort reason while reading a %s body", async (status) => {
    const controller = new AbortController();
    const reason = { cancelled: true };
    // A body read rejects with the signal's reason once the request is aborted.
    nextFetch(async () =>
      failingRead(HttpResponse.json({ id: 1, status }, { status }), "json", () => {
        controller.abort(reason);
        return reason;
      }),
    );
    await expect(apiV2Fetch("/resource", options("read", { signal: controller.signal }))).rejects.toBe(reason);
  });
  it("retains headers in JSON and Blob mutators", async () => {
    respond(
      () => HttpResponse.json({ id: 1 }, { headers: { ETag: "version" } }),
      () => HttpResponse.text("calendar", { headers: { "Content-Disposition": "attachment" } }),
    );
    const json = await apiV2HeadersFetch("/resource", options());
    expect(json.body).toEqual({ id: 1 });
    expect(json.headers.get("ETag")).toBe("version");
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
  respond(unauthorized);
  await expect(apiV2Fetch("/resource", options())).rejects.toBe(failure);
  expect(requests).toHaveLength(1);
});

it("keeps the original 401 when recovery fails otherwise", async () => {
  recover.mockRejectedValue(new ApiV2NetworkError(new TypeError("offline")));
  respond(unauthorized);
  await expect(apiV2Fetch("/resource", options())).rejects.toMatchObject({ name: "ApiV2ProblemError", status: 401 });
});

it("a session operation ignores the provider and removes Authorization", async () => {
  respondAt("/session", () => HttpResponse.json({ id: 1 }));
  await apiV2Fetch("/session", options("sessionRead", { headers: { Authorization: "Bearer stale" } }));
  expect(getToken).not.toHaveBeenCalled();
  expect(requests).toHaveLength(1);
  expect(requests[0].headers.has("Authorization")).toBe(false);
  expect(requests[0].headers.get("X-Requested-With")).toBe("XMLHttpRequest");
});

it("a session operation's 401 remains an HTTP error without recovery", async () => {
  respondAt("/session", unauthorized);
  await expect(apiV2Fetch("/session", options("sessionRead"))).rejects.toBeInstanceOf(ApiV2ProblemError);
  expect(recover).not.toHaveBeenCalled();
  expect(requests).toHaveLength(1);
});

describe("public operations", () => {
  it("keep the signed-in caller's view by sending their token", async () => {
    respondAt("/public", () => HttpResponse.json({ id: 1 }));
    await apiV2Fetch("/public", options("publicRead"));
    expect(requests[0].headers.get("Authorization")).toBe("Bearer old");
  });

  it.each([
    ["the provider cannot get a token", () => getToken.mockRejectedValue(new ApiV2SessionEndedError(401, undefined))],
    ["the page installed no provider", () => restore()],
  ])("read anonymously when %s", async (_, arrange) => {
    arrange();
    respondAt("/public", () => HttpResponse.json({ id: 1 }));
    expect(await apiV2Fetch("/public", options("publicRead", { headers: { Authorization: "Bearer stale" } }))).toEqual({
      id: 1,
    });
    expect(requests).toHaveLength(1);
    expect(requests[0].headers.has("Authorization")).toBe(false);
  });

  it("stop on an identity change while getting the token", async () => {
    const changed = new ApiV2IdentityChangedError();
    getToken.mockRejectedValue(changed);
    await expect(apiV2Fetch("/public", options("publicRead"))).rejects.toBe(changed);
    expect(requests).toHaveLength(0);
  });

  it("replay a rejected token's read with its replacement", async () => {
    respondAt("/public", unauthorized, () => HttpResponse.json({ id: 1 }));
    await apiV2Fetch("/public", options("publicRead"));
    expect(requests.map((request) => request.headers.get("Authorization"))).toEqual(["Bearer old", "Bearer new"]);
  });

  it("replay anonymously when recovery ends the session", async () => {
    recover.mockRejectedValue(new ApiV2SessionEndedError(401, undefined));
    respondAt("/public", unauthorized, () => HttpResponse.json({ id: 1 }));
    expect(await apiV2Fetch("/public", options("publicRead"))).toEqual({ id: 1 });
    expect(requests.map((request) => request.headers.get("Authorization"))).toEqual(["Bearer old", null]);
  });
});

it("protected operations still require a token", async () => {
  const error = new ApiV2SessionEndedError(401, undefined);
  getToken.mockRejectedValue(error);
  await expect(apiV2Fetch("/resource", options())).rejects.toBe(error);
  expect(getToken).toHaveBeenCalledTimes(1);
  expect(requests).toHaveLength(0);
});
