import { describe, expect, it } from "vitest";
import { guaranteedReadFields } from "./resources.ts";
import { fixture } from "./test-helper.ts";

const document = fixture({
  components: {
    schemas: {
      ItemsRead: {
        type: "object",
        properties: {
          id: { type: "integer", "x-rspace-read-guaranteed": true, "x-rspace-access": {} },
          name: { type: "string", "x-rspace-read-guaranteed": true, "x-rspace-access": {} },
          owner: { type: "string", "x-rspace-access": {} },
          customFields: { type: "object" },
        },
      },
    },
  },
  paths: {
    "/api/v2/items/{id}": {
      get: {
        operationId: "getItem",
        tags: ["items"],
        "x-rspace-operation": "READ",
        responses: { 200: { content: { "application/json": { schema: { $ref: "#/components/schemas/ItemsRead" } } } } },
      },
    },
  },
});

describe("guaranteed read fields", () => {
  it("reads only the read-guaranteed extension, whatever the access metadata", () => {
    expect(guaranteedReadFields(document)).toEqual(new Map([["items", ["id", "name"]]]));
  });
});
