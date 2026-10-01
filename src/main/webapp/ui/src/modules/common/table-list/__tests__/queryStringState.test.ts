import { describe, expect, it } from "vitest";
import { parseColumns, parseSorting, serializeColumns, serializeSorting } from "../queryStringState";
import { config, type TestRecord } from "./fixtures/tableListFixtures";

describe("table-list query string state", () => {
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
