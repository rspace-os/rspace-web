import { afterEach, describe, expect, it } from "vitest";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveConfig";
import { parseRawTableView, readRawTableView, writeRawTableView } from "../utils/viewStorage";

type TestRecord = { id: string; title: string; owner: string; score: number; enabled: boolean; modifiedAt: string };
const config = resolveCollectionConfig<TestRecord>({
  slug: "records",
  idField: "id",
  useAsTitle: "title",
  labels: { singularKey: "tableListExamples:record", pluralKey: "tableListExamples:records" },
  defaultColumns: ["title", "owner", "score", "enabled", "modifiedAt"],
  defaultSort: [{ field: "modifiedAt", direction: "desc" }],
  fields: [
    { name: "id", type: "text", labelKey: "tableListExamples:fields.id", list: false },
    { name: "title", type: "text", labelKey: "tableListExamples:fields.title" },
    { name: "owner", type: "text", labelKey: "tableListExamples:fields.owner" },
    { name: "score", type: "number", labelKey: "tableListExamples:fields.score" },
    { name: "enabled", type: "boolean", labelKey: "tableListExamples:fields.enabled" },
    { name: "modifiedAt", type: "dateTime", labelKey: "tableListExamples:fields.modified" },
  ],
});

const defaults = JSON.stringify({ fields: config.defaultColumns });
const stored = (view: Record<string, unknown>) =>
  JSON.stringify({ v: 1, search: null, where: null, columns: null, sort: null, ...view });

afterEach(() => window.localStorage.clear());

describe("a stored table view", () => {
  it("round-trips raw values and records the default columns with a column selection", () => {
    const raw = {
      search: "Ada",
      where: "owner==Ada",
      columns: JSON.stringify({ fields: ["title", "score"] }),
      sort: "-score",
    };

    writeRawTableView("key", raw, config.defaultColumns);

    expect(JSON.parse(window.localStorage.getItem("key") as string)).toEqual({ v: 1, ...raw, defaults });
    expect(readRawTableView(window.localStorage.getItem("key"), config.defaultColumns)).toEqual(raw);
  });

  it("stores empty values as absent and removes an entirely empty view", () => {
    window.localStorage.setItem("key", "stale");
    writeRawTableView("key", { search: "Ada", where: "", columns: null, sort: "" }, config.defaultColumns);
    expect(JSON.parse(window.localStorage.getItem("key") as string)).toEqual({
      v: 1,
      search: "Ada",
      where: null,
      columns: null,
      sort: null,
    });

    writeRawTableView("key", { search: "", where: null, columns: null, sort: null }, config.defaultColumns);
    expect(window.localStorage.getItem("key")).toBeNull();
  });

  it.each([
    ["not JSON", "not-json"],
    ["another version", stored({ v: 2 })],
    ["a missing member", JSON.stringify({ v: 1, search: null, where: null, columns: null })],
    ["a column selection that is not a string", stored({ columns: 4 })],
    ["a column selection without its defaults", stored({ columns: JSON.stringify({ fields: ["title"] }) })],
    ["a filter that is not RSQL", stored({ search: "Ada", where: "not valid rsql" })],
  ])("is malformed with %s", (_label, serialized) => {
    expect(readRawTableView(serialized, config.defaultColumns)).toBe("malformed");
  });

  it("falls back to the default columns for a selection that is not a column list, keeping the other fields", () => {
    expect(readRawTableView(stored({ search: "Ada", columns: "{", defaults }), config.defaultColumns)).toEqual({
      search: "Ada",
      where: null,
      columns: null,
      sort: null,
    });
  });
});

describe("a saved column selection when the configured defaults change", () => {
  const view = (fields: readonly string[], savedDefaults: readonly string[]) =>
    stored({ columns: JSON.stringify({ fields }), defaults: JSON.stringify({ fields: savedDefaults }) });
  const restored = (serialized: string) => {
    const raw = readRawTableView(serialized, config.defaultColumns);
    return raw === null || raw === "malformed" || raw.columns === null ? null : JSON.parse(raw.columns).fields;
  };

  it.each<[string, readonly string[], readonly string[], readonly string[]]>([
    [
      "shows a default column added since the view was saved, after the default before it",
      ["title", "enabled"],
      ["title", "owner", "enabled", "modifiedAt"],
      ["title", "score", "enabled"],
    ],
    [
      "keeps a default hidden that the user removed after seeing it",
      ["title", "score"],
      config.defaultColumns,
      ["title", "score"],
    ],
    [
      "keeps a runtime-field column it does not know while merging a new default",
      ["title", "customFields.SF104"],
      ["title", "owner", "enabled", "modifiedAt"],
      ["title", "score", "customFields.SF104"],
    ],
  ])("%s", (_label, fields, savedDefaults, expected) => {
    expect(restored(view(fields, savedDefaults))).toEqual(expected);
  });

  it("skips the merge when the saved defaults are not a column list", () => {
    expect(restored(stored({ columns: JSON.stringify({ fields: ["title"] }), defaults: "{" }))).toEqual(["title"]);
  });
});

describe("a URL view resolved against the configuration", () => {
  it("resolves valid members and marks each invalid member independently", () => {
    expect(
      parseRawTableView(
        {
          search: "Ada",
          where: "owner==Ada",
          columns: JSON.stringify({ fields: ["title", "removedField"] }),
          sort: "title,-score",
        },
        config,
      ),
    ).toEqual({
      search: "Ada",
      where: { kind: "comparison", field: "owner", operator: "equals", value: "Ada" },
      columns: null,
      sort: [
        { field: "title", direction: "asc" },
        { field: "score", direction: "desc" },
      ],
    });
    expect(
      parseRawTableView({ search: null, where: "removedField==value", columns: null, sort: "removedField" }, config),
    ).toEqual({ search: undefined, where: null, columns: undefined, sort: null });
  });

  it("drops only the stale filter and column and keeps the valid siblings", () => {
    const dropped: string[] = [];
    const stale = {
      isStale: (name: string) => name.startsWith("customFields."),
      onDropped: (name: string) => dropped.push(name),
    };

    const parsed = parseRawTableView(
      {
        search: null,
        where: 'owner=="Ada";customFields.SF104=="BSL-2"',
        columns: JSON.stringify({ fields: ["title", "customFields.SF104", "score"] }),
        sort: null,
      },
      config,
      stale,
    );

    expect(parsed.where).toEqual({ kind: "comparison", field: "owner", operator: "equals", value: "Ada" });
    expect(parsed.columns).toEqual(["title", "score"]);
    expect(dropped).toEqual(["customFields.SF104", "customFields.SF104"]);
  });
});
