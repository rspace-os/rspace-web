import { HttpResponse, http } from "msw";
import { expect, test } from "vitest";
import { server } from "@/__tests__/mswServer";
import { relationshipSources } from "../sources";

test("instrument search rejects an unsuccessful response", async () => {
  server.use(http.get("/api/v2/instruments", () => new HttpResponse(null, { status: 503 })));

  await expect(
    relationshipSources.instruments.search("microscope", "token", new AbortController().signal),
  ).rejects.toThrow("Relationship option request failed with status 503");
});

test.each([
  ["null", null],
  ["malformed envelope", { docs: "invalid" }],
])("instrument search returns no results for a %s body", async (_, body) => {
  server.use(http.get("/api/v2/instruments", () => HttpResponse.json(body)));

  await expect(
    relationshipSources.instruments.search("microscope", "token", new AbortController().signal),
  ).resolves.toEqual([]);
});
