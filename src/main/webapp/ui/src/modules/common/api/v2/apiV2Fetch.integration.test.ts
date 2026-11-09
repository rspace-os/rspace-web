import { HttpResponse, http } from "msw";
import { ValiError } from "valibot";
import { afterEach, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import { apiV2Fetch } from "./apiV2Fetch";
import { apiV2Operations } from "./generated/valibot/operations";

afterEach(() => vi.restoreAllMocks());

it("validates a registered JSON response and returns its original extension members", async () => {
  const body = { id: 1, extension: { values: [1, 2] } };
  server.use(http.get("/api/v2/users/1", () => HttpResponse.json(body)));

  expect(await apiV2Fetch("/api/v2/users/1", { operationId: "getUser" })).toEqual(body);
});

it("rejects a malformed response with the generated operation schema", async () => {
  server.use(http.get("/api/v2/users/1", () => HttpResponse.json({ id: "invalid" })));

  await expect(apiV2Fetch("/api/v2/users/1", { operationId: "getUser" })).rejects.toBeInstanceOf(ValiError);
});

const requestOperation = Object.entries(apiV2Operations).find(([, operation]) => operation.request);

it.skipIf(!requestOperation)("validates a JSON body with the generated request schema before sending", async () => {
  if (!requestOperation) throw new Error("No JSON request operation is registered");
  const fetch = vi.spyOn(globalThis, "fetch");

  await expect(
    apiV2Fetch("/api/v2/request-validation", {
      operationId: requestOperation[0],
      method: "POST",
      body: JSON.stringify(42),
    }),
  ).rejects.toMatchObject({
    status: 400,
    code: "errors.api.v2.invalidRequest",
    problem: { invalidParams: expect.arrayContaining([{ name: "body", reason: expect.any(String) }]) },
  });
  expect(fetch).not.toHaveBeenCalled();
});

it("keeps problem extensions after validation with the generated problem schema", async () => {
  const problem = { status: 409, title: "Conflict", code: "conflict", conflict: { id: 9 } };
  server.use(http.get("/api/v2/users/1", () => HttpResponse.json(problem, { status: 409 })));

  await expect(apiV2Fetch("/api/v2/users/1", { operationId: "getUser" })).rejects.toMatchObject({ problem });
});
