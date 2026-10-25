import { describe, expect, it } from "vitest";
import { apiV2CollectionMetadataFromOpenApi } from "../../../adapters/apiV2/apiV2CollectionMetadata";
import type { TestRecord } from "../../fixtures/tableListFixtures";

const openApi = {
  components: { schemas: { RecordRead: { properties: {} } } },
  paths: {
    "/api/v2/records": {
      get: {
        parameters: [
          {
            name: "sort",
            "x-rspace-sort": {
              fields: ["title", "modifiedAt"],
              default: ["-modifiedAt"],
              maximumFields: 5,
            },
          },
          {
            name: "where",
            schema: { type: "string", maxLength: 4096 },
            "x-rspace-filter": {
              maximumComparisons: 50,
              maximumLikeComparisons: 10,
              maximumNesting: 10,
              maximumArguments: 100,
              selectors: {
                title: {
                  schema: { type: "string" },
                  operators: ["==", "=contains="],
                  wildcards: true,
                },
                "target.id": {
                  schema: { type: "integer", format: "int64" },
                  operators: ["=="],
                  wildcards: false,
                },
                "target.deleted": {
                  schema: { type: "boolean" },
                  operators: ["=="],
                  wildcards: false,
                },
                "target.updatedAt": {
                  schema: { type: "string", format: "date-time" },
                  operators: ["=="],
                  wildcards: false,
                },
                "target.owner": {
                  schema: { type: "object" },
                  operators: ["=="],
                  wildcards: false,
                },
              },
            },
          },
          { name: "limit", schema: { type: "integer", default: 20, maximum: 100 } },
          {
            name: "fields",
            "x-rspace-allowed-fields": { records: ["id", "title", "modifiedAt"] },
          },
        ],
        responses: {
          "200": {
            content: {
              "application/json": {
                schema: { properties: { docs: { items: { $ref: "#/components/schemas/RecordRead" } } } },
              },
            },
          },
        },
      },
    },
  },
};

function whereParameter(document: typeof openApi) {
  const parameter = document.paths["/api/v2/records"].get.parameters.find((parameter) => parameter.name === "where");
  if (!parameter) throw new Error("Fixture has no where parameter");
  return parameter as Record<string, unknown>;
}

function runtimeNamespace(): Record<string, unknown> {
  return {
    namespace: "customFields",
    catalog: "/api/v2/instruments/fields/customFields",
    responseField: "customFields",
    filterable: true,
    columnSelectable: true,
    sortable: false,
    maximumProjections: 50,
    catalogDefaultLimit: 50,
    catalogMaximumLimit: 200,
    catalogMaximumIds: 100,
    via: "",
    viaResource: "",
  };
}

function selectors(document: typeof openApi) {
  return (whereParameter(document)["x-rspace-filter"] as { selectors: Record<string, Record<string, unknown>> })
    .selectors;
}

