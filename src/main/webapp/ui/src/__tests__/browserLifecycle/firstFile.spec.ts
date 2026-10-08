import { HttpResponse, http } from "msw";
import { expect, test } from "vitest";
import { worker } from "@/__tests__/browserSetup";

test("intercepts requests in the first test iframe", { retry: 0 }, async () => {
  worker.use(http.get("/userform/ajax/inventoryOauthToken", () => HttpResponse.json({ data: "first-file" })));

  const response = await fetch("/userform/ajax/inventoryOauthToken");
  expect(response.status).toBe(200);
  expect(await response.json()).toEqual({ data: "first-file" });
});
