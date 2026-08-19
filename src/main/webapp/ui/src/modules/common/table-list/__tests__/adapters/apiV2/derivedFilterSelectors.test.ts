import * as v from "valibot";
import { describe, expect, it } from "vitest";
import type { SearchSelector } from "@/modules/common/collection/collectionConfig";
import type { ApiV2CollectionMetadata } from "../../../adapters/apiV2/apiV2CollectionMetadata";
import { createApiV2CollectionAdapter } from "../../../adapters/apiV2/createApiV2CollectionAdapter";
import { apiV2CollectionRequestParams } from "../../../adapters/apiV2/createApiV2CollectionFetcher";

type RelationshipValue = {
  relationTo: "instruments";
  value: { id: number; name: string; deleted: boolean; label?: string; floor?: number };
};

type Booking = { id: string; target: RelationshipValue; room: RelationshipValue };

const relationshipSchema = v.object({
  relationTo: v.literal("instruments"),
  value: v.object({
    id: v.number(),
    name: v.string(),
    deleted: v.boolean(),
    label: v.optional(v.string()),
    floor: v.optional(v.number()),
  }),
});

const documentSchema = v.object({ id: v.string(), target: relationshipSchema, room: relationshipSchema });

/**
 * Two relationships, and the config declares each one exactly once. Nothing here names a target's
 * field, so a solution that needs a declaration per relationship fails this test.
 */
const config = {
  slug: "bookingConfigurations",
  idField: "id" as const,
  labels: { singularKey: "a", pluralKey: "b" },
  useAsTitle: "target" as const,
  defaultColumns: ["target" as const, "room" as const],
  fields: [
    { name: "id" as const, labelKey: "id", type: "text" as const, list: false as const },
    {
      name: "target" as const,
      labelKey: "target",
      type: "relationship" as const,
      relationTo: "instruments",
      hasMany: false,
    },
    {
      name: "room" as const,
      labelKey: "room",
      type: "relationship" as const,
      relationTo: "instruments",
      hasMany: false,
    },
  ],
};

const metadata: ApiV2CollectionMetadata<Booking> = {
  resourceName: "bookingConfigurations",
  fields: ["id", "target", "room"],
  sorting: { fields: [], default: [], maximumFields: 5 },
  filtering: {
    selectors: {
      "target.name": {
        operators: ["=contains="],
        wildcards: true,
        title: "Instrument name",
        fieldType: "text",
      },
      "target.deleted": { operators: ["=="], wildcards: false, title: "Deleted", fieldType: "boolean" },
      "room.label": {
        operators: ["==", "=contains="],
        wildcards: false,
        title: "Room label",
        fieldType: "text",
      },
      "room.floor": { operators: ["=="], wildcards: false, fieldType: "number" },
      "target.value": { operators: ["=="], wildcards: false },
      "target.relationTo": { operators: ["=="], wildcards: false },
      "unrelated.thing": { operators: ["=contains="], wildcards: false },
    },
    limits: {
      maximumComparisons: 50,
      maximumLikeComparisons: 10,
      maximumNesting: 10,
      maximumArguments: 100,
      maximumWhereLength: 4096,
    },
  },
  pagination: { defaultLimit: 10, maximumLimit: 100 },
};

