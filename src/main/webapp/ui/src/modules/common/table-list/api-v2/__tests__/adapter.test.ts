import * as v from "valibot";
import { describe, expect, it } from "vitest";
import { v2ListEnvelope } from "@/__tests__/helpers/v2ListEnvelope";
import type { InstrumentsRead } from "@/modules/common/api/v2/generated/models/instrumentsRead";
import { runtimeDocumentSchema as documentSchema } from "../../__tests__/fixtures/schema";
import { hazardClassDefinition } from "../../__tests__/fixtures/tableList";
import type { CollectionQueryState } from "../../types";
import { RuntimeFieldProjectionLimitError } from "../../utils/errors";
import { createApiV2CollectionAdapter } from "../adapter";
import { apiV2CollectionRequestParams } from "../fetcher";
import { createApiV2FilterFields, enrichApiV2FilterConfig } from "../filterFields";
import type { ApiV2CollectionMetadata } from "../metadata";
import type { RuntimeFieldDefinition } from "../runtimeFieldCatalog";

type Instrument = Required<Pick<InstrumentsRead, "id" | "name">> & {
  customFields?: Record<string, string | number | readonly string[] | null>;
};

const config = {
  slug: "instruments",
  idField: "id" as const,
  labels: { singularKey: "instrument", pluralKey: "instruments" },
  useAsTitle: "name" as const,
  defaultColumns: ["name" as const],
  fields: [
    { name: "id" as const, labelKey: "id", type: "number" as const, list: false as const },
    { name: "name" as const, labelKey: "name", type: "text" as const },
  ],
};

const metadata: ApiV2CollectionMetadata<Instrument> = {
  resourceName: "instruments",
  fields: ["id", "name"],
  sorting: { fields: ["name"], default: [{ field: "name", direction: "asc" }], maximumFields: 5 },
  filtering: {
    selectors: { name: { operators: ["==", "=contains="], wildcards: true, fieldType: "text" } },
    limits: {
      maximumComparisons: 50,
      maximumLikeComparisons: 10,
      maximumNesting: 10,
      maximumArguments: 100,
      maximumWhereLength: 4096,
    },
  },
  runtimeFields: [
    {
      namespace: "customFields",
      catalog: "/api/v2/instruments/fields/customFields",
      responseField: "customFields",
      filterable: true,
      columnSelectable: true,
      sortable: false,
      maximumProjections: 2,
      catalogDefaultLimit: 50,
      catalogMaximumLimit: 200,
      catalogMaximumIds: 50,
      via: "",
    },
  ],
  pagination: { defaultLimit: 20, maximumLimit: 100 },
};

const temperature: RuntimeFieldDefinition = {
  ...hazardClassDefinition,
  id: "SF108",
  selector: "customFields.SF108",
  label: "Preservation temperature",
  type: "number",
  jsonType: "number",
  operators: ["==", "=ge=", "=le=", "=exists="],
  source: { id: "IT9", label: "Cell line template" },
};

function adapter(definitions: readonly RuntimeFieldDefinition[]) {
  return createApiV2CollectionAdapter<Instrument>({
    config,
    documentSchema,
    metadata,
    runtimeFields: [{ namespace: "customFields", definitions }],
  });
}

function state(visibleFields: readonly string[]): CollectionQueryState<Instrument> {
  return {
    filters: { search: "", expression: null },
    sorting: [],
    page: { pageIndex: 0, pageSize: 20 },
    visibleFields: visibleFields as CollectionQueryState<Instrument>["visibleFields"],
  };
}

