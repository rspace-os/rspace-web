import { HttpResponse, http } from "msw";
import { expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { apiV2Fetch } from "./apiV2Fetch";

it("validates a registered JSON response and returns its original extension members", async () => {
  const body = { id: 1, extension: { values: [1, 2] } };
  server.use(http.get("/api/v2/users/1", () => HttpResponse.json(body)));

  expect(await apiV2Fetch("/api/v2/users/1", { operationId: "getUser" })).toEqual(body);
});

it("keeps problem extensions after validation with the generated problem schema", async () => {
  const problem = { status: 409, title: "Conflict", code: "conflict", conflict: { id: 9 } };
  server.use(http.get("/api/v2/users/1", () => HttpResponse.json(problem, { status: 409 })));

  await expect(apiV2Fetch("/api/v2/users/1", { operationId: "getUser" })).rejects.toMatchObject({ problem });
});
