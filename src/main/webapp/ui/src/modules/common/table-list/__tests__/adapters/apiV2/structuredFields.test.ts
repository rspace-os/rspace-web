import * as v from "valibot";
import { describe, expect, it } from "vitest";
import type { CollectionConfig } from "@/modules/common/collection/collectionConfig";
import { apiV2CollectionMetadataFromOpenApi } from "../../../adapters/apiV2/apiV2CollectionMetadata";
import { createApiV2CollectionAdapter } from "../../../adapters/apiV2/createApiV2CollectionAdapter";

type RecordDocument = {
  id: string;
  title: string;
  rows: Record<string, unknown>[];
  owners: { relationTo: string; value: number }[];
};

const config = {
  slug: "records",
  idField: "id",
  useAsTitle: "title",
  defaultColumns: ["title", "rows", "owners"],
  labels: { singularKey: "record", pluralKey: "records" },
  fields: [
    { name: "id", type: "text", labelKey: "id" },
    { name: "title", type: "text", labelKey: "title" },
    {
      name: "rows",
      type: "array",
      labelKey: "rows",
      fields: [{ name: "dayOfWeek", type: "number", labelKey: "day" }],
      form: { component: () => null },
    },
    { name: "owners", type: "relationship", labelKey: "owners", relationTo: "users", hasMany: true },
  ],
} satisfies CollectionConfig<RecordDocument>;

const documentSchema = v.object({
  id: v.string(),
  title: v.string(),
  rows: v.array(v.record(v.string(), v.unknown())),
  owners: v.array(v.object({ relationTo: v.string(), value: v.number() })),
});

function field(name: string, jsonType: string, overrides: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    name,
    property: name,
    type: { jsonType, format: null, maxLength: null, structure: {} },
    requiredOnCreate: false,
    nullable: false,
    readOnly: false,
    writeOperations: ["CREATE", "UPDATE"],
    defaultValue: null,
    writeConstraints: {},
    filterOperators: [],
    supportsWildcards: false,
    sortable: false,
    openApi: { title: null, description: null, format: null, extensions: {} },
    readAccess: {},
    createAccess: {},
    updateAccess: {},
    fields: [],
    ...overrides,
  };
}

function rowFields() {
  return [
    field("dayOfWeek", "integer", {
      requiredOnCreate: true,
      defaultValue: 1,
      writeConstraints: { minimum: 1, maximum: 7 },
      openApi: { title: "Weekday" },
    }),
    field("details", "object", {
      nullable: true,
      fields: [
        field("label", "string", {
          type: { jsonType: "string", maxLength: 64, structure: {} },
          readOnly: true,
          defaultValue: "Stored",
          writeConstraints: { pattern: "^[A-Z]", maxLength: 12 },
        }),
        field("roles", "array", {
          writeConstraints: { minItems: 0, maxItems: 3 },
          fields: [
            field("enabled", "boolean", { defaultValue: false }),
            field("role", "string", { type: { jsonType: "string", structure: { enum: ["OWNER", "BOOKER"] } } }),
            field("expiresAt", "string", { type: { jsonType: "string", format: "date-time", structure: {} } }),
          ],
        }),
      ],
    }),
  ];
}

function openApi(children: unknown = rowFields()) {
  const properties: Record<string, Record<string, unknown>> = {
    rows: {
      type: ["array", "null"],
      readOnly: true,
      minItems: 1,
      maxItems: 7,
      items: { $ref: "#/components/schemas/RoleSource" },
      "x-rspace-fields": children,
    },
    owners: { type: "array", items: { oneOf: [] } },
  };
  return {
    components: {
      schemas: {
        CatalogEntryRead: { type: "object", properties },
        CatalogEntryCreate: { properties: { rows: { type: "array", "x-rspace-fields": [] } } },
        CatalogEntryUpdate: { properties: { rows: { type: "array", "x-rspace-fields": [] } } },
      },
    },
    paths: {
      "/api/v2/records": {
        get: {
          parameters: [
            { name: "sort", "x-rspace-sort": { fields: ["title"], default: [], maximumFields: 5 } },
            {
              name: "where",
              schema: { maxLength: 4096 },
              "x-rspace-filter": {
                selectors: { title: { schema: { type: "string" }, operators: ["=contains="], wildcards: false } },
                maximumComparisons: 50,
                maximumLikeComparisons: 10,
                maximumNesting: 10,
                maximumArguments: 100,
              },
            },
            { name: "limit", schema: { default: 20, maximum: 100 } },
            { name: "fields", "x-rspace-allowed-fields": { records: ["id", "title", "rows", "owners"] } },
          ],
          responses: {
            "200": {
              content: {
                "application/json": {
                  schema: { properties: { docs: { items: { $ref: "#/components/schemas/CatalogEntryRead" } } } },
                },
              },
            },
          },
        },
      },
    },
  };
}

