import { describe, expect, it } from "vitest";
import { tableListMetadata } from "./tableMetadata.ts";

type Json = Record<string, unknown>;

function fixture() {
  return {
    components: { schemas: { RecordRead: { properties: {} } } },
    paths: {
      "/api/v2/records": {
        get: {
          tags: ["records"],
          "x-rspace-operation": "LIST",
          parameters: [
            {
              name: "sort",
              "x-rspace-sort": { fields: ["title", "modifiedAt"], default: ["-modifiedAt"], maximumFields: 5 },
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
                    title: "Title",
                  },
                  "target.id": { schema: { type: "integer", format: "int64" }, operators: ["=="], wildcards: false },
                  "target.deleted": { schema: { type: "boolean" }, operators: ["=="], wildcards: false },
                  "target.updatedAt": {
                    schema: { type: "string", format: "date-time" },
                    operators: ["=="],
                    wildcards: false,
                  },
                  "target.owner": { schema: { type: "object" }, operators: ["=="], wildcards: false },
                } as Record<string, Json>,
              } as Json,
            } as Json,
            { name: "limit", schema: { type: "integer", default: 20, maximum: 100 } },
            { name: "fields", "x-rspace-allowed-fields": { records: ["id", "title", "modifiedAt"] } },
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
      "/api/v2/instruments/fields/{namespace}": {
        get: { tags: ["instruments"], "x-rspace-operation": "RUNTIME_FIELDS" },
      },
    },
  };
}
type Fixture = ReturnType<typeof fixture>;

const parameter = (document: Fixture, name: string): Json => {
  const found = document.paths["/api/v2/records"].get.parameters.find((candidate) => candidate.name === name);
  if (!found) throw new Error(`Fixture has no ${name} parameter`);
  return found;
};
const filter = (document: Fixture) => parameter(document, "where")["x-rspace-filter"] as Json;
const selectors = (document: Fixture) => filter(document).selectors as Record<string, Json>;
const records = (document: Fixture) => tableListMetadata(document).records;

const runtimeNamespace: Json = {
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
};
function withRuntimeFields(...namespaces: unknown[]) {
  const document = fixture();
  parameter(document, "where")["x-rspace-runtime-fields"] = namespaces;
  return document;
}