describe("runtime custom fields", () => {
  it("publishes runtime sources for the panels with their separate capabilities", () => {
    expect(adapter([]).config.runtimeSources).toEqual([
      {
        namespace: "customFields",
        catalog: "/api/v2/instruments/fields/customFields",
        maximumLimit: 200,
        filterable: true,
        columnSelectable: true,
      },
    ]);
  });

  it("derives one filterable, optional column per catalog definition", () => {
    const field = adapter([hazardClassDefinition]).config.fields.find(
      (candidate) => String(candidate.name) === "customFields.SF104",
    );

    expect(field?.type).toBe("text");
    expect(field?.form).toBe(false);
    expect(field?.capabilities.filterOperators).toContain("contains");
    expect(field?.capabilities.sortable).toBe(false);
  });

  it("carries the definition source so duplicate labels stay distinguishable", () => {
    const other: RuntimeFieldDefinition = {
      ...hazardClassDefinition,
      id: "SF204",
      selector: "customFields.SF204",
      source: { id: "IT11", label: "Bacterial template" },
    };
    const fields = adapter([hazardClassDefinition, other]).config.fields.filter(
      (candidate) => candidate.origin?.kind === "runtimeField",
    );

    expect(fields.map((field) => field.derivedLabel)).toEqual([{ text: "Hazard class" }, { text: "Hazard class" }]);
    expect(fields.map((field) => field.labelKey)).toEqual(["customFields.SF104", "customFields.SF204"]);
    expect(fields.map((field) => field.origin?.sourceLabel)).toEqual(["Cell line template", "Bacterial template"]);
    expect(fields.map((field) => field.origin?.runtimeValueType)).toEqual(["text", "text"]);
  });

  it("maps a numeric definition to a number field", () => {
    const field = adapter([temperature]).config.fields.find(
      (candidate) => String(candidate.name) === "customFields.SF108",
    );

    expect(field?.type).toBe("number");
    expect(field?.capabilities.filterOperators).toContain("greaterThanOrEqual");
    expect(field?.capabilities.filterOperators).not.toContain("contains");
  });

  it("retains Inventory runtime types for their specialised value controls", () => {
    const date: RuntimeFieldDefinition = {
      ...hazardClassDefinition,
      id: "SF109",
      selector: "customFields.SF109",
      type: "date",
      operators: ["==", "=ge=", "=le=", "=exists="],
    };
    const time: RuntimeFieldDefinition = {
      ...hazardClassDefinition,
      id: "SF110",
      selector: "customFields.SF110",
      type: "time",
      operators: ["==", "=ge=", "=le=", "=exists="],
    };
    const choice: RuntimeFieldDefinition = {
      ...hazardClassDefinition,
      id: "SF111",
      selector: "customFields.SF111",
      type: "choice",
      jsonType: "array",
      options: ["Brightfield", "Fluorescence"],
      operators: ["=contains=", "=in=", "=exists="],
    };

    const fields = adapter([date, time, choice]).config.fields.filter(
      (candidate) => candidate.origin?.kind === "runtimeField",
    );

    expect(fields.map((field) => field.origin?.runtimeValueType)).toEqual(["date", "time", "choice"]);
    expect(fields.map((field) => field.type)).toEqual(["text", "text", "select"]);
  });

  it("projects only the visible custom columns", () => {
    const parameters = apiV2CollectionRequestParams(
      adapter([hazardClassDefinition, temperature]),
      state(["name", "customFields.SF104"]),
    );

    expect(parameters.get("fields[instruments]")).toBe("id,name,customFields.SF104");
  });

  it("does not project a custom field that is only filtered on", () => {
    const filtered = {
      ...state(["name"]),
      filters: {
        search: "",
        expression: {
          kind: "comparison" as const,
          field: "customFields.SF104" as never,
          operator: "equals" as const,
          value: "BSL-2",
        },
      },
    };
    const parameters = apiV2CollectionRequestParams(adapter([hazardClassDefinition]), filtered);

    expect(parameters.get("fields[instruments]")).toBe("id,name");
    expect(parameters.get("where")).toBe("customFields.SF104==BSL-2");
  });

  it("does not add a predicate for a custom column with no filter", () => {
    const parameters = apiV2CollectionRequestParams(
      adapter([hazardClassDefinition]),
      state(["name", "customFields.SF104"]),
    );

    expect(parameters.get("where")).toBeNull();
  });

  it("does not raise the relationship expansion depth for a custom column", () => {
    expect(adapter([hazardClassDefinition]).requiredDepth(state(["name", "customFields.SF104"]))).toBe(0);
  });

  it("refuses more custom columns than the server accepts", () => {
    let error: unknown;
    try {
      apiV2CollectionRequestParams(
        adapter([
          hazardClassDefinition,
          temperature,
          { ...hazardClassDefinition, id: "SF9", selector: "customFields.SF9" },
        ]),
        state(["customFields.SF104", "customFields.SF108", "customFields.SF9"]),
      );
    } catch (caught) {
      error = caught;
    }

    expect(error).toBeInstanceOf(RuntimeFieldProjectionLimitError);
    expect(error).toMatchObject({ limit: 2 });
  });

  it("counts the column limit per namespace, not across all of them", () => {
    const filterOnly = metadata.runtimeFields?.[0];
    const withHop: ApiV2CollectionMetadata<Instrument> = {
      ...metadata,
      runtimeFields: [
        ...(metadata.runtimeFields ?? []),
        {
          ...(filterOnly as NonNullable<typeof filterOnly>),
          namespace: "target.customFields",
          responseField: "",
          columnSelectable: false,
          maximumProjections: 0,
          via: "target",
        },
      ],
    };
    const built = createApiV2CollectionAdapter<Instrument>({
      config,
      documentSchema,
      metadata: withHop,
      runtimeFields: [{ namespace: "customFields", definitions: [hazardClassDefinition] }],
    });

    expect(() => apiV2CollectionRequestParams(built, state(["name", "customFields.SF104"]))).not.toThrow();
  });

  it("projects a custom field reached through a relationship and carries its raw value source", () => {
    const own = metadata.runtimeFields?.[0];
    const withHop: ApiV2CollectionMetadata<Instrument> = {
      ...metadata,
      runtimeFields: [
        {
          ...(own as NonNullable<typeof own>),
          namespace: "target.customFields",
          responseField: "target.customFields",
          columnSelectable: true,
          maximumProjections: 50,
          via: "target",
        },
      ],
    };
    const built = createApiV2CollectionAdapter<Instrument>({
      config,
      documentSchema,
      metadata: withHop,
      runtimeFields: [{ namespace: "target.customFields", definitions: [hazardClassDefinition] }],
    });
    const hopped = "target.customFields.SF104";

    const parameters = apiV2CollectionRequestParams(built, state(["name", hopped]));
    expect(parameters.get("fields[instruments]")).toBe(`id,name,${hopped}`);

    const field = built.config.fields.find((candidate) => String(candidate.name) === hopped);
    const list = field && typeof field.list === "object" ? field.list : undefined;
    if (!field || !list?.valueSource) throw new Error("hopped custom field is not offered as a column");
    expect(list.valueSource).toEqual({
      kind: "runtimeField",
      responseField: "target.customFields",
      id: "SF104",
      numeric: false,
    });
    expect(field.origin?.via).toEqual({ name: "target", label: { text: "target" } });
  });

  it("validates typed runtime values and carries descriptors for each projected column", () => {
    const built = adapter([hazardClassDefinition, temperature]);
    const page = built.parseResponse(
      {
        ...v2ListEnvelope([
          { id: 1, name: "Freezer", customFields: { SF104: "BSL-2", SF108: -80 } },
          { id: 2, name: "Bench", customFields: { SF104: null } },
        ]),
        limit: 20,
      },
      ["id", "name", "customFields"] as never,
    );
    expect(page.rows[0].customFields).toEqual({ SF104: "BSL-2", SF108: -80 });
    expect(page.rows[1].customFields).toEqual({ SF104: null });
    expect(built.config.fields.find((field) => String(field.name) === "customFields.SF104")?.list).toMatchObject({
      valueSource: { kind: "runtimeField", responseField: "customFields", id: "SF104", numeric: false },
    });
    expect(built.config.fields.find((field) => String(field.name) === "customFields.SF108")?.list).toMatchObject({
      valueSource: { kind: "runtimeField", responseField: "customFields", id: "SF108", numeric: true },
    });
  });

  it("offers a definition's published options as a picker instead of free text", () => {
    const hazard: RuntimeFieldDefinition = {
      ...hazardClassDefinition,
      type: "radio",
      options: ["BSL-1", "BSL-2", "BSL-3"],
    };

    const field = adapter([hazard]).config.fields.find((candidate) => String(candidate.name) === "customFields.SF104");

    expect(field?.type).toBe("select");
    expect(field?.type === "select" ? field.options : []).toEqual(["BSL-1", "BSL-2", "BSL-3"]);
  });

  it("preserves a runtime definition's wildcard support", () => {
    const built = adapter([{ ...hazardClassDefinition, supportsWildcards: true }]);
    const field = built.config.fields.find((candidate) => String(candidate.name) === "customFields.SF104");
    const withWildcard = state(["name"]);
    withWildcard.filters.expression = {
      kind: "comparison",
      field: "customFields.SF104" as never,
      operator: "equals",
      value: "BSL*",
    };

    expect(field?.capabilities.supportsWildcards).toBe(true);
    expect(apiV2CollectionRequestParams(built, withWildcard).get("where")).toBe("customFields.SF104==BSL*");
  });

  it("rejects a runtime selector that shadows published collection metadata", () => {
    const withShadow: ApiV2CollectionMetadata<Instrument> = {
      ...metadata,
      filtering: {
        ...metadata.filtering,
        selectors: {
          ...metadata.filtering.selectors,
          "customFields.SF104": { operators: ["=="], wildcards: false, fieldType: "text" },
        },
      },
    };

    expect(() =>
      createApiV2FilterFields({
        config,
        metadata: withShadow,
        runtimeFields: [{ namespace: "customFields", definitions: [hazardClassDefinition] }],
      }),
    ).toThrow("Runtime field selector shadows an existing selector: customFields.SF104");
  });

  it("leaves a collection with no runtime namespace untouched", () => {
    const staticOnly = createApiV2CollectionAdapter<Instrument>({
      config,
      documentSchema,
      metadata: { ...metadata, runtimeFields: [] },
    });

    expect(staticOnly.config.fields.map((field) => field.name)).toEqual(["id", "name"]);
    expect(staticOnly.isRuntimeSelector("customFields.SF104")).toBe(false);
    expect(apiV2CollectionRequestParams(staticOnly, state(["name"])).get("fields[instruments]")).toBe("id,name");
  });
});

