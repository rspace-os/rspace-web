import * as v from "valibot";
import { describe, expect, it } from "vitest";
import { createTestI18n } from "@/__tests__/helpers/createTestI18n";
import type { SearchSelector } from "@/modules/common/collection/collectionConfig";
import bookingEnglish from "@/modules/common/i18n/locales/en-US/booking.json";
import commonEnglish from "@/modules/common/i18n/locales/en-US/common.json";
import type { ApiV2CollectionMetadata } from "../../../adapters/apiV2/apiV2CollectionMetadata";
import { enrichApiV2FilterConfig } from "../../../adapters/apiV2/apiV2FilterFields";
import { createApiV2CollectionAdapter } from "../../../adapters/apiV2/createApiV2CollectionAdapter";
import { apiV2CollectionRequestParams } from "../../../adapters/apiV2/createApiV2CollectionFetcher";
import type { FilterExpression } from "../../../tableListState";

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
      target: { operators: ["==", "=in="], wildcards: false, fieldType: "text" },
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
  relationshipFields: {
    "target.name": { operators: ["=contains="], wildcards: true, title: "Instrument name", fieldType: "text" },
    "target.deleted": { operators: ["=="], wildcards: false, title: "Deleted", fieldType: "boolean" },
    "target.globalId": { operators: [], wildcards: false, title: "Global ID", fieldType: "text" },
    "room.label": { operators: ["==", "=contains="], wildcards: false, title: "Room label", fieldType: "text" },
    "room.floor": { operators: ["=="], wildcards: false, fieldType: "number" },
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
    expect(derived.map((field) => field.name)).toEqual([
      "target.name",
      "target.deleted",
      "target.globalId",
      "room.label",
      "room.floor",
    ]);
  });

  it("prefixes target field titles so two relationships to one resource stay distinguishable", () => {
    expect(derived.map((field) => field.label)).toEqual([
      "Bookable item \u2192 Instrument name",
      "Bookable item \u2192 Deleted",
      "Bookable item \u2192 Global ID",
      "Room \u2192 Room label",
      "Room \u2192 floor",
    ]);
  });

  it("offers them as optional columns but keeps them out of forms and defaults", () => {
    expect(derived.every((field) => field.list !== false && field.form === false)).toBe(true);
    expect(adapter.config.defaultColumns).toEqual(["target", "room"]);
  });

  it("offers a field that cannot be filtered on as a column anyway", () => {
    const globalId = derived.find((field) => field.name === "target.globalId");

    expect(globalId?.list).not.toBe(false);
    expect(globalId?.capabilities.filterOperators).toEqual([]);
  });

  it("renders a target field the page's schema never declared", () => {
    const page = adapter.parseResponse(
      {
        docs: [
          {
            id: "1",
            target: {
              relationTo: "instruments",
              value: { id: 7, name: "Confocal", deleted: false, globalId: "IN7" },
            },
            room: { relationTo: "instruments", value: { id: 8, name: "Lab", deleted: false } },
          },
        ],
        totalDocs: 1,
        limit: 10,
        page: 1,
        pagingCounter: 1,
        totalPages: 1,
        hasPrevPage: false,
        hasNextPage: false,
        prevPage: null,
        nextPage: null,
      },
      ["id", "target", "room"] as never[],
    );
    const field = adapter.config.fields.find((candidate) => String(candidate.name) === "target.globalId");
    if (!field || field.list === false || !field.list?.renderCell) throw new Error("Missing target.globalId renderer");

    expect(field.list.renderCell({ config: adapter.config, field, row: page.rows[0], value: undefined as never })).toBe(
      "IN7",
    );
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

  it("derives and serializes relationship filters whose owners are not configured", () => {
    type OwnerlessRecord = { id: string; title: string };
    const ownerlessConfig = {
      slug: "ownerless-records",
      idField: "id" as const,
      labels: { singularKey: "record", pluralKey: "records" },
      useAsTitle: "title" as const,
      defaultColumns: ["title" as const],
      fields: [
        { name: "id" as const, labelKey: "id", type: "text" as const, list: false as const },
        { name: "title" as const, labelKey: "title", type: "text" as const },
      ],
    };
    const ownerlessSchema = v.object({ id: v.string(), title: v.string() });
    const ownerlessMetadata: ApiV2CollectionMetadata<OwnerlessRecord> = {
      resourceName: "ownerless-records",
      fields: ["id", "title"],
      sorting: { fields: [], default: [], maximumFields: 5 },
      filtering: {
        selectors: {
          "createdBy.username": { operators: ["=="], wildcards: false, fieldType: "text" },
          "updatedBy.username": { operators: ["=="], wildcards: false, fieldType: "text" },
          "reviewedBy.username": { operators: ["=="], wildcards: false, fieldType: "text" },
        },
        limits: metadata.filtering.limits,
      },
      relationshipFields: {
        "createdBy.username": {
          operators: ["=="],
          wildcards: false,
          title: "Username",
          viaTitle: "Created by",
          fieldType: "text",
        },
        "updatedBy.username": {
          operators: ["=="],
          wildcards: false,
          title: "Username",
          viaTitle: "Updated by",
          fieldType: "text",
        },
        "reviewedBy.username": {
          operators: ["=="],
          wildcards: false,
          title: "Username",
          fieldType: "text",
        },
      },
      pagination: { defaultLimit: 10, maximumLimit: 100 },
    };
    const translate = (key: string) =>
      ({
        "common:tableList.fields.createdBy": "Erstellt von",
        "common:tableList.fields.updatedBy": "Aktualisiert von",
      })[key] ?? key;
    const adapter = createApiV2CollectionAdapter<OwnerlessRecord>({
      translate,
      config: ownerlessConfig,
      documentSchema: ownerlessSchema,
      metadata: ownerlessMetadata,
    });
    const state = {
      filters: {
        search: "",
        expression: {
          kind: "and" as const,
          children: [
            {
              kind: "comparison" as const,
              field: "createdBy.username" as never,
              operator: "equals" as const,
              value: "ada",
            },
            {
              kind: "comparison" as const,
              field: "updatedBy.username" as never,
              operator: "equals" as const,
              value: "grace",
            },
          ],
        },
      },
      sorting: [],
      page: { pageIndex: 0, pageSize: 10 },
      visibleFields: ["title" as const],
    };
    const createdBy = adapter.config.fields.find((field) => String(field.name) === "createdBy.username");
    const updatedBy = adapter.config.fields.find((field) => String(field.name) === "updatedBy.username");
    const reviewedBy = adapter.config.fields.find((field) => String(field.name) === "reviewedBy.username");

    expect(createdBy).toMatchObject({
      label: "Erstellt von \u2192 Username",
      type: "text",
      list: false,
      form: false,
    });
    expect(updatedBy?.label).toBe("Aktualisiert von \u2192 Username");
    expect(reviewedBy?.label).toBe("reviewedBy \u2192 Username");
    expect(createdBy).not.toHaveProperty("filterPicker");
    expect(
      adapter.config.fields.some((field) => ["createdBy", "updatedBy", "reviewedBy"].includes(String(field.name))),
    ).toBe(false);
    expect(adapter.toSearchParams(state).get("where")).toBe("createdBy.username==ada;updatedBy.username==grace");
    expect(adapter.toSearchParams(state).get("fields[ownerless-records]")).toBe("id,title");
    expect(adapter.selectedFields(state)).toEqual(["id", "title"]);
    expect(adapter.requiredDepth(state)).toBe(0);

    const enriched = enrichApiV2FilterConfig({ config: ownerlessConfig, metadata: ownerlessMetadata, translate });
    expect(enriched.fields.find((field) => String(field.name) === "createdBy.username")?.label).toBe(
      "Erstellt von \u2192 Username",
    );
    expect(
      enriched.fields.find((field) => String(field.name) === "updatedBy.username")?.capabilities.filterOperators,
    ).toEqual(["equals"]);
    expect(enriched.defaultColumns).toEqual(["title"]);
  });

  it("translates shared owner and target field labels through a page-scoped translator", async () => {
    // A page such as All bookable items translates with its own namespace; the shared labels live in
    // `common`, and the server publishes no titles for user fields.
    const i18n = await createTestI18n({ common: commonEnglish, booking: bookingEnglish }, "booking");
    const bookingT = i18n.getFixedT(null, "booking");
    type Audited = { id: string };
    const untitled = { operators: ["==", "=contains="] as const, wildcards: false, fieldType: "text" as const };
    const auditedMetadata: ApiV2CollectionMetadata<Audited> = {
      resourceName: "audited-records",
      fields: ["id"],
      sorting: { fields: [], default: [], maximumFields: 5 },
      filtering: {
        selectors: {
          location: {
            operators: ["==", "!=", "=in=", "=out="],
            wildcards: false,
            fieldType: "text",
            picker: { resource: "booking-locations", identity: "globalId", globalIdPrefix: "IC" },
          },
        },
        limits: metadata.filtering.limits,
      },
      relationshipFields: {
        "createdBy.id": { ...untitled, fieldType: "number" },
        "createdBy.firstName": untitled,
        "updatedBy.email": untitled,
        "updatedBy.createdAt": { ...untitled, fieldType: "dateTime" },
      },
      pagination: { defaultLimit: 10, maximumLimit: 100 },
    };
    const enriched = enrichApiV2FilterConfig<Audited>({
      config: {
        slug: "audited-records",
        idField: "id",
        labels: { singularKey: "a", pluralKey: "b" },
        useAsTitle: "id",
        defaultColumns: ["id"],
        fields: [{ name: "id", labelKey: "id", type: "text" }],
      },
      metadata: auditedMetadata,
      translate: (key) => String(bookingT(key as never)),
    });
    const label = (name: string) => enriched.fields.find((field) => String(field.name) === name)?.label;

    expect(label("createdBy.id")).toBe("Created by \u2192 ID");
    expect(label("createdBy.firstName")).toBe("Created by \u2192 First name");
    expect(label("updatedBy.email")).toBe("Updated by \u2192 Email");
    expect(label("updatedBy.createdAt")).toBe("Updated by \u2192 Created at");
    expect(label("location")).toBe("Location");
    expect(enriched.fields.find((field) => String(field.name) === "location")).toMatchObject({
      type: "relationship",
      relationTo: "booking-locations",
      filterPicker: { resource: "booking-locations", identity: "globalId", globalIdPrefix: "IC" },
    });
    expect(enriched.fields.some((field) => field.label?.includes("tableList."))).toBe(false);
  });

  it("derives a filter-only identity relationship and its target fields from metadata", () => {
    const withoutTargetConfig = {
      ...config,
      useAsTitle: "room" as const,
      defaultColumns: ["room" as const],
      fields: config.fields.filter((field) => field.name !== "target"),
    };
    const withPicker: ApiV2CollectionMetadata<Booking> = {
      ...metadata,
      filtering: {
        ...metadata.filtering,
        selectors: {
          ...metadata.filtering.selectors,
          target: {
            operators: ["==", "=in="] as const,
            wildcards: false,
            fieldType: "text",
            picker: { resource: "instruments", identity: "globalId", globalIdPrefix: "IN" },
          },
        },
      },
    };
    const built = createApiV2CollectionAdapter<Booking>({
      config: withoutTargetConfig,
      documentSchema,
      metadata: withPicker,
    });
    const identity = built.config.fields.find((field) => String(field.name) === "target");
    const targetName = built.config.fields.find((field) => String(field.name) === "target.name");

    expect(identity).toMatchObject({
      type: "relationship",
      relationTo: "instruments",
      hasMany: false,
      filterPicker: { resource: "instruments", identity: "globalId", globalIdPrefix: "IN" },
      list: false,
      form: false,
    });
    expect(targetName?.capabilities.filterOperators).toEqual(["contains"]);
    expect(
      built.selectedFields({
        filters: { search: "", expression: null },
        sorting: [],
        page: { pageIndex: 0, pageSize: 10 },
        visibleFields: ["target.name"] as never,
      }),
    ).toEqual(["id", "room", "target"]);
    expect(
      built.requiredDepth({
        filters: { search: "", expression: null },
        sorting: [],
        page: { pageIndex: 0, pageSize: 10 },
        visibleFields: ["target.name"] as never,
      }),
    ).toBe(1);
  });

  it("normalizes known picker operands before serialization and rejects malformed identities", () => {
    const withPicker: ApiV2CollectionMetadata<Booking> = {
      ...metadata,
      filtering: {
        ...metadata.filtering,
        selectors: {
          ...metadata.filtering.selectors,
          target: {
            operators: ["==", "!=", "=in=", "=out="],
            wildcards: false,
            fieldType: "text",
            picker: { resource: "instruments", identity: "globalId", globalIdPrefix: "IN" },
          },
        },
      },
    };
    const built = createApiV2CollectionAdapter<Booking>({ config, documentSchema, metadata: withPicker });
    const listFilter = {
      filters: {
        search: "",
        expression: {
          kind: "comparison" as const,
          field: "target" as const,
          operator: "in" as const,
          value: [" in2 ", "in3"],
        },
      },
      sorting: [],
      page: { pageIndex: 0, pageSize: 10 },
      visibleFields: ["room"] as const,
    };

    expect(built.toSearchParams(listFilter).get("where")).toBe("target=in=(IN2,IN3)");
    expect(() =>
      built.toSearchParams({
        ...listFilter,
        filters: {
          ...listFilter.filters,
          expression: { kind: "comparison", field: "target", operator: "equals", value: "other" },
        },
      }),
    ).toThrow("Invalid relationship filter value: target");
  });

  it("leaves unknown picker sources on the typed filter path", () => {
    const withUnknownPicker: ApiV2CollectionMetadata<Booking> = {
      ...metadata,
      filtering: {
        ...metadata.filtering,
        selectors: {
          ...metadata.filtering.selectors,
          target: {
            operators: ["=="],
            wildcards: false,
            fieldType: "text",
            picker: { resource: "not-installed", identity: "globalId", globalIdPrefix: "ZZ" },
          },
        },
      },
    };
    const built = createApiV2CollectionAdapter<Booking>({ config, documentSchema, metadata: withUnknownPicker });

    expect(
      built
        .toSearchParams({
          filters: {
            search: "",
            expression: { kind: "comparison", field: "target", operator: "equals", value: "typed-value" },
          },
          sorting: [],
          page: { pageIndex: 0, pageSize: 10 },
          visibleFields: ["room"],
        })
        .get("where"),
    ).toBe("target==typed-value");
  });

  it("preserves legacy numeric, relation target, and me identity values", () => {
    const legacyMetadata: ApiV2CollectionMetadata<Booking> = {
      ...metadata,
      filtering: {
        ...metadata.filtering,
        selectors: {
          ...metadata.filtering.selectors,
          "target.value": { operators: ["=="], wildcards: false, fieldType: "number" },
          "target.relationTo": { operators: ["=="], wildcards: false, fieldType: "text" },
          "createdBy.value": { operators: ["=="], wildcards: false, fieldType: "text" },
        },
      },
    };
    const built = createApiV2CollectionAdapter<Booking>({ config, documentSchema, metadata: legacyMetadata });
    const expression: FilterExpression<Booking> = {
      kind: "and",
      children: [
        { kind: "comparison", field: "target.value", operator: "equals", value: 42 },
        { kind: "comparison", field: "target.relationTo", operator: "equals", value: "instruments" },
        { kind: "comparison", field: "createdBy.value" as never, operator: "equals", value: "me" },
      ],
    };

    expect(
      built
        .toSearchParams({
          filters: { search: "", expression },
          sorting: [],
          page: { pageIndex: 0, pageSize: 10 },
          visibleFields: ["room"],
        })
        .get("where"),
    ).toBe("target.value==42;target.relationTo==instruments;createdBy.value==me");
  });

  it("does not attach a picker to a multi-target relationship without picker metadata", () => {
    const multiTargetConfig = {
      ...config,
      fields: config.fields.map((field) => (field.name === "target" ? { ...field, hasMany: true } : field)),
    };
    const built = createApiV2CollectionAdapter<Booking>({ config: multiTargetConfig, documentSchema, metadata });

    expect(built.config.fields.find((field) => field.name === "target")?.filterPicker).toBeUndefined();
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

  it("maps a global-ID search field to an exact relationship search", () => {
    const searchable = adapterWithSearch(["target.name", "target.globalId"]);
    const params = searchable.toSearchParams({
      filters: { search: "in2", expression: null },
      sorting: [],
      page: { pageIndex: 0, pageSize: 10 },
      visibleFields: ["target"],
    });

    expect(params.get("where")).toBe("target.name=contains=in2,target==IN2");
  });

  it("keeps the validated legacy IN search when picker metadata names an unavailable source", () => {
    const selectors = {
      ...metadata.filtering.selectors,
      target: {
        operators: ["=="] as const,
        wildcards: false,
        fieldType: "text" as const,
        picker: { resource: "retired-instruments", identity: "globalId" as const, globalIdPrefix: "IN" },
      },
    };
    const searchable = adapterWithSearch(["target.name", "target.globalId"], {
      ...metadata,
      filtering: { ...metadata.filtering, selectors },
    });

    const params = searchable.toSearchParams({
      filters: { search: "in2", expression: null },
      sorting: [],
      page: { pageIndex: 0, pageSize: 10 },
      visibleFields: ["target"] as never,
    });

    expect(params.get("where")).toBe("target.name=contains=in2,target==IN2");
  });

  it("does not use an incompatible source's declared prefix as a target identity", () => {
    const selectors = {
      ...metadata.filtering.selectors,
      target: {
        operators: ["=="] as const,
        wildcards: false,
        fieldType: "text" as const,
        picker: { resource: "instruments", identity: "globalId" as const, globalIdPrefix: "XX" },
      },
    };
    const searchable = adapterWithSearch(["target.name", "target.globalId"], {
      ...metadata,
      filtering: { ...metadata.filtering, selectors },
    });

    const params = searchable.toSearchParams({
      filters: { search: "XX2", expression: null },
      sorting: [],
      page: { pageIndex: 0, pageSize: 10 },
      visibleFields: ["target"] as never,
    });

    expect(params.get("where")).toBe("target.name=contains=XX2");
  });

  it("keeps an out-of-range instrument identifier as plain text", () => {
    const searchable = adapterWithSearch(["target.name", "target.globalId"]);
    const params = searchable.toSearchParams({
      filters: { search: "IN9223372036854775808", expression: null },
      sorting: [],
      page: { pageIndex: 0, pageSize: 10 },
      visibleFields: ["target"],
    });

    expect(params.get("where")).toBe("target.name=contains=IN9223372036854775808");
  });

  it("keeps non-instrument identifiers as ordinary name searches", () => {
    const searchable = adapterWithSearch(["target.name", "target.globalId"]);
    const params = searchable.toSearchParams({
      filters: { search: "ic2", expression: null },
      sorting: [],
      page: { pageIndex: 0, pageSize: 10 },
      visibleFields: ["target"],
    });

    expect(params.get("where")).toBe("target.name=contains=ic2");
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
