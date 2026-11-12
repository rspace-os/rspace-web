import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { buildInvalidationRules, type Document, getOperations, queryFile } from "./index";

const document: Document = {
  paths: {
    "/api/v2/bookings": {
      get: { operationId: "listBookings", tags: ["Bookings"] },
      post: { operationId: "createBooking", tags: ["Bookings"] },
    },
    "/api/v2/bookings/count": { get: { operationId: "countBookings", tags: ["Bookings"] } },
    "/api/v2/bookings/{id}": {
      get: { operationId: "getBooking", tags: ["Bookings"] },
      patch: { operationId: "updateBooking", tags: ["Bookings"] },
    },
    "/api/v2/bookings/{id}/access": { get: { operationId: "getBookingAccess", tags: ["Access"] } },
    "/api/v2/bookings/{id}/audit": { get: { operationId: "listBookingAuditEvents", tags: ["Audit"] } },
    "/api/v2/bookings/{id}/access/grantees": { get: { operationId: "searchBookingAccessGrantees", tags: ["Access"] } },
    "/api/v2/booking-settings": { get: { operationId: "listBookingSettings", tags: ["Bookings"] } },
    "/api/v2/booking-configurations": { get: { operationId: "listBookingConfigurations", tags: ["Bookings"] } },
    "/api/v2/catalogue": { get: { operationId: "searchCatalogue", tags: ["AB Widget"] } },
  },
};

describe("resource invalidation", () => {
  it("invalidates list/count and the affected record, access and audit without crossing resource names", () => {
    expect(buildInvalidationRules(document)).toEqual([
      { onMutations: ["createBooking"], invalidates: [{ query: "listBookings" }, { query: "countBookings" }] },
      {
        onMutations: ["updateBooking"],
        invalidates: [
          { query: "listBookings" },
          { query: "countBookings" },
          { query: "getBooking", params: ["id"] },
          { query: "getBookingAccess", params: ["id"], file: "./access/access.ts" },
          { query: "listBookingAuditEvents", params: ["id"], file: "./audit/audit.ts" },
        ],
      },
    ]);
  });
  it("merges cross-resource targets with Orval tag filenames", () => {
    const rules = buildInvalidationRules(document, [
      { onMutations: ["updateBooking"], invalidates: [{ query: "searchCatalogue" }] },
    ]);
    expect(rules[1].invalidates).toContainEqual({ query: "searchCatalogue", file: "./ab-widget/ab-widget.ts" });
    expect(() => buildInvalidationRules(document, [{ onMutations: ["unknown"], invalidates: [] }])).toThrow(
      "Unknown mutation",
    );
    expect(() =>
      buildInvalidationRules(document, [{ onMutations: ["updateBooking"], invalidates: [{ query: "unknown" }] }]),
    ).toThrow("Unknown query");
  });
});

// Run after generation, against either the committed client or a scratch client.
it("covers every mutation and resolves every target to a generated GET and file", async () => {
  const root = resolve(process.env.API_V2_CONFIG_TEST_ROOT ?? ".");
  const spec: Document = JSON.parse(
    readFileSync(resolve(root, "src/modules/common/api/v2/openapi.generated.json"), "utf8"),
  );
  const { crossResourceInvalidation } = await import("../../src/modules/common/api/v2/crossResourceInvalidation");
  const operations = getOperations(spec);
  const rules = buildInvalidationRules(spec, crossResourceInvalidation);
  const mutations = operations.filter((operation) => !["get", "head", "options"].includes(operation.method));
  expect(rules.map((rule) => rule.onMutations[0])).toEqual(mutations.map((operation) => operation.operationId));
  for (const rule of rules) {
    const mutation = mutations.find((operation) => operation.operationId === rule.onMutations[0]);
    expect(mutation).toBeDefined();
    for (const target of rule.invalidates) {
      const query = operations.find(
        (operation) => operation.operationId === target.query && operation.method === "get",
      );
      expect(query).toBeDefined();
      if (!query || !mutation) throw new Error("Unknown operation");
      const file = resolve(root, "src/modules/common/api/v2/generated", target.file ?? queryFile(query));
      expect(existsSync(file), file).toBe(true);
      const source = readFileSync(file, "utf8");
      expect(source).toContain(`export const ${query.operationId} =`);
    }
  }
});
