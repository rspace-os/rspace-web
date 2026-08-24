import * as v from "valibot";
import { describe, expect, it } from "vitest";
import type { ApiV2CollectionMetadata } from "../../../adapters/apiV2/apiV2CollectionMetadata";
import { createApiV2CollectionAdapter, staleRuntimeFields } from "../../../adapters/apiV2/createApiV2CollectionAdapter";
import { apiV2CollectionRequestParams } from "../../../adapters/apiV2/createApiV2CollectionFetcher";
import type { RuntimeFieldDefinition } from "../../../adapters/apiV2/runtimeFieldCatalog";
import type { CollectionQueryState } from "../../../tableListState";

type Instrument = {
  id: number;
  name: string;
  customFields?: Record<string, string | number | readonly string[] | null>;
};

const documentSchema = v.object({ id: v.number(), name: v.string() });

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
      catalog: "/api/v2/instruments/custom-fields",
      responseField: "customFields",
      filterable: true,
      columnSelectable: true,
      sortable: false,
      maximumProjections: 2,
      catalogDefaultLimit: 50,
      catalogMaximumLimit: 200,
      catalogMaximumIds: 50,
      via: "",
      viaResource: "",
    },
  ],
  pagination: { defaultLimit: 20, maximumLimit: 100 },
};

function definition(overrides: Partial<RuntimeFieldDefinition> = {}): RuntimeFieldDefinition {
  return {
    id: "SF104",
    selector: "customFields.SF104",
    label: "Hazard class",
    type: "text",
    jsonType: "string",
    operators: ["==", "!=", "=in=", "=out=", "=contains=", "=like=", "=exists="],
    supportsWildcards: false,
    columnSelectable: true,
    sortable: false,
    source: { id: "IT9", label: "Cell line template" },
    options: [],
    ...overrides,
  };
}

const temperature = definition({
  id: "SF108",
  selector: "customFields.SF108",
  label: "Preservation temperature",
  type: "number",
  jsonType: "number",
  operators: ["==", "=ge=", "=le=", "=exists="],
  source: { id: "IT9", label: "Cell line template" },
});

