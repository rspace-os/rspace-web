import type * as v from "valibot";
import { expect, it } from "vitest";
import { apiV2ReadSchemas } from "@/modules/common/api/v2/generated/apiV2ReadSchemas";
import type { apiV2Resources } from "@/modules/common/api/v2/generated/apiV2Resources";
import { apiV2CollectionMetadata } from "@/modules/common/api/v2/generated/tableListMetadata";
import type { InstrumentsReadSchema } from "@/modules/common/api/v2/generated/valibot/schemas";
import { createApiV2CollectionAdapter } from "../adapter";
import { isRelationshipWireSelector } from "../filterFields";
import { apiV2CollectionMetadataFor } from "../metadata";
import { type ApiV2DocumentSchema, expandedValueKeys } from "../schema";

type Instrument = v.InferOutput<typeof InstrumentsReadSchema>;

it("defaults a registry resource's metadata and document schema to the generated ones", () => {
  const adapter = createApiV2CollectionAdapter<
    Instrument,
    ApiV2DocumentSchema<Instrument>,
    typeof apiV2Resources.instruments
  >({
    config: {
      slug: "instruments",
      idField: "id",
      useAsTitle: "name",
      labels: { singularKey: "tableListExamples:record", pluralKey: "tableListExamples:records" },
      defaultColumns: ["name"],
      fields: [
        { name: "id", labelKey: "tableListExamples:fields.id", type: "number", list: false },
        { name: "name", labelKey: "tableListExamples:fields.title", type: "text" },
      ],
    },
    metadata: apiV2CollectionMetadataFor<Instrument>("instruments"),
  });

  expect(adapter.metadata.runtimeFields?.map(({ namespace }) => namespace)).toEqual(["customFields", "extraFields"]);
  expect(adapter.parseResponse({ docs: [{ id: 1, name: "Centrifuge" }], hasNextPage: false }, ["id", "name"])).toEqual({
    rows: [{ id: 1, name: "Centrifuge" }],
    rowCount: undefined,
    hasNextPage: false,
  });
  expect(() => apiV2CollectionMetadataFor("records")).toThrow("No API v2 collection metadata for records");
});

// The adapter lists a target field only when its owner's read schema declares it on the expanded document.
const targetSelectors = Object.entries(apiV2CollectionMetadata).flatMap(([resource, metadata]) => {
  const schema = Object.entries(apiV2ReadSchemas).find(([name]) => name === resource)?.[1];
  if (schema === undefined) return [];
  return Object.entries(metadata.filtering.selectors).flatMap(([selector, published]) => {
    // The adapter reads the target field after the first dot, as filterFields.ts does.
    const dot = selector.indexOf(".");
    const owner = selector.slice(0, dot);
    const field = dot < 0 ? undefined : selector.slice(dot + 1);
    const relationship = schema.entries[owner as keyof typeof schema.entries];
    // The adapter derives a target field only from a selector that publishes its field type.
    const typed = "fieldType" in published && published.fieldType != null;
    if (field === undefined || !typed || isRelationshipWireSelector(selector) || relationship === undefined) return [];
    return [{ resource, selector, owner: relationship, field }];
  });
});

it("finds published target selectors in the registry", () => {
  expect(targetSelectors.length).toBeGreaterThan(0);
});

it.each(targetSelectors.map((entry) => [`${entry.resource} ${entry.selector}`, entry] as const))(
  "declares %s on the owner's expanded read schema",
  (_, { owner, field }) => {
    expect(expandedValueKeys(owner).has(field)).toBe(true);
  },
);