it("reuses runtime filter fields without advertising catalogue value columns", () => {
  const enriched = enrichApiV2FilterConfig({
    config,
    metadata,
    runtimeFields: [{ namespace: "customFields", definitions: [hazardClassDefinition] }],
  });
  expect(enriched.defaultColumns).toEqual(config.defaultColumns);
  expect(enriched.fields.find((field) => String(field.name) === "customFields.SF104")).toMatchObject({
    list: false,
    capabilities: { filterOperators: expect.arrayContaining(["equals", "exists"]) },
  });
  expect(enriched.runtimeSources?.[0]?.columnSelectable).toBe(false);
});

it("validates sparse wire fields before mapping them to domain rows", () => {
  const schema = v.object({ id: v.string(), title: v.optional(v.string()), owner: v.string() });
  let mapped = 0;
  const adapter = createApiV2CollectionAdapter<{ id: string; title: string; owner?: string }, typeof schema>({
    config: {
      slug: "mapped",
      idField: "id",
      useAsTitle: "title",
      defaultColumns: ["title"],
      labels: { singularKey: "record", pluralKey: "records" },
      fields: [
        { name: "id", type: "text", labelKey: "id" },
        { name: "title", type: "text", labelKey: "title" },
      ],
    },
    documentSchema: schema,
    mapDocument: (document): { id: string; title: string; owner?: string } => {
      mapped++;
      return { id: document.id, title: document.title ?? "Unavailable" };
    },
    metadata: {
      resourceName: "mapped",
      fields: ["id", "title", "owner"],
      sorting: { fields: [], default: [], maximumFields: 5 },
      filtering: {
        selectors: {},
        limits: {
          maximumComparisons: 50,
          maximumLikeComparisons: 10,
          maximumNesting: 10,
          maximumArguments: 100,
          maximumWhereLength: 4096,
        },
      },
      pagination: { defaultLimit: 20, maximumLimit: 100 },
    },
  });
  expect(adapter.parseResponse({ docs: [{ id: "1", title: "One" }] }, ["id", "title"]).rows).toEqual([
    { id: "1", title: "One" },
  ]);
  expect(
    adapter.parseResponse({ items: [{ id: "3", title: "Three" }], total: 3, page: 1, pageSize: 2 }, ["id", "title"]),
  ).toMatchObject({ rows: [{ id: "3", title: "Three" }], rowCount: 3, hasNextPage: true });
  expect(() => adapter.parseResponse({ docs: [{ id: "2", title: 42 }] }, ["id", "title"])).toThrow();
  expect(mapped).toBe(2);
});
