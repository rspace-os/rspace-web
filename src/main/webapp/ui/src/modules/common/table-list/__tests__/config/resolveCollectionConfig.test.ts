import { describe, expect, it } from "vitest";
import type { CollectionConfig, FieldConfig } from "@/modules/common/collection/collectionConfig";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import { config, type TestRecord } from "../fixtures/tableListFixtures";

function input(): CollectionConfig<TestRecord> {
  return {
    slug: config.slug,
    idField: config.idField,
    labels: config.labels,
    useAsTitle: config.useAsTitle,
    defaultColumns: config.defaultColumns,
    listSearchableFields: config.listSearchableFields,
    pagination: config.pagination,
    defaultSort: config.defaultSort,
    fields: config.fields,
  };
}

describe("resolveCollectionConfig", () => {
  it("applies type-specific capability defaults", () => {
    expect(config.fields.find((field) => field.name === "title")?.capabilities.filterOperators).toContain("matches");
    expect(config.fields.find((field) => field.name === "score")?.capabilities.filterOperators).toContain(
      "greaterThan",
    );
    expect(config.fields.find((field) => field.name === "enabled")?.capabilities.filterOperators).not.toContain(
      "notIn",
    );
  });

  const invalidCases: readonly [string, (value: CollectionConfig<TestRecord>) => CollectionConfig<TestRecord>][] = [
    ["duplicate field", (value) => ({ ...value, fields: [...value.fields, value.fields[0]] })],
    ["unknown title field", (value) => ({ ...value, useAsTitle: "missing" as "title" })],
    ["unknown default column", (value) => ({ ...value, defaultColumns: ["missing" as "title"] })],
    ["non-text searchable field", (value) => ({ ...value, listSearchableFields: ["score"] })],
  ];

  it.each(invalidCases)("rejects a %s", (_name, mutate) => {
    expect(() => resolveCollectionConfig(mutate(input()))).toThrow();
  });

  it("rejects operators that are incompatible with a field type", () => {
    const value = input();
    expect(() =>
      resolveCollectionConfig({
        ...value,
        fields: value.fields.map((field) =>
          field.name === "score" ? { ...field, capabilities: { filterOperators: ["contains"] } } : field,
        ),
      }),
    ).toThrow("not compatible");
  });

  const relationshipConfig = {
    slug: "relatedRecords",
    idField: "id",
    labels: { singularKey: "record", pluralKey: "records" },
    useAsTitle: "title",
    defaultColumns: ["title", "target"],
    listSearchableFields: ["target.name"],
    fields: [
      { name: "id", type: "text", labelKey: "id", list: false },
      { name: "title", type: "text", labelKey: "title" },
      { name: "target", type: "relationship", relationTo: "targets", hasMany: false, labelKey: "target" },
    ],
  } as const satisfies CollectionConfig<{
    id: string;
    title: string;
    target: { relationTo: "targets"; value: { name: string } };
  }>;

  it("accepts a one-hop relationship search selector before API metadata is available", () => {
    expect(resolveCollectionConfig(relationshipConfig).listSearchableFields).toEqual(["target.name"]);
  });

  it.each(["title.name", "target.owner.name", ".name", "target."])(
    "rejects the invalid relationship search selector %s",
    (selector) => {
      expect(() =>
        resolveCollectionConfig({
          ...relationshipConfig,
          listSearchableFields: [selector as "target.name"],
        }),
      ).toThrow("relationship");
    },
  );

  it("rejects duplicate search selectors", () => {
    expect(() =>
      resolveCollectionConfig({
        ...relationshipConfig,
        listSearchableFields: ["target.name", "target.name"],
      }),
    ).toThrow("Duplicate searchable");
  });
});