describe("filter selectors derived from published relationship targets", () => {
  const adapter = createApiV2CollectionAdapter<Booking>({
    config,
    documentSchema,
    metadata,
    translate: (key) => ({ target: "Bookable item", room: "Room" })[key] ?? key,
  });
  // A derived selector is not a key of the document, which is why its name is read as a string.
  const derived = adapter.config.fields
    .map((field) => ({ ...field, name: String(field.name) }))
    .filter((field) => field.name.includes("."));

  it("offers every target's fields without a declaration for each relationship", () => {
    expect(derived.map((field) => field.name)).toEqual(["target.name", "target.deleted", "room.label", "room.floor"]);
  });

  it("prefixes target field titles so two relationships to one resource stay distinguishable", () => {
    expect(derived.map((field) => field.labelKey)).toEqual([
      "Bookable item: Instrument name",
      "Bookable item: Deleted",
      "Room: Room label",
      // No title published, so the selector itself is the fallback.
      "Room: room.floor",
    ]);
  });

  it("offers them as optional columns but keeps them out of forms and defaults", () => {
    expect(derived.every((field) => field.list !== false && field.form === false)).toBe(true);
    expect(adapter.config.defaultColumns).toEqual(["target", "room"]);
  });

  it("takes the operators and wildcard rule from the published selector", () => {
    const room = derived.find((field) => field.name === "room.label");
    expect(room?.capabilities.filterOperators).toEqual(["equals", "contains"]);
    expect(room?.capabilities.supportsWildcards).toBe(false);
    expect(derived.find((field) => field.name === "target.name")?.capabilities.supportsWildcards).toBe(true);
  });

  it("uses the target field's published primitive type", () => {
    expect(derived.find((field) => field.name === "target.deleted")?.type).toBe("boolean");
    expect(derived.find((field) => field.name === "room.floor")?.type).toBe("number");
  });

  it("selects owner relationships instead of dotted virtual fields", () => {
    const state = {
      filters: { search: "", expression: null },
      sorting: [],
      page: { pageIndex: 0, pageSize: 10 },
      visibleFields: ["room.label" as never, "room.floor" as never],
    };

    expect(adapter.selectedFields(state)).toEqual(["id", "target", "room"]);
    expect(adapter.requiredDepth(state)).toBe(1);
  });

  it("keeps request identity when a fixed-depth relationship already covers a virtual field", () => {
    const state = {
      filters: { search: "", expression: null },
      sorting: [],
      page: { pageIndex: 0, pageSize: 10 },
      visibleFields: ["target" as never],
    };
    const withVirtualField = { ...state, visibleFields: ["target" as never, "target.deleted" as never] };

    expect(apiV2CollectionRequestParams(adapter, withVirtualField, 1).toString()).toBe(
      apiV2CollectionRequestParams(adapter, state, 1).toString(),
    );
  });

  it("renders a primitive value from the expanded target", () => {
    const field = adapter.config.fields.find((candidate) => String(candidate.name) === "target.deleted");
    if (!field || field.list === false || !field.list?.renderCell) throw new Error("Missing target.deleted renderer");
    const row: Booking = {
      id: "1",
      target: { relationTo: "instruments", value: { id: 1, name: "Scope", deleted: false } },
      room: { relationTo: "instruments", value: { id: 2, name: "Room", deleted: false } },
    };

    expect(field.list.renderCell({ config: adapter.config, field, row, value: undefined as never })).toBe("false");
  });

  function adapterWithSearch(listSearchableFields: readonly SearchSelector<Booking>[], sourceMetadata = metadata) {
    return createApiV2CollectionAdapter<Booking>({
      config: { ...config, listSearchableFields },
      documentSchema,
      metadata: sourceMetadata,
    });
  }

  it("uses a published relationship field in the search expression", () => {
    const searchable = adapterWithSearch(["target.name"]);
    const params = searchable.toSearchParams({
      filters: { search: "scope", expression: null },
      sorting: [],
      page: { pageIndex: 0, pageSize: 10 },
      visibleFields: ["target"],
    });

    expect(params.get("where")).toBe("target.name=contains=scope");
  });

  it.each([
    ["target.unknown", /not filterable/],
    ["room.floor", /does not support contains/],
    ["target.value", /wire field/],
  ] as const)("rejects the invalid API search selector %s", (selector, error) => {
    expect(() => adapterWithSearch([selector])).toThrow(error);
  });

  it("rejects a non-text API search selector", () => {
    const selectors = {
      ...metadata.filtering.selectors,
      "target.deleted": {
        operators: ["==", "=contains="] as const,
        wildcards: false,
        title: "Deleted",
        fieldType: "boolean" as const,
      },
    };
    expect(() =>
      adapterWithSearch(["target.deleted"], {
        ...metadata,
        filtering: { ...metadata.filtering, selectors },
      }),
    ).toThrow(/must be text/);
  });

  it("rejects a search allowlist above the API pattern limit", () => {
    expect(() =>
      adapterWithSearch(["target.name"], {
        ...metadata,
        filtering: {
          ...metadata.filtering,
          limits: { ...metadata.filtering.limits, maximumLikeComparisons: 0 },
        },
      }),
    ).toThrow(/Search field limit/);
  });
});
