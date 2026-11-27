import { Temporal } from "@js-temporal/polyfill";
import * as v from "valibot";
import { describe, expect, it } from "vitest";
import { v2ListEnvelope } from "@/__tests__/helpers/v2ListEnvelope";
import type { InstrumentsRead } from "@/modules/common/api/v2/generated/models/instrumentsRead";
import { metadataFixture } from "../../__tests__/fixtures/apiV2Metadata";
import { runtimeDocumentSchema as documentSchema } from "../../__tests__/fixtures/schema";
import { hazardClassDefinition } from "../../__tests__/fixtures/tableList";
import type { CollectionQueryState } from "../../types";
import { RuntimeFieldProjectionLimitError } from "../../utils/errors";
import { createApiV2CollectionAdapter } from "../adapter";
import { createApiV2FilterFields, enrichApiV2FilterConfig } from "../filterFields";
import type { ApiV2CollectionMetadata } from "../metadata";
import { apiV2CollectionRequestParams } from "../requestParams";
import type { RuntimeFieldDefinition } from "../runtimeFieldCatalog";

type Instrument = Required<Pick<InstrumentsRead, "id" | "name">> & {
  customFields?: Record<string, string | number | readonly string[] | null>;
};

const config = {
  slug: "instruments",
  idField: "id" as const,
  labels: { singularKey: "tableListExamples:record", pluralKey: "tableListExamples:records" } as const,
  useAsTitle: "name" as const,
  defaultColumns: ["name" as const],
  fields: [
    {
      name: "id" as const,
      labelKey: "tableListExamples:fields.id" as const,
      type: "number" as const,
      list: false as const,
    },
    { name: "name" as const, labelKey: "tableListExamples:fields.title" as const, type: "text" as const },
  ],
};