function adapter(document = openApi()) {
  return createApiV2CollectionAdapter({
    config,
    documentSchema,
    metadata: apiV2CollectionMetadataFromOpenApi<RecordDocument>(document, "records"),
  });
}

describe("declared OpenAPI fields", () => {
  it("resolves recursive Read metadata beside a named item reference for the custom editor", () => {
    const resolved = adapter();
    const rows = resolved.config.fields.find((entry) => entry.name === "rows");

    expect(rows).toMatchObject({
      type: "array",
      form: config.fields[2].form,
      nullable: true,
      readOnly: true,
      minRows: 1,
      maxRows: 7,
      capabilities: { sortable: false, filterOperators: [], supportsWildcards: false },
      fields: [
        {
          name: "dayOfWeek",
          type: "number",
          label: "Weekday",
          required: true,
          defaultValue: 1,
          writeConstraints: { minimum: 1, maximum: 7 },
        },
        {
          name: "details",
          type: "object",
          nullable: true,
          fields: [
            {
              name: "label",
              type: "text",
              maximumLength: 12,
              readOnly: true,
              defaultValue: "Stored",
              writeConstraints: { pattern: "^[A-Z]", maxLength: 12 },
            },
            {
              name: "roles",
              type: "array",
              minRows: 0,
              maxRows: 3,
              fields: [
                { name: "enabled", type: "boolean", defaultValue: false },
                { name: "role", type: "select", options: ["OWNER", "BOOKER"] },
                { name: "expiresAt", type: "dateTime" },
              ],
            },
          ],
        },
      ],
    });
    expect(resolved.config.fields.find((entry) => entry.name === "owners")).toMatchObject({
      type: "relationship",
      hasMany: true,
    });
    const state = {
      filters: { search: "", expression: null },
      sorting: [],
      page: { pageIndex: 0, pageSize: 20 },
      visibleFields: ["rows", "owners"] as const,
    };
    expect(resolved.toSearchParams(state).get("fields[records]")).toBe("id,title,rows,owners");
    expect(() => resolved.toSearchParams({ ...state, sorting: [{ field: "rows", direction: "asc" }] })).toThrow();
    expect(() =>
      resolved.toSearchParams({
        ...state,
        filters: { search: "", expression: { kind: "comparison", field: "rows", operator: "equals", value: "one" } },
      }),
    ).toThrow();
  });

  it("rejects configured structured fields with a missing extension or incompatible type", () => {
    const missing = openApi();
    delete missing.components.schemas.CatalogEntryRead.properties.rows["x-rspace-fields"];
    expect(() => adapter(missing)).toThrow("must declare x-rspace-fields");

    const mismatch = openApi();
    mismatch.components.schemas.CatalogEntryRead.properties.rows.type = "object";
    expect(() => adapter(mismatch)).toThrow("has a different structured type");
  });

  it("rejects invalid parent definitions and unresolved Read references", () => {
    const scalar = openApi();
    scalar.components.schemas.CatalogEntryRead.properties.rows.type = "string";
    expect(() => adapter(scalar)).toThrow("x-rspace-fields on a scalar field");

    const union = openApi();
    union.components.schemas.CatalogEntryRead.properties.rows.type = ["array", "object", "null"];
    expect(() => adapter(union)).toThrow("must declare one structured type");

    const missingReference = openApi();
    missingReference.paths["/api/v2/records"].get.responses["200"].content[
      "application/json"
    ].schema.properties.docs.items.$ref = "#/components/schemas/MissingRead";
    expect(() => adapter(missingReference)).toThrow("Read component must be an object");
  });

  it("rejects cyclic metadata", () => {
    const cyclic = field("nested", "object");
    cyclic.fields = [cyclic];
    expect(() => adapter(openApi([cyclic]))).toThrow("cyclic child fields");
  });

  it.each<[string, unknown, string]>([
    ["empty children", [], "must declare non-empty x-rspace-fields"],
    ["duplicate children", [field("name", "string"), field("name", "string")], "duplicate or empty child"],
    ["missing nested children", [field("nested", "array")], "must declare non-empty x-rspace-fields"],
    ["invalid flag", [field("name", "string", { nullable: "yes" })], "nullable for rows.name must be a boolean"],
    [
      "scalar children",
      [field("name", "string", { fields: [field("other", "string")] })],
      "cannot declare child fields",
    ],
    ["unknown child type", [field("name", "relationship")], "unsupported field type"],
    [
      "invalid structure",
      [field("name", "string", { type: { jsonType: "string", structure: "OBJECT" } })],
      "structure for rows.name must be an object",
    ],
    [
      "invalid constraints",
      [field("name", "string", { writeConstraints: [] })],
      "write constraints for rows.name must be an object",
    ],
    [
      "invalid row bounds",
      [field("nested", "array", { fields: [field("name", "string")], writeConstraints: { minItems: 3, maxItems: 1 } })],
      "invalid row bounds",
    ],
  ])("rejects %s", (_name, children, error) => {
    expect(() => adapter(openApi(children))).toThrow(error);
  });
});