describe("apiV2CollectionMetadataFromOpenApi", () => {
  it("names a resource whose list operation is missing", () => {
    expect(() => apiV2CollectionMetadataFromOpenApi(openApi, "missing")).toThrow("missing list operation");
  });

  it.each([
    ["sort", "x-rspace-sort", "sort metadata"],
    ["limit", "schema", "limit schema"],
  ])("identifies the %s parameter when its default is invalid", (name, entry, role) => {
    const document = structuredClone(openApi);
    const parameter = document.paths["/api/v2/records"].get.parameters.find((parameter) => parameter.name === name);
    if (!parameter) throw new Error(`Fixture has no ${name} parameter`);
    const descriptor = (parameter as Record<string, unknown>)[entry] as Record<string, unknown>;
    descriptor.default = null;
    expect(() => apiV2CollectionMetadataFromOpenApi(document, "records")).toThrow(role);
  });

  it.each([undefined, null, "true", 1])("rejects the selector flag %s instead of coercing it", (wildcards) => {
    const document = structuredClone(openApi);
    selectors(document).title.wildcards = wildcards;
    expect(() => apiV2CollectionMetadataFromOpenApi(document, "records")).toThrow("title.wildcards");
  });

  it.each([0, -1, 1.5, Number.POSITIVE_INFINITY, Number.NaN])("rejects invalid query limits: %s", (limit) => {
    const document = structuredClone(openApi);
    (whereParameter(document)["x-rspace-filter"] as Record<string, unknown>).maximumComparisons = limit;
    expect(() => apiV2CollectionMetadataFromOpenApi(document, "records")).toThrow("maximumComparisons");
  });

  it.each([
    "filterable",
    "columnSelectable",
    "sortable",
    "via",
    "viaResource",
    "catalogDefaultLimit",
    "catalogMaximumLimit",
    "catalogMaximumIds",
  ])("requires the runtime descriptor's %s", (field) => {
    const document = structuredClone(openApi);
    const namespace = runtimeNamespace();
    delete namespace[field];
    whereParameter(document)["x-rspace-runtime-fields"] = [namespace];
    expect(() => apiV2CollectionMetadataFromOpenApi(document, "records")).toThrow(`0.${field}`);
  });

  it("rejects malformed runtime flags and catalog limits", () => {
    const document = structuredClone(openApi);
    const namespace = runtimeNamespace();
    whereParameter(document)["x-rspace-runtime-fields"] = [namespace];
    namespace.filterable = "true";
    expect(() => apiV2CollectionMetadataFromOpenApi(document, "records")).toThrow("0.filterable");
    namespace.filterable = true;
    namespace.catalogMaximumIds = Number.POSITIVE_INFINITY;
    expect(() => apiV2CollectionMetadataFromOpenApi(document, "records")).toThrow("0.catalogMaximumIds");
  });

  it.each(["x-rspace-runtime-fields", "x-rspace-relationship-fields"])("rejects null for %s", (extension) => {
    const document = structuredClone(openApi);
    whereParameter(document)[extension] = null;
    expect(() => apiV2CollectionMetadataFromOpenApi(document, "records")).toThrow("Validation failed");
  });

  it.each([
    [{ oneOf: [{ type: "integer" }, { type: "number" }] }, "number"],
    [{ oneOf: [{ type: "integer" }, { type: "string" }] }, "text"],
  ])("reads the current polymorphic ID shape %j", (schema, expectedType) => {
    const document = structuredClone(openApi);
    selectors(document).title.schema = schema;
    expect(apiV2CollectionMetadataFromOpenApi(document, "records").filtering.selectors.title?.fieldType).toBe(
      expectedType,
    );
  });

  it.each([
    {},
    { type: "file" },
    { type: ["string", "number"] },
    { oneOf: [] },
    { oneOf: [{ type: ["string", "null"] }] },
    { oneOf: [{ type: "object" }] },
    { anyOf: [{ type: "string" }] },
    { allOf: [{ type: "string" }] },
    { $ref: "#/components/schemas/Name" },
    { type: "string", oneOf: [{ type: "string" }] },
  ])("rejects unsupported selector schema %j", (schema) => {
    const document = structuredClone(openApi);
    selectors(document).title.schema = schema;
    expect(() => apiV2CollectionMetadataFromOpenApi(document, "records")).toThrow("title.schema");
  });

  it("requires a local list item reference instead of an inline schema", () => {
    const document = structuredClone(openApi);
    const items =
      document.paths["/api/v2/records"].get.responses["200"].content["application/json"].schema.properties.docs.items;
    items.$ref = "https://example.com/schema.json";
    expect(() => apiV2CollectionMetadataFromOpenApi(document, "records")).toThrow("items.$ref");
    Reflect.deleteProperty(items, "$ref");
    Object.assign(items, { properties: {} });
    expect(() => apiV2CollectionMetadataFromOpenApi(document, "records")).toThrow("items.$ref");
  });
  it("reads a nullable field's type from its union with null", () => {
    const withRelationship = structuredClone(openApi) as typeof openApi;
    const where = (withRelationship.paths["/api/v2/records"].get.parameters as Record<string, unknown>[]).find(
      (parameter) => parameter.name === "where",
    ) as Record<string, unknown>;
    where["x-rspace-relationship-fields"] = {
      "owner.lastLogin": {
        schema: { type: ["string", "null"], format: "date-time" },
        operators: ["=="],
        wildcards: false,
        viaTitle: "Owner",
      },
      "owner.rank": { schema: { type: ["integer", "null"] }, operators: [], wildcards: false },
    };

    const metadata = apiV2CollectionMetadataFromOpenApi<TestRecord>(withRelationship, "records");

    expect(metadata.relationshipFields?.["owner.lastLogin"].fieldType).toBe("dateTime");
    expect(metadata.relationshipFields?.["owner.lastLogin"].viaTitle).toBe("Owner");
    expect(metadata.relationshipFields?.["owner.rank"].fieldType).toBe("number");
  });

  it("reads picker metadata and the current relationship wire selectors", () => {
    const withPicker = structuredClone(openApi) as typeof openApi;
    const where = (withPicker.paths["/api/v2/records"].get.parameters as Record<string, unknown>[]).find(
      (parameter) => parameter.name === "where",
    ) as Record<string, unknown>;
    const filter = where["x-rspace-filter"] as Record<string, unknown>;
    const selectors = filter.selectors as Record<string, Record<string, unknown>>;
    selectors.target = {
      schema: { type: "string" },
      operators: ["==", "!=", "=in=", "=out=", "=exists="],
      wildcards: false,
      picker: { resource: "instruments", identity: "globalId", globalIdPrefix: "IN" },
    };
    selectors["target.value"] = { schema: { type: "integer" }, operators: ["=="], wildcards: false };
    selectors["target.relationTo"] = { schema: { type: "string" }, operators: ["=="], wildcards: false };
    selectors["createdBy.value"] = { schema: { type: "string" }, operators: ["=="], wildcards: false };

    const metadata = apiV2CollectionMetadataFromOpenApi<TestRecord>(withPicker, "records");

    expect(metadata.filtering.selectors.target?.picker).toEqual({
      resource: "instruments",
      identity: "globalId",
      globalIdPrefix: "IN",
    });
    expect(metadata.filtering.selectors["target.value"]?.fieldType).toBe("number");
    expect(metadata.filtering.selectors["target.relationTo"]?.fieldType).toBe("text");
    expect(metadata.filtering.selectors["createdBy.value"]?.picker).toBeUndefined();
  });

  it("rejects malformed picker descriptors and unknown operators", () => {
    const malformedPicker = structuredClone(openApi) as typeof openApi;
    const malformedWhere = (malformedPicker.paths["/api/v2/records"].get.parameters as Record<string, unknown>[]).find(
      (parameter) => parameter.name === "where",
    ) as Record<string, unknown>;
    const malformedFilter = malformedWhere["x-rspace-filter"] as Record<string, unknown>;
    (malformedFilter.selectors as Record<string, Record<string, unknown>>).target = {
      schema: { type: "string" },
      operators: ["=="],
      picker: { resource: "https://evil.example", identity: "globalId", globalIdPrefix: "IN" },
    };

    expect(() => apiV2CollectionMetadataFromOpenApi<TestRecord>(malformedPicker, "records")).toThrow("target.picker");

    const unknownOperator = structuredClone(openApi) as typeof openApi;
    const unknownWhere = (unknownOperator.paths["/api/v2/records"].get.parameters as Record<string, unknown>[]).find(
      (parameter) => parameter.name === "where",
    ) as Record<string, unknown>;
    const unknownFilter = unknownWhere["x-rspace-filter"] as Record<string, unknown>;
    (unknownFilter.selectors as Record<string, Record<string, unknown>>).title = {
      schema: { type: "string" },
      operators: ["=regex="],
    };

    expect(() => apiV2CollectionMetadataFromOpenApi<TestRecord>(unknownOperator, "records")).toThrow("title.operators");
  });

  it("rejects metadata selectors beyond one relationship hop", () => {
    const tooDeep = structuredClone(openApi) as typeof openApi;
    const where = (tooDeep.paths["/api/v2/records"].get.parameters as Record<string, unknown>[]).find(
      (parameter) => parameter.name === "where",
    ) as Record<string, unknown>;
    const filter = where["x-rspace-filter"] as Record<string, unknown>;
    (filter.selectors as Record<string, unknown>)["target.value.globalId"] = {
      schema: { type: "string" },
      operators: ["=="],
    };

    expect(() => apiV2CollectionMetadataFromOpenApi<TestRecord>(tooDeep, "records")).toThrow("target.value.globalId");

    const tooDeepRelationship = structuredClone(openApi) as typeof openApi;
    const relationshipWhere = (
      tooDeepRelationship.paths["/api/v2/records"].get.parameters as Record<string, unknown>[]
    ).find((parameter) => parameter.name === "where") as Record<string, unknown>;
    relationshipWhere["x-rspace-relationship-fields"] = {
      "owner.value.globalId": { schema: { type: "string" }, operators: ["=="] },
    };

    expect(() => apiV2CollectionMetadataFromOpenApi<TestRecord>(tooDeepRelationship, "records")).toThrow(
      "owner.value.globalId",
    );
  });

  it("reads generated collection capabilities", () => {
    expect(apiV2CollectionMetadataFromOpenApi<TestRecord>(openApi, "records")).toEqual({
      resourceName: "records",
      fields: ["id", "title", "modifiedAt"],
      sorting: {
        fields: ["title", "modifiedAt"],
        default: [{ field: "modifiedAt", direction: "desc" }],
        maximumFields: 5,
      },
      filtering: {
        selectors: {
          title: { operators: ["==", "=contains="], wildcards: true, fieldType: "text" },
          "target.id": { operators: ["=="], wildcards: false, fieldType: "number" },
          "target.deleted": { operators: ["=="], wildcards: false, fieldType: "boolean" },
          "target.updatedAt": { operators: ["=="], wildcards: false, fieldType: "dateTime" },
          "target.owner": { operators: ["=="], wildcards: false, fieldType: null },
        },
        limits: {
          maximumComparisons: 50,
          maximumLikeComparisons: 10,
          maximumNesting: 10,
          maximumArguments: 100,
          maximumWhereLength: 4096,
        },
      },
      relationshipFields: {},
      runtimeFields: [],
      pagination: { defaultLimit: 20, maximumLimit: 100 },
    });
  });

  it("reads a runtime field namespace when the collection declares one", () => {
    const withRuntimeFields = structuredClone(openApi) as typeof openApi;
    const parameters = (
      withRuntimeFields.paths["/api/v2/records"].get as {
        parameters: Record<string, unknown>[];
      }
    ).parameters;
    const where = parameters.find((parameter) => parameter.name === "where");
    if (!where) throw new Error("fixture has no where parameter");
    where["x-rspace-runtime-fields"] = [
      {
        namespace: "customFields",
        catalog: "/api/v2/instruments/fields/customFields",
        responseField: "customFields",
        filterable: true,
        columnSelectable: true,
        sortable: false,
        maximumProjections: 50,
        catalogDefaultLimit: 50,
        catalogMaximumLimit: 200,
        catalogMaximumIds: 100,
        via: "",
        viaResource: "",
      },
    ];

    expect(apiV2CollectionMetadataFromOpenApi<TestRecord>(withRuntimeFields, "records").runtimeFields).toEqual([
      {
        namespace: "customFields",
        catalog: "/api/v2/instruments/fields/customFields",
        responseField: "customFields",
        filterable: true,
        columnSelectable: true,
        sortable: false,
        maximumProjections: 50,
        catalogDefaultLimit: 50,
        catalogMaximumLimit: 200,
        catalogMaximumIds: 100,
        via: "",
        viaResource: "",
      },
    ]);
  });

  it("reads a namespace reached through a relationship", () => {
    const hopped = structuredClone(openApi) as typeof openApi;
    const parameters = (hopped.paths["/api/v2/records"].get as { parameters: Record<string, unknown>[] }).parameters;
    const where = parameters.find((parameter) => parameter.name === "where");
    if (!where) throw new Error("fixture has no where parameter");
    where["x-rspace-runtime-fields"] = [
      {
        namespace: "target.customFields",
        catalog: "/api/v2/instruments/fields/customFields",
        responseField: "",
        via: "target",
        viaResource: "instruments",
        filterable: true,
        columnSelectable: false,
        sortable: false,
        maximumProjections: 0,
        catalogDefaultLimit: 50,
        catalogMaximumLimit: 200,
        catalogMaximumIds: 100,
      },
    ];

    const [namespace] = apiV2CollectionMetadataFromOpenApi<TestRecord>(hopped, "records").runtimeFields ?? [];

    expect(namespace?.namespace).toBe("target.customFields");
    expect(namespace?.via).toBe("target");
    expect(namespace?.viaResource).toBe("instruments");
    expect(namespace?.columnSelectable).toBe(false);
  });

  it("accepts the booking instrument alias when it delegates to the instrument catalog", () => {
    const delegated = structuredClone(openApi) as typeof openApi;
    const parameters = (delegated.paths["/api/v2/records"].get as { parameters: Record<string, unknown>[] }).parameters;
    const where = parameters.find((parameter) => parameter.name === "where");
    if (!where) throw new Error("fixture has no where parameter");
    where["x-rspace-runtime-fields"] = [
      {
        namespace: "target.customFields",
        catalog: "/api/v2/instruments/fields/customFields",
        responseField: "",
        via: "target",
        viaResource: "booking-instruments",
        filterable: true,
        columnSelectable: false,
        sortable: false,
        maximumProjections: 0,
        catalogDefaultLimit: 50,
        catalogMaximumLimit: 200,
        catalogMaximumIds: 100,
      },
    ];

    const [namespace] = apiV2CollectionMetadataFromOpenApi<TestRecord>(delegated, "records").runtimeFields ?? [];

    expect(namespace?.viaResource).toBe("booking-instruments");
  });

  it("rejects runtime namespaces that do not match their delegated catalog", () => {
    const mismatched = structuredClone(openApi) as typeof openApi;
    const parameters = (mismatched.paths["/api/v2/records"].get as { parameters: Record<string, unknown>[] })
      .parameters;
    const where = parameters.find((parameter) => parameter.name === "where");
    if (!where) throw new Error("fixture has no where parameter");
    where["x-rspace-runtime-fields"] = [
      {
        namespace: "target.extraFields",
        catalog: "/api/v2/instruments/fields/customFields",
        responseField: "",
        via: "target",
        viaResource: "booking-instruments",
        filterable: true,
        columnSelectable: false,
        sortable: false,
        maximumProjections: 0,
        catalogDefaultLimit: 50,
        catalogMaximumLimit: 200,
        catalogMaximumIds: 100,
      },
    ];

    expect(() => apiV2CollectionMetadataFromOpenApi<TestRecord>(mismatched, "records")).toThrow(
      "does not match its catalog",
    );
  });

  it("rejects duplicate runtime namespaces", () => {
    const duplicate = structuredClone(openApi) as typeof openApi;
    const parameters = (duplicate.paths["/api/v2/records"].get as { parameters: Record<string, unknown>[] }).parameters;
    const where = parameters.find((parameter) => parameter.name === "where");
    if (!where) throw new Error("fixture has no where parameter");
    const namespace = {
      namespace: "customFields",
      catalog: "/api/v2/instruments/fields/customFields",
      responseField: "customFields",
      filterable: true,
      columnSelectable: true,
      sortable: false,
      maximumProjections: 50,
      catalogDefaultLimit: 50,
      catalogMaximumLimit: 200,
      catalogMaximumIds: 100,
      via: "",
      viaResource: "",
    };
    where["x-rspace-runtime-fields"] = [namespace, namespace];

    expect(() => apiV2CollectionMetadataFromOpenApi<TestRecord>(duplicate, "records")).toThrow(
      "Duplicate runtime field namespace",
    );
  });

  it("rejects a runtime field descriptor with no catalog", () => {
    const invalid = structuredClone(openApi) as typeof openApi;
    const parameters = (invalid.paths["/api/v2/records"].get as { parameters: Record<string, unknown>[] }).parameters;
    const where = parameters.find((parameter) => parameter.name === "where");
    if (!where) throw new Error("fixture has no where parameter");
    where["x-rspace-runtime-fields"] = [{ namespace: "customFields", responseField: "customFields" }];

    expect(() => apiV2CollectionMetadataFromOpenApi<TestRecord>(invalid, "records")).toThrow("0.catalog:");
  });
});