describe("declared array metadata", () => {
  const fields: FieldConfig<Record<string, unknown>>[] = [
    {
      name: "quantity",
      type: "number",
      labelKey: "quantity",
      required: true,
      writeConstraints: { minimum: 1, maximum: 100 },
    },
    {
      name: "details",
      type: "object",
      labelKey: "details",
      fields: [
        {
          name: "notes",
          type: "array",
          labelKey: "notes",
          fields: [{ name: "text", type: "text", labelKey: "text", required: true, defaultValue: "Draft" }],
        },
      ],
    },
  ];
  const array = (): Extract<FieldConfig<TestRecord>, { type: "array" | "object" }> => ({
    name: "title",
    type: "array",
    labelKey: "rows",
    form: false,
    fields,
  });
  const resolve = (field: FieldConfig<TestRecord>) =>
    resolveCollectionConfig({
      ...input(),
      listSearchableFields: [],
      defaultSort: [],
      fields: input().fields.map((existing) => (existing.name === "title" ? field : existing)),
    });

  it("carries recursive child constraints and defaults while keeping arrays non-filterable", () => {
    const resolved = resolve(array()).fields.find((field) => field.name === "title");
    expect(resolved).toMatchObject({
      fields,
      capabilities: { sortable: false, filterOperators: [], supportsWildcards: false },
    });
  });

  it("rejects missing, empty, duplicate, cyclic and relationship children", () => {
    const missing = array();
    Reflect.deleteProperty(missing, "fields");
    expect(() => resolve(missing)).toThrow("non-empty child fields");
    expect(() => resolve({ ...array(), fields: [] })).toThrow("non-empty child fields");
    expect(() => resolve({ ...array(), fields: [fields[0], fields[0]] })).toThrow("Duplicate");
    const cycle: FieldConfig<Record<string, unknown>>[] = [];
    const nested: FieldConfig<Record<string, unknown>> = {
      name: "cycle",
      type: "object",
      labelKey: "cycle",
      form: false,
      fields: cycle,
    };
    cycle.push(nested);
    expect(() => resolve({ ...array(), fields: cycle })).toThrow("Cyclic");
    expect(() =>
      resolve({
        ...array(),
        fields: [{ name: "target", type: "relationship", relationTo: "items", hasMany: false, labelKey: "target" }],
      }),
    ).toThrow("Embedded relationship");
  });

  it.each([{ child: null }, { child: "text" }, { child: { name: "quantity" } }])(
    "rejects an invalid child declaration $child",
    ({ child }) => {
      const invalid = array();
      Reflect.set(invalid, "fields", [child]);
      expect(() => resolve(invalid)).toThrow("Invalid child field declaration");
    },
  );

  it("requires nested arrays and objects to declare non-empty named children", () => {
    for (const type of ["array", "object"] as const) {
      const nested: FieldConfig<Record<string, unknown>> = { name: "details", type, labelKey: "details", fields: [] };
      expect(() => resolve({ ...array(), fields: [nested] })).toThrow("non-empty child fields");
      Reflect.deleteProperty(nested, "fields");
      expect(() => resolve({ ...array(), fields: [nested] })).toThrow("non-empty child fields");
    }
    expect(() => resolve({ ...array(), fields: [{ ...fields[0], name: " " }] })).toThrow("empty child field");
  });

  it("requires a custom editor and rejects array sorting or filtering", () => {
    const custom = array();
    Reflect.deleteProperty(custom, "form");
    expect(() => resolve(custom)).toThrow("custom editor");
    expect(() => resolve({ ...array(), capabilities: { sortable: true } })).toThrow("cannot be filtered or sorted");
    expect(() => resolve({ ...array(), capabilities: { filterOperators: ["equals"] } })).toThrow(
      "cannot be filtered or sorted",
    );
    expect(() => resolve({ ...array(), fields: [{ ...fields[0], capabilities: { sortable: true } }] })).toThrow(
      "Embedded field quantity cannot be filtered or sorted",
    );
    expect(() =>
      resolve({ ...array(), fields: [{ ...fields[0], capabilities: { filterOperators: ["equals"] } }] }),
    ).toThrow("Embedded field quantity cannot be filtered or sorted");
  });

  it("rejects search and sort paths into embedded rows", () => {
    const value = {
      ...input(),
      defaultSort: [],
      fields: input().fields.map((field) => (field.name === "title" ? array() : field)),
    };
    expect(() => resolveCollectionConfig({ ...value, listSearchableFields: ["title.quantity"] })).toThrow(
      "root must be a relationship",
    );
    expect(() => resolveCollectionConfig({ ...value, listSearchableFields: ["title"] })).toThrow(
      "Searchable field must be text or select",
    );
    expect(() =>
      resolveCollectionConfig({
        ...value,
        listSearchableFields: [],
        defaultSort: [{ field: "title", direction: "asc" }],
      }),
    ).toThrow("not sortable");
    const sort = { field: "title", direction: "asc" } as const;
    Reflect.set(sort, "field", "title.quantity");
    expect(() => resolveCollectionConfig({ ...value, listSearchableFields: [], defaultSort: [sort] })).toThrow(
      "Unknown default sort field",
    );
  });
});
