import { describe, expect, it } from "vitest";
import type { CollectionConfig } from "@/modules/common/collection/config";
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
