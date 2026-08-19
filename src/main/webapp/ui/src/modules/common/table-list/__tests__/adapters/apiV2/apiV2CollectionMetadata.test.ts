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
      pagination: { defaultLimit: 20, maximumLimit: 100 },
    });
  });
});
