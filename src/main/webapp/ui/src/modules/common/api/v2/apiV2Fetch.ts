import * as v from "valibot";
import i18n from "@/modules/common/i18n";
import {
  type ApiV2Error,
  ApiV2ProblemError,
  ApiV2SessionEndedError,
  ApiV2UnexpectedResponseError,
  httpStatusMessage,
  readJsonBody,
  responseMedia,
  transportFailure,
} from "./ApiV2ProblemError";
import { apiV2Operations } from "./generated/valibot/operations";
import { ApiV2ProblemSchema } from "./generated/valibot/schemas";
import { ApiV2IdentityChangedError, getApiV2TokenProvider } from "./tokenProvider";

/**
 * What a generated call rejects with: an `ApiV2Error` (an HTTP failure, an unusable response, a network failure or an
 * ended session), a blocked identity change, or an abort. A request cancelled with a custom `signal.reason` rejects
 * with that reason, which can be any value.
 */
export type ErrorType<_E> = ApiV2Error | ApiV2IdentityChangedError | DOMException;
export type BodyType<B> = B;

function invalidRequest(issues: readonly v.BaseIssue<unknown>[]): ApiV2ProblemError {
  const message = i18n.t("common:apiErrors.apiV2.invalidRequest");
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

async function httpError(response: Response, signal?: AbortSignal | null): Promise<ApiV2ProblemError> {
  let body: unknown;
  if (response.headers.get("Content-Type")?.includes("json")) {
    try {
      body = await response.json();
    } catch (error) {
      // Malformed JSON still represents an HTTP failure; a cancelled read does not.
      if (signal?.aborted) throw error;
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
  return new ApiV2ProblemError(response.status, undefined, httpStatusMessage(response.status));
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
          message: i18n.t("common:apiErrors.apiV2.invalidJson"),
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
  const send = async () => {
    try {
      return await fetch(url, { ...request, headers });
    } catch (error) {
      throw transportFailure(error, init.signal);
    }
  };
  let response = await send();
  if (response.status === 401 && token && provider) {
    let recovered: string | undefined;
    try {
      recovered = await provider.recover(token);
    } catch (error) {
      // An ended session or a changed identity outranks the rejected request; other failures keep its HTTP error.
      if (error instanceof ApiV2SessionEndedError || error instanceof ApiV2IdentityChangedError) throw error;
    }
    init.signal?.throwIfAborted();
    if (recovered && recovered !== token && (init.method ?? "GET").toUpperCase() === "GET") {
      headers.set("Authorization", `Bearer ${recovered}`);
      response = await send();
    }
  }
  if (!response.ok) throw await httpError(response, init.signal);
  return { response, operation };
}

async function apiV2Json(url: string, init?: RequestInit): Promise<{ body: unknown; headers: Headers }> {
  const { response, operation } = await apiV2Response(url, init);
  if (response.status === 204) return { body: undefined, headers: response.headers };
  const media = responseMedia(response);
  const schema = operation.responses[String(response.status)]?.[media];
  if (!schema) throw new ApiV2UnexpectedResponseError("undeclared", { status: response.status, media });
  const body = await readJsonBody(response, init?.signal);
  try {
    v.parse(schema, body);
  } catch (error) {
    if (!v.isValiError(error)) throw error;
    throw new ApiV2UnexpectedResponseError("invalidBody", { status: response.status, media, cause: error });
  }
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
  try {
    return { body: await response.blob(), headers: response.headers };
  } catch (error) {
    throw transportFailure(error, init?.signal);
  }
}
