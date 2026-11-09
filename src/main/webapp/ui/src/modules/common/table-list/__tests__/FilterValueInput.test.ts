import { expect, it } from "vitest";
import type { ResolvedFieldConfig } from "@/modules/common/collection/config";
import { valueControlFor } from "../components/FilterValueInput";

type Row = { value: unknown };
const capabilities = { sortable: false, filterOperators: [], supportsWildcards: false };

it("gives a relationship field with no registered source a text input", () => {
  const field: ResolvedFieldConfig<Row> = {
    name: "value",
    derivedLabel: { text: "Owner" },
    type: "relationship",
    relationTo: "unregistered",
    hasMany: false,
    capabilities,
  };
  expect(valueControlFor(field, "equals", undefined)).toEqual({ kind: "input", type: "text", step: undefined });
});

it("keeps configured-only multiple selection for a select field without options", () => {
  const field: ResolvedFieldConfig<Row> = {
    name: "value",
    derivedLabel: { text: "State" },
    type: "select",
    options: [],
    capabilities,
  };
  expect(valueControlFor(field, "in", undefined)).toEqual({ kind: "multiple", options: [] });
});
