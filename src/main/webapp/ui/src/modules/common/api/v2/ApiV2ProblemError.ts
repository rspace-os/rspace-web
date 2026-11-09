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
