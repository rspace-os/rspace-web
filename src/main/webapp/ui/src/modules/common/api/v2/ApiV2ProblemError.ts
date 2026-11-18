import type { ApiV2Problem } from "./generated/models/apiV2Problem";

/** Base class of every failure the API v2 transport and token provider normalise. */
export class ApiV2Error extends Error {
  readonly status?: number;

  constructor(message: string, options: { status?: number; cause?: unknown } = {}) {
    super(message, "cause" in options ? { cause: options.cause } : undefined);
    this.name = "ApiV2Error";
    this.status = options.status;
  }
}

export class ApiV2ProblemError extends ApiV2Error {
  declare readonly status: number;

  constructor(
    status: number,
    readonly code: string | undefined,
    message: string,
    readonly problem?: ApiV2Problem,
  ) {
    super(message, { status });
    this.name = "ApiV2ProblemError";
  }
}

/**
 * Why a response could not be used:
 * - `undeclared`: the registry does not declare its status and media type;
 * - `invalidBody`: the body fails its schema (the Valibot error is the `cause`);
 * - `malformedJson`: the body is not JSON (the `SyntaxError` is the `cause`).
 */
export type ApiV2UnexpectedResponseReason = "undeclared" | "invalidBody" | "malformedJson";

const UNEXPECTED_RESPONSE_MESSAGES: Record<ApiV2UnexpectedResponseReason, string> = {
  undeclared: "Unregistered API v2 response",
  invalidBody: "Invalid API v2 response body",
  malformedJson: "Malformed API v2 response JSON",
};

export class ApiV2UnexpectedResponseError extends ApiV2Error {
  readonly media?: string;

  constructor(
    readonly reason: ApiV2UnexpectedResponseReason,
    details: { status?: number; media?: string; cause?: unknown; message?: string } = {},
  ) {
    const { message, media, ...options } = details;
    super(message ?? [UNEXPECTED_RESPONSE_MESSAGES[reason], options.status, media].filter(Boolean).join(" "), options);
    this.name = "ApiV2UnexpectedResponseError";
    this.media = media;
  }
}

/** `fetch()` or reading a body failed while the request was not cancelled. */
export class ApiV2NetworkError extends ApiV2Error {
  constructor(cause: TypeError) {
    super("API v2 network failure", { cause });
    this.name = "ApiV2NetworkError";
  }
}

/**
 * The browser session has ended, so no token can be issued. `status` is the token route's 401 or 403, or 200 for the
 * HTML login page. Retrying cannot succeed, whatever the status.
 */
export class ApiV2SessionEndedError extends ApiV2Error {
  constructor(status: number | undefined, cause: unknown) {
    super("API v2 browser session ended", { status, cause });
    this.name = "ApiV2SessionEndedError";
  }
}

/** The media type of a response, lower-cased and without parameters. */
export function responseMedia(response: Response): string {
  return response.headers.get("Content-Type")?.split(";")[0].trim().toLowerCase() ?? "";
}

/**
 * Normalises a failure of `fetch()` or of reading a body. Cancellation passes through unchanged, whatever the signal's
 * reason, so TanStack Query still recognises it.
 */
export function transportFailure(error: unknown, signal?: AbortSignal | null): unknown {
  if (signal?.aborted || !(error instanceof TypeError)) return error;
  return new ApiV2NetworkError(error);
}

/** Reads a JSON body, wrapping a `SyntaxError` as `malformedJson` and a read failure as a network error. */
export async function readJsonBody(response: Response, signal?: AbortSignal | null): Promise<unknown> {
  try {
    return await response.json();
  } catch (error) {
    if (!signal?.aborted && error instanceof SyntaxError) {
      throw new ApiV2UnexpectedResponseError("malformedJson", {
        status: response.status,
        media: responseMedia(response),
        cause: error,
      });
    }
    throw transportFailure(error, signal);
  }
}
