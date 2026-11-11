import * as v from "valibot";
import { ApiV2ProblemError, ApiV2UnexpectedResponseError } from "./ApiV2ProblemError";
import { apiV2Operations } from "./generated/valibot/operations";
import { ApiV2ProblemSchema } from "./generated/valibot/schemas";
import { type ApiV2IdentityChangedError, getApiV2TokenProvider } from "./tokenProvider";

/**
 * What a generated call rejects with: an HTTP failure, a response the registry does not declare or whose body fails
 * its schema, a blocked identity change, an abort, or a network failure.
 */
export type ErrorType<_E> =
  | ApiV2ProblemError
  | ApiV2UnexpectedResponseError
  | v.ValiError<v.GenericSchema>
  | ApiV2IdentityChangedError
  | DOMException
  | TypeError;
export type BodyType<B> = B;

function invalidRequest(issues: readonly v.BaseIssue<unknown>[]): ApiV2ProblemError {
  const message = "Invalid API request";
  return new ApiV2ProblemError(400, "errors.api.v2.invalidRequest", message, {
    status: 400,
    title: message,
    code: "errors.api.v2.invalidRequest",
    invalidParams: issues.map((issue) => ({
      name: issue.path?.map((part) => String(part.key)).join(".") ?? "body",
      reason: issue.message,
    })),
  });
}

async function httpError(response: Response): Promise<ApiV2ProblemError> {
  let body: unknown;
  if (response.headers.get("Content-Type")?.includes("json")) {
    try {
      body = await response.json();
    } catch {
      /* Malformed JSON still represents an HTTP failure. */
    }
  }
  const parsed = v.safeParse(ApiV2ProblemSchema, body);
  if (parsed.success) {
    return new ApiV2ProblemError(
      response.status,
      parsed.output.code,
      parsed.output.detail ?? parsed.output.title ?? response.statusText,
      body as typeof parsed.output,
    );
  }
  return new ApiV2ProblemError(response.status, undefined, response.statusText || `HTTP ${response.status}`);
}

/** Shared transport for the JSON, response-header and binary generated clients. */
async function apiV2Response(
  url: string,
  init: RequestInit = {},
): Promise<{ response: Response; operation: (typeof apiV2Operations)[string] }> {
  const operationId = "operationId" in init ? init.operationId : undefined;
  if (typeof operationId !== "string" || !Object.hasOwn(apiV2Operations, operationId)) {
    throw new TypeError("Unregistered API v2 operation");
  }
  const operation = apiV2Operations[operationId];
  init.signal?.throwIfAborted();
  if (operation.request) {
    let body: unknown;
    try {
      body = typeof init.body === "string" ? JSON.parse(init.body) : init.body;
    } catch {
      throw invalidRequest([
        {
          kind: "schema",
          type: "json",
          input: init.body,
          expected: "JSON",
          received: "invalid JSON",
          message: "Invalid JSON body",
        },
      ]);
    }
    const result = v.safeParse(operation.request, body);
    if (!result.success) throw invalidRequest(result.issues);
  }
  const headers = new Headers(init.headers);
  headers.set("X-Requested-With", "XMLHttpRequest");
  const publicOperation = "public" in operation && operation.public === true;
  const sessionOperation = "session" in operation && operation.session === true;
  const provider = publicOperation || sessionOperation ? undefined : getApiV2TokenProvider();
  const token = await provider?.getToken(init.signal ?? undefined);
  init.signal?.throwIfAborted();
  if (token) headers.set("Authorization", `Bearer ${token}`);
  else headers.delete("Authorization");
  // operationId belongs to generated clients and is never forwarded to fetch.
  const { operationId: _operationId, ...request } = { ...init, operationId };
  let response = await fetch(url, { ...request, headers });
  if (response.status === 401 && token && provider) {
    let recovered: string | undefined;
    try {
      recovered = await provider.recover(token);
    } catch {
      /* Preserve the rejected request's HTTP error. */
    }
    init.signal?.throwIfAborted();
    if (recovered && recovered !== token && (init.method ?? "GET").toUpperCase() === "GET") {
      headers.set("Authorization", `Bearer ${recovered}`);
      response = await fetch(url, { ...request, headers });
    }
  }
  if (!response.ok) throw await httpError(response);
  return { response, operation };
}

async function apiV2Json(url: string, init?: RequestInit): Promise<{ body: unknown; headers: Headers }> {
  const { response, operation } = await apiV2Response(url, init);
  if (response.status === 204) return { body: undefined, headers: response.headers };
  const media = response.headers.get("Content-Type")?.split(";")[0].trim().toLowerCase() ?? "";
  const schema = operation.responses[String(response.status)]?.[media];
  if (!schema) throw new ApiV2UnexpectedResponseError(response.status, media);
  const body: unknown = await response.json();
  v.parse(schema, body);
  return { body, headers: response.headers };
}

export function apiV2Fetch<T>(url: string, init: RequestInit & { operationId: string }): Promise<T>;
export function apiV2Fetch<T>(url: string, init?: RequestInit): Promise<T>;
export async function apiV2Fetch<T>(url: string, init?: RequestInit): Promise<T> {
  return (await apiV2Json(url, init)).body as T;
}

// Each mutator keeps the overload pair: Orval types generated `options` as the last overload's parameter, so callers
// pass plain RequestInit while the generated call adds its operationId.

/** Mutator for operations whose response headers matter (ETag, Location). */
export function apiV2HeadersFetch<T>(
  url: string,
  init: RequestInit & { operationId: string },
): Promise<{ body: T; headers: Headers }>;
export function apiV2HeadersFetch<T>(url: string, init?: RequestInit): Promise<{ body: T; headers: Headers }>;
export async function apiV2HeadersFetch<T>(url: string, init?: RequestInit): Promise<{ body: T; headers: Headers }> {
  const result = await apiV2Json(url, init);
  return { body: result.body as T, headers: result.headers };
}

// Orval's inferred mutators are called with the response type argument; a binary body ignores it.
/** Mutator for binary downloads. */
export function apiV2BlobFetch<_T = unknown>(
  url: string,
  init: RequestInit & { operationId: string },
): Promise<{ body: Blob; headers: Headers }>;
export function apiV2BlobFetch<_T = unknown>(
  url: string,
  init?: RequestInit,
): Promise<{ body: Blob; headers: Headers }>;
export async function apiV2BlobFetch<_T = unknown>(
  url: string,
  init?: RequestInit,
): Promise<{ body: Blob; headers: Headers }> {
  const { response } = await apiV2Response(url, init);
  return { body: await response.blob(), headers: response.headers };
}
