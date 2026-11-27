import { describe, expect, it } from "vitest";
import type { CollectionConfig, FieldConfig } from "@/modules/common/collection/config";
import { isFilterOperatorCompatible } from "@/modules/common/collection/fieldTypes";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveConfig";

type TestRecord = { id: string; title: string; score: number };
const input: CollectionConfig<TestRecord> = {
  slug: "records",
  idField: "id",
  labels: { singularKey: "tableListExamples:record", pluralKey: "tableListExamples:records" },
  useAsTitle: "title",
  defaultColumns: ["title"],
  listSearchableFields: ["title"],
  fields: [
    { name: "id", type: "text", labelKey: "tableListExamples:fields.id", list: false },
    { name: "title", type: "text", labelKey: "tableListExamples:fields.title" },
    { name: "score", type: "number", labelKey: "tableListExamples:fields.score" },
  ],
};

describe("resolveCollectionConfig", () => {
  const relationshipConfig = {
    slug: "relatedRecords",
    idField: "id",
    labels: { singularKey: "tableListExamples:record", pluralKey: "tableListExamples:records" },
    useAsTitle: "title",
    defaultColumns: ["title", "target"],
    listSearchableFields: ["target.name"],
    fields: [
      { name: "id", type: "text", labelKey: "tableListExamples:fields.id", list: false },
      { name: "title", type: "text", labelKey: "tableListExamples:fields.title" },
      {
        name: "target",
        type: "relationship",
        relationTo: "targets",
        hasMany: false,
        labelKey: "tableListExamples:fields.owner",
      },
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

  it.each<[string, () => unknown, string]>([
    [
      "duplicate field",
      () => resolveCollectionConfig({ ...input, fields: [...input.fields, input.fields[0]] }),
      "Duplicate field: id",
    ],
    [
      "unknown title field",
      () => resolveCollectionConfig({ ...input, useAsTitle: "missing" as "title" }),
      "Unknown title field: missing",
    ],
    [
      "unknown default column",
      () => resolveCollectionConfig({ ...input, defaultColumns: ["missing" as "title"] }),
      "Unknown default column field: missing",
    ],
    [
      "non-text searchable field",
      () => resolveCollectionConfig({ ...input, listSearchableFields: ["score"] }),
      "Searchable field must be text or select: score",
    ],
    [
      "operator that is incompatible with a field type",
      () =>
        resolveCollectionConfig({
          ...input,
          fields: input.fields.map((field) =>
            field.name === "score" ? { ...field, capabilities: { filterOperators: ["contains"] } } : field,
          ),
        }),
      "not compatible",
    ],
    [
      "duplicate search selector",
      () => resolveCollectionConfig({ ...relationshipConfig, listSearchableFields: ["target.name", "target.name"] }),
      "Duplicate searchable",
    ],
  ])("rejects a %s", (_name, resolve, message) => {
    expect(resolve).toThrow(message);
  });
});

describe("declared array metadata", () => {
  const fields: FieldConfig<Record<string, unknown>>[] = [
    {
      name: "quantity",
      type: "number",
      labelKey: "tableListExamples:fields.score",
      required: true,
      writeConstraints: { minimum: 1, maximum: 100 },
    },
    {
      name: "details",
      type: "object",
      labelKey: "tableListExamples:fields.notes",
      fields: [
        {
          name: "notes",
          type: "array",
          labelKey: "tableListExamples:fields.draft",
          fields: [
            {
              name: "text",
              type: "text",
              labelKey: "tableListExamples:fields.title",
              required: true,
              defaultValue: "Draft",
            },
          ],
        },
      ],
    },
  ];
  const array = (): Extract<FieldConfig<TestRecord>, { type: "array" | "object" }> => ({
    name: "title",
    type: "array",
    labelKey: "tableListExamples:fields.score",
    form: false,
    fields,
  });
  const resolve = (field: FieldConfig<TestRecord>) =>
    resolveCollectionConfig({
      ...input,
      listSearchableFields: [],
      defaultSort: [],
      fields: input.fields.map((existing) => (existing.name === "title" ? field : existing)),
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
      labelKey: "tableListExamples:fields.status",
      form: false,
      fields: cycle,
    };
    cycle.push(nested);
    expect(() => resolve({ ...array(), fields: cycle })).toThrow("Cyclic");
    expect(() =>
      resolve({
        ...array(),
        fields: [
          {
            name: "target",
            type: "relationship",
            relationTo: "items",
            hasMany: false,
            labelKey: "tableListExamples:fields.owner",
          },
        ],
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
      const nested: FieldConfig<Record<string, unknown>> = {
        name: "details",
        type,
        labelKey: "tableListExamples:fields.notes",
        fields: [],
      };
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
      ...input,
      defaultSort: [],
      fields: input.fields.map((field) => (field.name === "title" ? array() : field)),
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

it.each([
  ["text", "matches", true],
  ["text", "greaterThan", false],
  ["number", "lessThanOrEqual", true],
  ["number", "contains", false],
  ["boolean", "exists", true],
  ["boolean", "in", false],
  ["dateTime", "greaterThan", true],
  ["dateTime", "in", false],
  ["select", "contains", true],
  ["select", "lessThan", false],
  ["relationship", "notIn", true],
  ["relationship", "contains", false],
] as const)("says whether a %s field accepts %s", (type, operator, compatible) => {
  expect(isFilterOperatorCompatible(type, operator)).toBe(compatible);
});
