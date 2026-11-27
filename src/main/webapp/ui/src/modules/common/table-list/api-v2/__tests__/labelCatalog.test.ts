import { expect, it } from "vitest";
import openApi from "@/modules/common/api/v2/openapi.generated.json";
import { isRelationshipWireSelector, relationshipLabelKeys } from "../filterFields";

type PublishedFilters = Record<string, { selectors?: Record<string, { picker?: unknown }> }>;

function runtimeVias(node: unknown): string[] {
  if (Array.isArray(node)) return node.flatMap(runtimeVias);
  if (typeof node !== "object" || node === null) return [];
  return Object.entries(node).flatMap(([key, value]) =>
    key === "x-rspace-runtime-fields" && Array.isArray(value)
      ? value.map((namespace: { via?: string }) => namespace.via ?? "").filter((via) => via !== "")
      : runtimeVias(value),
  );
}

// Target fields are checked by `tsc` through `targetFieldLabelKeys`' `satisfies` clause.
it("labels every relationship the OpenAPI document publishes from the catalog", () => {
  const filters: PublishedFilters =
    (openApi.components as { "x-rspace-filters"?: PublishedFilters })["x-rspace-filters"] ?? {};
  const relationships = new Set(runtimeVias(openApi));
  for (const { selectors = {} } of Object.values(filters)) {
    for (const [selector, published] of Object.entries(selectors)) {
      const dot = selector.indexOf(".");
      if (dot < 0) {
        if (published.picker) relationships.add(selector);
      } else if (!isRelationshipWireSelector(selector)) {
        relationships.add(selector.slice(0, dot));
      }
    }
  }

  expect([...relationships].filter((name) => !Object.hasOwn(relationshipLabelKeys, name))).toEqual([]);
});
