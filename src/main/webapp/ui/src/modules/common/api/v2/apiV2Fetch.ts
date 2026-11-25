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
import {
  ApiV2IdentityChangedError,
  type ApiV2TokenProvider,
  findApiV2TokenProvider,
  getApiV2TokenProvider,
} from "./tokenProvider";

/**
 * What a generated call rejects with: an `ApiV2Error` (an HTTP failure, an unusable response, a network failure or an
 * ended session), a blocked identity change, or an abort. A request cancelled with a custom `signal.reason` rejects
 * with that reason, which can be any value. A malformed `where` fails with a `TypeError` while the URL is built; that
 * is a programming error and not part of `ErrorType`.
 */
export type ErrorType<_E> = ApiV2Error | ApiV2IdentityChangedError | DOMException;
export type BodyType<B> = B;

/** The `invalidParams` name of a request validation issue: its dotted path, or `body` for the body as a whole. */
function invalidParamName(issue: v.BaseIssue<unknown>): string {
  return issue.path?.map((part) => String(part.key)).join(".") ?? "body";
}

/** The problem a request whose body or parameters fail their generated schema rejects with, before it is sent. */
function invalidRequestError(issues: readonly v.BaseIssue<unknown>[]): ApiV2ProblemError {
  const message = i18n.t("common:apiErrors.apiV2.invalidRequest");
  return new ApiV2ProblemError(400, "errors.api.v2.invalidRequest", message, {
    status: 400,
    title: message,
    code: "errors.api.v2.invalidRequest",
    invalidParams: issues.map((issue) => ({
      name: invalidParamName(issue),
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

/**
 * A public operation's token: the signed-in caller keeps their view, while a page without a provider, or one that
 * cannot get a token, reads anonymously. Only an abort or an identity change stops the request.
 */
async function optionalToken(provider: ApiV2TokenProvider | undefined, signal?: AbortSignal | null) {
  if (!provider) return undefined;
  try {
    return await provider.getToken(signal ?? undefined, { optional: true });
  } catch (error) {
    if (signal?.aborted || error instanceof ApiV2IdentityChangedError) throw error;
    return undefined;
  }
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
      throw invalidRequestError([
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
    if (!result.success) throw invalidRequestError(result.issues);
  }
  const headers = new Headers(init.headers);
  headers.set("X-Requested-With", "XMLHttpRequest");
  const publicOperation = "public" in operation && operation.public === true;
  const sessionOperation = "session" in operation && operation.session === true;
  let provider: ApiV2TokenProvider | undefined;
  let token: string | undefined;
  if (publicOperation) {
    provider = findApiV2TokenProvider();
    token = await optionalToken(provider, init.signal);
  } else if (!sessionOperation) {
    provider = getApiV2TokenProvider();
    token = await provider.getToken(init.signal ?? undefined);
  }
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
      recovered = await provider.recover(token, { optional: publicOperation });
    } catch (error) {
      // A changed identity outranks the rejected request, and so does an ended session unless the read is public;
      // other failures keep its HTTP error.
      if (error instanceof ApiV2IdentityChangedError) throw error;
      if (error instanceof ApiV2SessionEndedError && !publicOperation) throw error;
    }
    init.signal?.throwIfAborted();
    const replayable = (init.method ?? "GET").toUpperCase() === "GET";
    if (recovered && recovered !== token && replayable) {
      headers.set("Authorization", `Bearer ${recovered}`);
      response = await send();
    } else if (publicOperation && replayable) {
      // Without a replacement token a public read still has an anonymous answer.
      headers.delete("Authorization");
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
