import { describe, expect, it } from "vitest";
import {
  parseColumns,
  parseFilters,
  parseSorting,
  serializeColumns,
  serializeFilters,
  serializeSorting,
} from "../queryStringState";
import type { FilterState } from "../tableListState";
import { config, type TestRecord } from "./fixtures/tableListFixtures";

describe("table-list query string state", () => {
  it("round-trips nested filters, arrays, and dates", () => {
    const filters: FilterState<TestRecord> = {
      search: "Ada",
      expression: {
        kind: "and",
        children: [
          { kind: "comparison", field: "score", operator: "in", value: [4, 8] },
          {
            kind: "comparison",
            field: "modifiedAt",
            operator: "greaterThan",
            value: new Date("2026-08-01T10:00:00Z"),
          },
        ],
      },
    };

    expect(parseFilters(serializeFilters(filters), config)).toEqual(filters);
  });

  it("rejects unknown fields, unsupported operators, and malformed state", () => {
    expect(
      parseFilters(
        JSON.stringify({
          search: "",
          expression: { kind: "comparison", field: "unknown", operator: "equals", value: "x" },
        }),
        config,
      ),
    ).toBeNull();
    expect(
      parseFilters(
        JSON.stringify({
          search: "",
          expression: { kind: "comparison", field: "enabled", operator: "contains", value: "yes" },
        }),
        config,
      ),
    ).toBeNull();
    expect(parseFilters("not-json", config)).toBeNull();
  });

  it("round-trips only unique, listable columns", () => {
    const columns = ["owner", "title"] as const;
    expect(JSON.parse(serializeColumns<TestRecord>(columns))).toEqual({ fields: columns });
    expect(parseColumns(serializeColumns<TestRecord>(columns), config)).toEqual(columns);
    expect(parseColumns(JSON.stringify(columns), config)).toEqual(columns);
    expect(parseColumns(JSON.stringify(["id"]), config)).toBeNull();
    expect(parseColumns(JSON.stringify(["title", "title"]), config)).toBeNull();
  });

  it("round-trips ordered sorting and treats an empty value as no sorting", () => {
    const sorting = [
      { field: "owner", direction: "asc" },
      { field: "modifiedAt", direction: "desc" },
    ] as const;

    expect(serializeSorting(sorting)).toBe("owner,-modifiedAt");
    expect(parseSorting(serializeSorting(sorting), config)).toEqual(sorting);
    expect(parseSorting("", config)).toEqual([]);
  });

  it("rejects empty, duplicate, unknown, and non-sortable sort fields", () => {
    const nonSortableConfig = {
      ...config,
      fields: config.fields.map((field) =>
        field.name === "owner" ? { ...field, capabilities: { ...field.capabilities, sortable: false } } : field,
      ),
    };

    expect(parseSorting(",title", config)).toBeNull();
    expect(parseSorting("title,", config)).toBeNull();
    expect(parseSorting("title,-title", config)).toBeNull();
    expect(parseSorting("unknown", config)).toBeNull();
    expect(parseSorting("owner", nonSortableConfig)).toBeNull();
  });
});
