import type { ApiV2Problem } from "./generated/models/apiV2Problem";

export class ApiV2ProblemError extends Error {
  constructor(
    readonly status: number,
    readonly code: string | undefined,
    message: string,
    readonly problem?: ApiV2Problem,
  ) {
    super(message);
    this.name = "ApiV2ProblemError";
  }
}

/** A response the operation's registry does not declare for its status and media type. */
export class ApiV2UnexpectedResponseError extends TypeError {
  constructor(
    readonly status: number,
    readonly media: string,
  ) {
    super(`Unregistered API v2 response ${status} ${media}`);
    this.name = "ApiV2UnexpectedResponseError";
  }
}
