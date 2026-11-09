import { describe, expect, it } from "vitest";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveConfig";
import { parseColumns, parseSorting, serializeColumns, serializeFilters, serializeSorting } from "../utils/queryString";

type TestRecord = { id: string; title: string; owner: string; modifiedAt: string };
const config = resolveCollectionConfig<TestRecord>({
  slug: "records",
  idField: "id",
  useAsTitle: "title",
  labels: { singularKey: "tableListExamples:record", pluralKey: "tableListExamples:records" },
  defaultColumns: ["title"],
  fields: [
    { name: "id", type: "text", labelKey: "tableListExamples:fields.id", list: false },
    { name: "title", type: "text", labelKey: "tableListExamples:fields.title" },
    { name: "owner", type: "text", labelKey: "tableListExamples:fields.owner" },
    { name: "modifiedAt", type: "dateTime", labelKey: "tableListExamples:fields.modified" },
  ],
});

describe("table-list query string state", () => {
  it("round-trips only unique, listable columns", () => {
    const columns = ["owner", "title"] as const;
    expect(JSON.parse(serializeColumns<TestRecord>(columns))).toEqual({ fields: columns });
    expect(parseColumns(serializeColumns<TestRecord>(columns), config)).toEqual(columns);
    expect(parseColumns(JSON.stringify({ fields: ["id"] }), config)).toBeNull();
    expect(parseColumns(JSON.stringify({ fields: ["title", "title"] }), config)).toBeNull();
  });

  const nonSortableConfig = {
    ...config,
    fields: config.fields.map((field) =>
      field.name === "owner" ? { ...field, capabilities: { ...field.capabilities, sortable: false } } : field,
    ),
  };
  it.each<[string, string, typeof config, ReturnType<typeof parseSorting<TestRecord>>]>([
    [
      "round-trips ordered sorting",
      "owner,-modifiedAt",
      config,
      [
        { field: "owner", direction: "asc" },
        { field: "modifiedAt", direction: "desc" },
      ],
    ],
    ["treats an empty value as no sorting", "", config, []],
    ["rejects an empty leading sort field", ",title", config, null],
    ["rejects an empty trailing sort field", "title,", config, null],
    ["rejects a duplicate sort field", "title,-title", config, null],
    ["rejects an unknown sort field", "unknown", config, null],
    ["rejects a non-sortable sort field", "owner", nonSortableConfig, null],
  ])("%s: %j", (_label, value, sortConfig, expected) => {
    expect(parseSorting(value, sortConfig)).toEqual(expected);
    if (expected?.length) expect(serializeSorting(expected)).toBe(value);
  });

  it("fingerprints filters by content, keeping dates distinct from their ISO strings", () => {
    const at = "2026-11-23T00:00:00.000Z";
    const comparison = { kind: "comparison", field: "modifiedAt", operator: "equals", value: at } as const;
    const reordered = { value: at, operator: "equals", field: "modifiedAt", kind: "comparison" } as const;

    expect(serializeFilters<TestRecord>({ search: "", expression: comparison })).toBe(
      serializeFilters<TestRecord>({ search: "", expression: reordered }),
    );
    expect(serializeFilters<TestRecord>({ search: "", expression: { ...comparison, value: new Date(at) } })).not.toBe(
      serializeFilters<TestRecord>({ search: "", expression: comparison }),
    );
  });
});
