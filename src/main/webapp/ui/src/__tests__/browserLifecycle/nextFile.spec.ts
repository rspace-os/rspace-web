import { HttpResponse, http } from "msw";
import { expect, test } from "vitest";
import { worker } from "@/__tests__/browserSetup";
import { OAUTH_TOKEN } from "@/__tests__/mocks/oauthTokenMocks";

test("intercepts requests with the next file's own handler", { retry: 0 }, async () => {
  worker.use(http.get("/userform/ajax/inventoryOauthToken", () => HttpResponse.json({ data: "next-file" })));

  const response = await fetch("/userform/ajax/inventoryOauthToken");
  expect(response.status).toBe(200);
  expect(await response.json()).toEqual({ data: "next-file" });
});

test("restores default handlers after the previous test's override", { retry: 0 }, async () => {
  const response = await fetch("/userform/ajax/inventoryOauthToken");
  expect(response.status).toBe(200);
  expect(await response.json()).toEqual({ data: OAUTH_TOKEN });
});
