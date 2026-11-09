import { describe, expect, it } from "vitest";
import { dereferenceApiV2OpenApi } from "./openApiReferences";

const document = {
  paths: {
    "/api/v2/records": {
      get: {
        parameters: [{ $ref: "#/components/parameters/RecordsWhere" }],
        responses: { "200": { $ref: "#/components/responses/Records" } },
      },
    },
  },
  components: {
    parameters: {
      RecordsWhere: { name: "where", "x-rspace-filter": { $ref: "#/components/x-rspace-filters/Records" } },
    },
    responses: {
      Records: { content: { "application/json": { schema: { $ref: "#/components/schemas/RecordsRead" } } } },
    },
    "x-rspace-filters": { Records: { selectors: {} } },
    schemas: { RecordsRead: { type: "object" } },
  },
};

describe("dereferenceApiV2OpenApi", () => {
  it("inlines component references and keeps schema references", () => {
    expect(dereferenceApiV2OpenApi(document)).toMatchObject({
      paths: {
        "/api/v2/records": {
          get: {
            parameters: [{ name: "where", "x-rspace-filter": { selectors: {} } }],
            responses: {
              "200": { content: { "application/json": { schema: { $ref: "#/components/schemas/RecordsRead" } } } },
            },
          },
        },
      },
    });
  });

  it("rejects a reference without a target", () => {
    expect(() => dereferenceApiV2OpenApi({ parameter: { $ref: "#/components/parameters/Missing" } })).toThrow(
      "Unresolved OpenAPI reference #/components/parameters/Missing",
    );
  });
});
