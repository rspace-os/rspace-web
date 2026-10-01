import { describe, expect, it } from "vitest";
import type { CollectionConfig } from "@/modules/common/collection/collectionConfig";
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
    ).toThrow(/not compatible/);
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
      ).toThrow(/searchable|relationship/i);
    },
  );

  it("rejects duplicate search selectors", () => {
    expect(() =>
      resolveCollectionConfig({
        ...relationshipConfig,
        listSearchableFields: ["target.name", "target.name"],
      }),
    ).toThrow(/Duplicate searchable/);
  });
});
