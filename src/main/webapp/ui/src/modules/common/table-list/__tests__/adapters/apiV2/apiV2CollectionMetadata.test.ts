import { describe, expect, it } from "vitest";
import { apiV2CollectionMetadataFromOpenApi } from "../../../adapters/apiV2/apiV2CollectionMetadata";
import type { TestRecord } from "../../fixtures/tableListFixtures";

const openApi = {
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
      },
    },
  },
};

describe("apiV2CollectionMetadataFromOpenApi", () => {
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

  it("reads optional picker identity metadata while keeping legacy identity selectors typed", () => {
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

    expect(() => apiV2CollectionMetadataFromOpenApi<TestRecord>(malformedPicker, "records")).toThrow(
      "has an invalid picker",
    );

    const unknownOperator = structuredClone(openApi) as typeof openApi;
    const unknownWhere = (unknownOperator.paths["/api/v2/records"].get.parameters as Record<string, unknown>[]).find(
      (parameter) => parameter.name === "where",
    ) as Record<string, unknown>;
    const unknownFilter = unknownWhere["x-rspace-filter"] as Record<string, unknown>;
    (unknownFilter.selectors as Record<string, Record<string, unknown>>).title = {
      schema: { type: "string" },
      operators: ["=regex="],
    };

    expect(() => apiV2CollectionMetadataFromOpenApi<TestRecord>(unknownOperator, "records")).toThrow(
      "has an unknown operator",
    );
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

    expect(() => apiV2CollectionMetadataFromOpenApi<TestRecord>(tooDeep, "records")).toThrow(
      "must have at most one relationship hop",
    );

    const tooDeepRelationship = structuredClone(openApi) as typeof openApi;
    const relationshipWhere = (
      tooDeepRelationship.paths["/api/v2/records"].get.parameters as Record<string, unknown>[]
    ).find((parameter) => parameter.name === "where") as Record<string, unknown>;
    relationshipWhere["x-rspace-relationship-fields"] = {
      "owner.value.globalId": { schema: { type: "string" }, operators: ["=="] },
    };

    expect(() => apiV2CollectionMetadataFromOpenApi<TestRecord>(tooDeepRelationship, "records")).toThrow(
      "must have at most one relationship hop",
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
        catalogMaximumIds: 50,
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
    };
    where["x-rspace-runtime-fields"] = [namespace, namespace];

    expect(() => apiV2CollectionMetadataFromOpenApi<TestRecord>(duplicate, "records")).toThrow(
      "Duplicate runtime field namespace customFields",
    );
  });

  it("rejects a runtime field descriptor with no catalog", () => {
    const invalid = structuredClone(openApi) as typeof openApi;
    const parameters = (invalid.paths["/api/v2/records"].get as { parameters: Record<string, unknown>[] }).parameters;
    const where = parameters.find((parameter) => parameter.name === "where");
    if (!where) throw new Error("fixture has no where parameter");
    where["x-rspace-runtime-fields"] = [{ namespace: "customFields", responseField: "customFields" }];

    expect(() => apiV2CollectionMetadataFromOpenApi<TestRecord>(invalid, "records")).toThrow(
      "Runtime field catalog must be a URL",
    );
  });
});