function adapter(definitions: readonly RuntimeFieldDefinition[]) {
  return createApiV2CollectionAdapter<Instrument>({
    config,
    documentSchema,
    metadata,
    runtimeFields: [{ namespace: "customFields", definitions }],
    translate: (key, values) => (values ? `${key}:${JSON.stringify(values)}` : key),
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
        viaLabel: "",
        catalog: "/api/v2/instruments/custom-fields",
        maximumLimit: 200,
        filterable: true,
        columnSelectable: true,
      },
    ]);
  });

  it("derives one filterable, optional column per catalog definition", () => {
    const field = adapter([definition()]).config.fields.find(
      (candidate) => String(candidate.name) === "customFields.SF104",
    );

    expect(field?.type).toBe("text");
    expect(field?.form).toBe(false);
    expect(field?.capabilities.filterOperators).toContain("contains");
    expect(field?.capabilities.sortable).toBe(false);
  });

  it("carries the definition source so duplicate labels stay distinguishable", () => {
    const other = definition({
      id: "SF204",
      selector: "customFields.SF204",
      source: { id: "IT11", label: "Bacterial template" },
    });
    const fields = adapter([definition(), other]).config.fields.filter(
      (candidate) => candidate.origin?.kind === "runtimeField",
    );

    expect(fields.map((field) => field.label)).toEqual(["Hazard class", "Hazard class"]);
    expect(fields.map((field) => field.labelKey)).toEqual(["customFields.SF104", "customFields.SF204"]);
    expect(fields.map((field) => field.origin?.sourceLabel)).toEqual(["Cell line template", "Bacterial template"]);
    expect(fields.map((field) => field.origin?.stableId)).toEqual(["SF104", "SF204"]);
  });

  it("maps a numeric definition to a number field", () => {
    const field = adapter([temperature]).config.fields.find(
      (candidate) => String(candidate.name) === "customFields.SF108",
    );

    expect(field?.type).toBe("number");
    expect(field?.capabilities.filterOperators).toContain("greaterThanOrEqual");
    expect(field?.capabilities.filterOperators).not.toContain("contains");
  });

  it("projects only the visible custom columns", () => {
    const parameters = apiV2CollectionRequestParams(
      adapter([definition(), temperature]),
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
    const parameters = apiV2CollectionRequestParams(adapter([definition()]), filtered);

    expect(parameters.get("fields[instruments]")).toBe("id,name");
    expect(parameters.get("where")).toBe("customFields.SF104==BSL-2");
  });

  it("does not add a predicate for a custom column with no filter", () => {
    const parameters = apiV2CollectionRequestParams(adapter([definition()]), state(["name", "customFields.SF104"]));

    expect(parameters.get("where")).toBeNull();
  });

  it("does not raise the relationship expansion depth for a custom column", () => {
    expect(adapter([definition()]).requiredDepth(state(["name", "customFields.SF104"]))).toBe(0);
  });

  it("refuses more custom columns than the server accepts", () => {
    expect(() =>
      apiV2CollectionRequestParams(
        adapter([definition(), temperature, definition({ id: "SF9", selector: "customFields.SF9" })]),
        state(["customFields.SF104", "customFields.SF108", "customFields.SF9"]),
      ),
    ).toThrow('tableList.error.customFieldColumnLimit:{"limit":2}');
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
          viaResource: "instruments",
        },
      ],
    };
    const built = createApiV2CollectionAdapter<Instrument>({
      config,
      documentSchema,
      metadata: withHop,
      runtimeFields: [{ namespace: "customFields", definitions: [definition()] }],
    });

    expect(() => apiV2CollectionRequestParams(built, state(["name", "customFields.SF104"]))).not.toThrow();
  });

  it("projects a custom field reached through a relationship, and renders it from its own key", () => {
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
          viaResource: "instruments",
        },
      ],
    };
    const built = createApiV2CollectionAdapter<Instrument>({
      config,
      documentSchema,
      metadata: withHop,
      runtimeFields: [{ namespace: "target.customFields", definitions: [definition()] }],
    });
    const hopped = "target.customFields.SF104";

    const parameters = apiV2CollectionRequestParams(built, state(["name", hopped]));
    expect(parameters.get("fields[instruments]")).toBe(`id,name,${hopped}`);

    const field = built.config.fields.find((candidate) => String(candidate.name) === hopped);
    const list = field && typeof field.list === "object" ? field.list : undefined;
    if (!field || !list?.renderCell) throw new Error("hopped custom field is not offered as a column");
    const row = { id: 1, name: "Confocal", "target.customFields": { SF104: "BSL-2" } } as unknown as Instrument;
    expect(list.renderCell({ config: built.config, field, row, value: undefined as never })).toBe("BSL-2");
  });

  it("validates typed values and renders an empty marker for a missing one", () => {
    const built = adapter([definition(), temperature]);
    const page = built.parseResponse(
      {
        docs: [
          { id: 1, name: "Freezer", customFields: { SF104: "BSL-2", SF108: -80 } },
          { id: 2, name: "Bench", customFields: { SF104: null } },
        ],
        totalDocs: 2,
        limit: 20,
        page: 1,
        totalPages: 1,
        pagingCounter: 1,
        hasPrevPage: false,
        hasNextPage: false,
        prevPage: null,
        nextPage: null,
      },
      ["id", "name", "customFields"] as never,
    );
    const render = (row: Instrument, name: string) => {
      const field = built.config.fields.find((candidate) => candidate.name === name);
      const list = field && typeof field.list === "object" ? field.list : undefined;
      if (!field || !list?.renderCell) throw new Error(`no cell renderer for ${name}`);
      return list.renderCell({ config: built.config, field, row, value: undefined as never });
    };

    expect(render(page.rows[0], "customFields.SF104")).toBe("BSL-2");
    expect(render(page.rows[0], "customFields.SF108")).toBe("-80");
    expect(render(page.rows[1], "customFields.SF104")).toBe("");
    expect(render(page.rows[1], "customFields.SF108")).toBe("");
  });

  it("offers a definition's published options as a picker instead of free text", () => {
    const hazard = definition({ type: "radio", options: ["BSL-1", "BSL-2", "BSL-3"] });

    const field = adapter([hazard]).config.fields.find((candidate) => String(candidate.name) === "customFields.SF104");

    expect(field?.type).toBe("select");
    expect(field?.type === "select" ? field.options : []).toEqual(["BSL-1", "BSL-2", "BSL-3"]);
  });

  it("leaves a definition with no published options as free text", () => {
    const field = adapter([definition()]).config.fields.find(
      (candidate) => String(candidate.name) === "customFields.SF104",
    );

    expect(field?.type).toBe("text");
  });

  it("preserves a runtime definition's wildcard support", () => {
    const built = adapter([definition({ supportsWildcards: true })]);
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

  it("reports only the stale runtime names of a saved view", () => {
    const built = adapter([definition()]);

    expect(staleRuntimeFields(["name", "customFields.SF104", "customFields.SF999"], built)).toEqual([
      "customFields.SF999",
    ]);
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