describe("tableListMetadata", () => {
  it("emits one entry per LIST resource without published titles", () => {
    expect(tableListMetadata(fixture())).toEqual({
      records: {
        resourceName: "records",
        fields: ["id", "title", "modifiedAt"],
        sorting: {
          fields: ["title", "modifiedAt"],
          default: [{ field: "modifiedAt", direction: "desc" }],
          maximumFields: 5,
        },
        filtering: {
          selectors: {
            title: { fieldType: "text", operators: ["==", "=contains="], wildcards: true },
            "target.id": { fieldType: "number", operators: ["=="], wildcards: false },
            "target.deleted": { fieldType: "boolean", operators: ["=="], wildcards: false },
            "target.updatedAt": { fieldType: "dateTime", operators: ["=="], wildcards: false },
            "target.owner": { fieldType: null, operators: ["=="], wildcards: false },
          },
          limits: {
            maximumComparisons: 50,
            maximumLikeComparisons: 10,
            maximumNesting: 10,
            maximumArguments: 100,
            maximumWhereLength: 4096,
          },
        },
        runtimeFields: [],
        pagination: { defaultLimit: 20, maximumLimit: 100 },
      },
    });
  });

  it.each<[string, (document: Fixture) => void, string]>([
    [
      "the list operation that is malformed",
      (document) => Reflect.deleteProperty(document.paths["/api/v2/records"].get, "responses"),
      "records list operation",
    ],
    [
      "the sort parameter when its default is invalid",
      (document) => {
        (parameter(document, "sort")["x-rspace-sort"] as Json).default = null;
      },
      "sort metadata",
    ],
    [
      "the limit parameter when its default is invalid",
      (document) => {
        (parameter(document, "limit").schema as Json).default = null;
      },
      "limit schema",
    ],
    [
      "a non-positive where bound",
      (document) => {
        parameter(document, "where").schema = { type: "string", maxLength: 0 };
      },
      "where schema",
    ],
    [
      "a non-positive limit bound",
      (document) => {
        parameter(document, "limit").schema = { type: "integer", default: 20, maximum: 0 };
      },
      "limit schema",
    ],
  ])("names %s", (_label, mutate, section) => {
    const document = fixture();
    mutate(document);
    expect(() => tableListMetadata(document)).toThrow(section);
  });

  it("rejects a default sort on a field that is not sortable", () => {
    const document = fixture();
    (parameter(document, "sort")["x-rspace-sort"] as Json).default = ["-createdAt"];
    expect(() => tableListMetadata(document)).toThrow("default sort field createdAt is not sortable");
  });

  it.each([undefined, null, "true", 1])("rejects the selector flag %s instead of coercing it", (wildcards) => {
    const document = fixture();
    selectors(document).title.wildcards = wildcards;
    expect(() => tableListMetadata(document)).toThrow("title.wildcards");
  });

  it.each([0, -1, 1.5, Number.POSITIVE_INFINITY, Number.NaN])("rejects invalid query limits: %s", (limit) => {
    const document = fixture();
    filter(document).maximumComparisons = limit;
    expect(() => tableListMetadata(document)).toThrow("maximumComparisons");
  });

  const without = (field: string) => {
    const namespace = { ...runtimeNamespace };
    delete namespace[field];
    return namespace;
  };
  it.each<[string, Json, string]>([
    ...[
      "filterable",
      "columnSelectable",
      "sortable",
      "via",
      "catalogDefaultLimit",
      "catalogMaximumLimit",
      "catalogMaximumIds",
    ].map((field): [string, Json, string] => [`a missing ${field}`, without(field), `0.${field}`]),
    ["a string filterable flag", { ...runtimeNamespace, filterable: "true" }, "0.filterable"],
    [
      "an infinite catalogMaximumIds",
      { ...runtimeNamespace, catalogMaximumIds: Number.POSITIVE_INFINITY },
      "0.catalogMaximumIds",
    ],
    ["no catalog", { namespace: "customFields", responseField: "customFields" }, "0.catalog:"],
  ])("rejects a runtime descriptor with %s", (_label, descriptor, path) => {
    expect(() => records(withRuntimeFields(descriptor))).toThrow(path);
  });

  it("rejects null for x-rspace-runtime-fields", () => {
    const document = fixture();
    parameter(document, "where")["x-rspace-runtime-fields"] = null;
    expect(() => records(document)).toThrow("Validation failed");
  });

  it("rejects a namespace outside the runtime-field pattern", () => {
    expect(() => records(withRuntimeFields({ ...runtimeNamespace, namespace: "target.otherFields" }))).toThrow(
      "0.namespace",
    );
  });

  it("emits published runtime namespaces, including those reached through a relationship", () => {
    const hopped = {
      ...runtimeNamespace,
      namespace: "target.customFields",
      responseField: "",
      via: "target",
      columnSelectable: false,
      maximumProjections: 0,
      viaResource: "instruments",
    };
    const { viaResource: _viaResource, ...emitted } = hopped;
    expect(records(withRuntimeFields(runtimeNamespace, hopped)).runtimeFields).toEqual([runtimeNamespace, emitted]);
  });

  it("rejects duplicate runtime namespaces", () => {
    expect(() => records(withRuntimeFields(runtimeNamespace, runtimeNamespace))).toThrow(
      "Duplicate runtime field namespace customFields",
    );
  });

  it("rejects runtime namespaces that do not match their catalog", () => {
    const mismatched = { ...runtimeNamespace, namespace: "target.extraFields", via: "target" };
    expect(() => records(withRuntimeFields(mismatched))).toThrow("does not match its catalog");
  });

  it("rejects a catalog outside the runtime-field route pattern", () => {
    const catalog = "https://example.com/api/v2/instruments/fields/customFields";
    expect(() => records(withRuntimeFields({ ...runtimeNamespace, catalog }))).toThrow(
      "Unsupported runtime field catalog route",
    );
  });

  it("rejects a catalog that is not a RUNTIME_FIELDS operation", () => {
    const catalog = "/api/v2/containers/fields/customFields";
    expect(() => records(withRuntimeFields({ ...runtimeNamespace, catalog }))).toThrow(
      `Runtime field catalog ${catalog} is not a RUNTIME_FIELDS operation`,
    );
  });

  it("rejects a via that does not name the namespace's relationship", () => {
    expect(() => records(withRuntimeFields({ ...runtimeNamespace, via: "target" }))).toThrow(
      "unsupported relationship source",
    );
  });

  it.each([
    [{ oneOf: [{ type: "integer" }, { type: "number" }] }, "number"],
    [{ oneOf: [{ type: "integer" }, { type: "string" }] }, "text"],
    [{ type: ["string", "null"], format: "date-time" }, "dateTime"],
    [{ type: ["integer", "null"] }, "number"],
  ])("reads the selector type of %j", (schema, fieldType) => {
    const document = fixture();
    selectors(document).title.schema = schema;
    expect(records(document).filtering.selectors.title?.fieldType).toBe(fieldType);
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
    const document = fixture();
    selectors(document).title.schema = schema;
    expect(() => records(document)).toThrow("title.schema");
  });

  it("emits picker metadata", () => {
    const document = fixture();
    const picker = { resource: "instruments", identity: "globalId", globalIdPrefix: "IN" };
    selectors(document).target = { schema: { type: "string" }, operators: ["==", "=in="], wildcards: false, picker };
    expect(records(document).filtering.selectors.target).toEqual({
      fieldType: "text",
      operators: ["==", "=in="],
      wildcards: false,
      picker,
    });
  });

  it.each([
    [{ resource: "https://evil.example", identity: "globalId", globalIdPrefix: "IN" }, "target.picker"],
    [{ resource: "instruments", identity: "id", globalIdPrefix: "IN" }, "target.picker.identity"],
    [{ resource: "instruments", identity: "globalId", globalIdPrefix: "in" }, "target.picker.globalIdPrefix"],
  ])("rejects the malformed picker %j", (picker, path) => {
    const document = fixture();
    selectors(document).target = { schema: { type: "string" }, operators: ["=="], wildcards: false, picker };
    expect(() => records(document)).toThrow(path);
  });

  it("rejects a picker on a selector whose name is not a plain identifier", () => {
    const document = fixture();
    selectors(document)["target-id"] = {
      schema: { type: "string" },
      operators: ["=="],
      wildcards: false,
      picker: { resource: "instruments", identity: "globalId", globalIdPrefix: "IN" },
    };
    expect(() => records(document)).toThrow("Filter selector target-id has an invalid picker");
  });

  it("rejects unknown operators", () => {
    const document = fixture();
    selectors(document).title.operators = ["=regex="];
    expect(() => records(document)).toThrow("title.operators");
  });

  it("rejects selectors beyond one relationship hop", () => {
    const document = fixture();
    selectors(document)["target.value.globalId"] = { schema: { type: "string" }, operators: ["=="], wildcards: false };
    expect(() => records(document)).toThrow("target.value.globalId");
  });

  it("requires a local list item reference instead of an inline schema", () => {
    const document = fixture();
    const items =
      document.paths["/api/v2/records"].get.responses["200"].content["application/json"].schema.properties.docs.items;
    items.$ref = "https://example.com/schema.json";
    expect(() => records(document)).toThrow("items.$ref");
  });

  it("requires the Read component", () => {
    const document = fixture();
    Reflect.deleteProperty(document.components.schemas, "RecordRead");
    expect(() => records(document)).toThrow("OpenAPI Read component RecordRead was not found");
  });

  it("requires the resource's allowed fields", () => {
    const document = fixture();
    parameter(document, "fields")["x-rspace-allowed-fields"] = { instruments: ["id"] };
    expect(() => records(document)).toThrow("OpenAPI has no allowed fields for records");
  });
});
