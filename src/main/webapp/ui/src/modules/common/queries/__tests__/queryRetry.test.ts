import { expect, test } from "vitest";
import { retryUnlessClientError } from "../queryRetry";

test.each([408, 429, 500, 502, 503, 599, undefined])("retries transient status %s three times", (status) => {
  expect([0, 1, 2, 3, 4].map((count) => retryUnlessClientError(count, { status }))).toEqual([
    true,
    true,
    true,
    false,
    false,
  ]);
});

test.each([400, 401, 403, 404, 409, 422, 499])("does not retry permanent status %s", (status) => {
  expect(retryUnlessClientError(0, { status })).toBe(false);
});
