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
      },
      "owner.rank": { schema: { type: ["integer", "null"] }, operators: [], wildcards: false },
    };

    const metadata = apiV2CollectionMetadataFromOpenApi<TestRecord>(withRelationship, "records");

    expect(metadata.relationshipFields?.["owner.lastLogin"].fieldType).toBe("dateTime");
    expect(metadata.relationshipFields?.["owner.rank"].fieldType).toBe("number");
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
        catalog: "/api/v2/records/custom-fields",
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
        catalog: "/api/v2/records/custom-fields",
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
        catalog: "/api/v2/instruments/custom-fields",
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
