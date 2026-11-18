import { QueryClient } from "@tanstack/react-query";
import { expect, test, vi } from "vitest";
import { ApiV2SessionEndedError, ApiV2UnexpectedResponseError } from "../../api/v2/ApiV2ProblemError";
import { SessionTokenProvider } from "../../api/v2/tokenProvider";
import { retryUnlessClientError } from "../queryRetry";

// Test setup imports this runtime before test mocks are installed.
const { mint } = vi.hoisted(() => {
  vi.resetModules();
  return { mint: vi.fn() };
});
vi.mock("../../api/v2/generated/oauth/oauth", () => ({ createUiOAuthToken: mint }));

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

test("does not retry an ended session answered by the HTML login page", async () => {
  mint.mockRejectedValue(new ApiV2UnexpectedResponseError("undeclared", { status: 200, media: "text/html" }));
  const onSessionEnded = vi.fn();
  const provider = new SessionTokenProvider({ onSessionEnded, onIdentityChanged: vi.fn() });
  const client = new QueryClient({ defaultOptions: { queries: { retry: retryUnlessClientError, retryDelay: 0 } } });

  await expect(
    client.fetchQuery({ queryKey: ["session"], queryFn: ({ signal }) => provider.getToken(signal) }),
  ).rejects.toBeInstanceOf(ApiV2SessionEndedError);
  expect(mint).toHaveBeenCalledOnce();
  expect(onSessionEnded).toHaveBeenCalledOnce();
});