const metadata = metadataFixture<Instrument>({
  resourceName: "instruments",
  fields: ["id", "name"],
  sorting: { fields: ["name"], default: [{ field: "name", direction: "asc" }], maximumFields: 5 },
  filtering: {
    selectors: { name: { operators: ["==", "=contains="], wildcards: true, fieldType: "text" } },
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
});

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

  const dateDefinition: RuntimeFieldDefinition = {
    ...hazardClassDefinition,
    id: "SF109",
    selector: "customFields.SF109",
    type: "date",
    operators: ["==", "=ge=", "=le=", "=exists="],
  };
  const timeDefinition: RuntimeFieldDefinition = {
    ...hazardClassDefinition,
    id: "SF110",
    selector: "customFields.SF110",
    type: "time",
    operators: ["==", "=ge=", "=le=", "=exists="],
  };
  const choiceDefinition: RuntimeFieldDefinition = {
    ...hazardClassDefinition,
    id: "SF111",
    selector: "customFields.SF111",
    type: "choice",
    jsonType: "array",
    options: ["Brightfield", "Fluorescence"],
    operators: ["=contains=", "=in=", "=exists="],
  };
  const radioDefinition: RuntimeFieldDefinition = {
    ...hazardClassDefinition,
    type: "radio",
    options: ["BSL-1", "BSL-2", "BSL-3"],
  };

  it.each<{
    definition: RuntimeFieldDefinition;
    fieldType: string;
    valueType: string;
    options?: readonly string[];
    operators?: readonly string[];
    excludedOperators?: readonly string[];
  }>([
    { definition: hazardClassDefinition, fieldType: "text", valueType: "text", operators: ["contains"] },
    {
      definition: temperature,
      fieldType: "number",
      valueType: "number",
      operators: ["greaterThanOrEqual"],
      excludedOperators: ["contains"],
    },
    { definition: dateDefinition, fieldType: "text", valueType: "date" },
    { definition: timeDefinition, fieldType: "text", valueType: "time" },
    { definition: choiceDefinition, fieldType: "select", valueType: "choice" },
    { definition: radioDefinition, fieldType: "select", valueType: "radio", options: ["BSL-1", "BSL-2", "BSL-3"] },
  ])(
    "derives a filterable, optional $fieldType column from a $valueType definition",
    ({ definition, fieldType, valueType, options, operators = [], excludedOperators = [] }) => {
      const field = adapter([definition]).config.fields.find(
        (candidate) => String(candidate.name) === definition.selector,
      );

      expect(field?.type).toBe(fieldType);
      expect(field?.origin?.runtimeValueType).toBe(valueType);
      expect(field?.form).toBe(false);
      expect(field?.capabilities.sortable).toBe(false);
      for (const operator of operators) expect(field?.capabilities.filterOperators).toContain(operator);
      for (const operator of excludedOperators) expect(field?.capabilities.filterOperators).not.toContain(operator);
      if (options) expect(field?.type === "select" ? field.options : []).toEqual(options);
    },
  );

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
    expect(fields.map((field) => field.origin?.sourceLabel)).toEqual(["Cell line template", "Bacterial template"]);
  });

  it("projects only the visible custom columns, with no predicate or expansion for them", () => {
    const built = adapter([hazardClassDefinition, temperature]);
    const visible = state(["name", "customFields.SF104"]);
    const parameters = apiV2CollectionRequestParams(built, visible);

    expect(parameters.fields?.instruments).toBe("id,name,customFields.SF104");
    expect(parameters.where).toBeUndefined();
    expect(built.requiredDepth(visible)).toBe(0);
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

    expect(parameters.fields?.instruments).toBe("id,name");
    expect(parameters.where).toBe("customFields.SF104==BSL-2");
  });

  const ownNamespace = metadata.runtimeFields?.[0];
  const filterOnlyHop: ApiV2CollectionMetadata<Instrument> = {
    ...metadata,
    runtimeFields: [
      ...(metadata.runtimeFields ?? []),
      {
        ...(ownNamespace as NonNullable<typeof ownNamespace>),
        namespace: "target.customFields",
        responseField: "",
        columnSelectable: false,
        maximumProjections: 0,
        via: "target",
      },
    ],
  };

  it.each<[string, ApiV2CollectionMetadata<Instrument>, RuntimeFieldDefinition[], string[], number | undefined]>([
    [
      "refuses more custom columns than the server accepts",
      metadata,
      [hazardClassDefinition, temperature, { ...hazardClassDefinition, id: "SF9", selector: "customFields.SF9" }],
      ["customFields.SF104", "customFields.SF108", "customFields.SF9"],
      2,
    ],
    [
      "counts the column limit per namespace, not across all of them",
      filterOnlyHop,
      [hazardClassDefinition],
      ["name", "customFields.SF104"],
      undefined,
    ],
  ])("%s", (_label, collectionMetadata, definitions, visibleFields, limit) => {
    const built = createApiV2CollectionAdapter<Instrument>({
      config,
      documentSchema,
      metadata: collectionMetadata,
      runtimeFields: [{ namespace: "customFields", definitions }],
    });
    let error: unknown;
    try {
      apiV2CollectionRequestParams(built, state(visibleFields));
    } catch (caught) {
      error = caught;
    }

    if (limit === undefined) {
      expect(error).toBeUndefined();
    } else {
      expect(error).toBeInstanceOf(RuntimeFieldProjectionLimitError);
      expect(error).toMatchObject({ limit });
    }
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
    expect(parameters.fields?.instruments).toBe(`id,name,${hopped}`);

    const field = built.config.fields.find((candidate) => String(candidate.name) === hopped);
    const list = field && typeof field.list === "object" ? field.list : undefined;
    if (!field || !list?.valueSource) throw new Error("hopped custom field is not offered as a column");
    expect(list.valueSource).toEqual({
      kind: "runtimeField",
      responseField: "target.customFields",
      id: "SF104",
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
    expect(
      ["SF104", "SF108"].map((id) => built.config.fields.find((field) => String(field.name) === `customFields.${id}`)),
    ).toMatchObject([
      { list: { valueSource: { kind: "runtimeField", responseField: "customFields", id: "SF104" } } },
      { list: { valueSource: { kind: "runtimeField", responseField: "customFields", id: "SF108" } } },
    ]);
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
    expect(apiV2CollectionRequestParams(built, withWildcard).where).toBe("customFields.SF104==BSL*");
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
    expect(apiV2CollectionRequestParams(staticOnly, state(["name"])).fields?.instruments).toBe("id,name");
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
      labels: { singularKey: "tableListExamples:record", pluralKey: "tableListExamples:records" },
      fields: [
        { name: "id", type: "text", labelKey: "tableListExamples:fields.id" },
        { name: "title", type: "text", labelKey: "tableListExamples:fields.title" },
      ],
    },
    documentSchema: schema,
    mapDocument: (document): { id: string; title: string; owner?: string } => {
      mapped++;
      return { id: document.id, title: document.title ?? "Unavailable" };
    },
    metadata: metadataFixture({ resourceName: "mapped", fields: ["id", "title", "owner"] }),
  });
  expect(adapter.parseResponse({ docs: [{ id: "1", title: "One" }] }, ["id", "title"]).rows).toEqual([
    { id: "1", title: "One" },
  ]);
  expect(() => adapter.parseResponse({ docs: [{ id: "2", title: 42 }] }, ["id", "title"])).toThrow();
  // Only decoded instants are turned back into text before parsing; a Date stays a wrong type.
  expect(() => adapter.parseResponse({ docs: [{ id: "3", title: new Date(0) }] }, ["id", "title"])).toThrow();
  expect(mapped).toBe(1);
});

it("re-parses a decoded date-time and an expanded relationship with the generated date-time pipe", () => {
  const schema = v.object({
    id: v.string(),
    start: v.pipe(
      v.string(),
      v.isoTimestamp(),
      v.transform((value) => Temporal.Instant.from(value)),
    ),
    owner: v.object({ id: v.number(), name: v.string() }),
  });
  type Row = v.InferOutput<typeof schema>;
  const mapped: Row[] = [];
  const adapter = createApiV2CollectionAdapter<Row, typeof schema>({
    config: {
      slug: "mapped",
      idField: "id",
      useAsTitle: "id",
      defaultColumns: ["start"],
      labels: { singularKey: "tableListExamples:record", pluralKey: "tableListExamples:records" },
      fields: [
        { name: "id", type: "text", labelKey: "tableListExamples:fields.id" },
        { name: "start", type: "dateTime", labelKey: "tableListExamples:fields.title" },
      ],
    },
    documentSchema: schema,
    mapDocument: (document): Row => {
      mapped.push(document);
      return document;
    },
    metadata: metadataFixture({ resourceName: "mapped", fields: ["id", "start", "owner"] }),
  });
  const start = Temporal.Instant.from("2026-01-01T00:00:00.123456789Z");
  const expected = { id: "1", start, owner: { id: 7, name: "Ada" } };

  const { rows } = adapter.parseResponse(
    { docs: [{ id: "1", start, owner: { id: 7, name: "Ada", extension: "undeclared" } }] },
    ["id", "start", "owner"],
  );

  expect(mapped).toEqual([expected]);
  expect(rows).toEqual([expected]);
  expect(rows[0].owner).not.toHaveProperty("extension");
});
