// TanStack Query's default retry count, kept for failures a retry can fix.
const MAXIMUM_RETRIES = 3;

/** The HTTP status an API request failed with, when the error records it (e.g. `ApiV2ProblemError`). */
export function httpStatusOf(error: unknown): number | undefined {
  return typeof error === "object" && error !== null && "status" in error && typeof error.status === "number"
    ? error.status
    : undefined;
}

/** True for a 4xx answer that repeating the same request cannot change, such as 403 or 404. */
export function isPermanentClientError(error: unknown): boolean {
  const status = httpStatusOf(error);
  return status !== undefined && status >= 400 && status < 500 && status !== 408 && status !== 429;
}

/** Retries network failures and server errors, but answers a permanent client error immediately. */
export function retryUnlessClientError(failureCount: number, error: unknown): boolean {
  return failureCount < MAXIMUM_RETRIES && !isPermanentClientError(error);
}
