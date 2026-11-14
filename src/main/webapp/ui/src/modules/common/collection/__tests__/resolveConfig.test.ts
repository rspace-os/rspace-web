import { describe, expect, it } from "vitest";
import type { CollectionConfig } from "@/modules/common/collection/config";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveConfig";

type TestRecord = { id: string; title: string; score: number };
const input: CollectionConfig<TestRecord> = {
  slug: "records",
  idField: "id",
  labels: { singularKey: "tableList.examples.record", pluralKey: "tableList.examples.records" },
  useAsTitle: "title",
  defaultColumns: ["title"],
  listSearchableFields: ["title"],
  fields: [
    { name: "id", type: "text", labelKey: "tableList.examples.fields.id", list: false },
    { name: "title", type: "text", labelKey: "tableList.examples.fields.title" },
    { name: "score", type: "number", labelKey: "tableList.examples.fields.score" },
  ],
};

describe("resolveCollectionConfig", () => {
  const invalidCases: readonly [string, (value: CollectionConfig<TestRecord>) => CollectionConfig<TestRecord>][] = [
    ["duplicate field", (value) => ({ ...value, fields: [...value.fields, value.fields[0]] })],
    ["unknown title field", (value) => ({ ...value, useAsTitle: "missing" as "title" })],
    ["unknown default column", (value) => ({ ...value, defaultColumns: ["missing" as "title"] })],
    ["non-text searchable field", (value) => ({ ...value, listSearchableFields: ["score"] })],
  ];

  it.each(invalidCases)("rejects a %s", (_name, mutate) => {
    expect(() => resolveCollectionConfig(mutate(input))).toThrow();
  });

  it("rejects operators that are incompatible with a field type", () => {
    const value = input;
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
