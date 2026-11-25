import type { OpenApiDocument } from "orval";
import { describe, expect, it } from "vitest";
import {
  filterCatalog,
  filteredWhereOperations,
  filteredWhereParameters,
  generateFilterTypes,
  rewriteWhereModel,
} from "./filters.ts";
import { fixture } from "./test-helper.ts";

const filter = (selectors: Record<string, unknown>) => ({ selectors });
function document(filters: Record<string, unknown>, parameters: Record<string, unknown> = {}): OpenApiDocument {
  return fixture({
    components: { "x-rspace-filters": filters, parameters },
    paths: {
      "/api/v2/items": {
        get: { operationId: "listItems", parameters: [{ $ref: "#/components/parameters/ItemsWhere" }] },
        delete: {
          operationId: "deleteManyItems",
          parameters: [
            { name: "where", in: "query", "x-rspace-filter": { $ref: "#/components/x-rspace-filters/Items" } },
          ],
        },
      },
      "/api/v2/search": { get: { operationId: "search", parameters: [{ name: "where", in: "query" }] } },
    },
  });
}
const items = filter({
  id: { operators: ["==", "=in="], wildcards: false, schema: { type: "integer", format: "int64" } },
  "owner.name": { operators: ["=like="], wildcards: true, schema: { type: ["string", "null"], maxLength: 255 } },
  state: { operators: ["=="], wildcards: false, schema: { type: "string", enum: ["OPEN", "CLOSED", null] } },
  when: { operators: ["=gt="], wildcards: false, schema: { type: "string", format: "date-time" } },
  hidden: { operators: [], wildcards: false, schema: { type: "boolean" } },
});
const one = (selector: Record<string, unknown>) =>
  document({ Items: filter({ id: { operators: ["=="], wildcards: false, schema: { type: "string" }, ...selector } }) });
const where = { name: "where", in: "query", "x-rspace-filter": { $ref: "#/components/x-rspace-filters/Items" } };

describe("filter types", () => {
  it("emits selector operators and value types, skipping selectors without operators", () => {
    const source = generateFilterTypes(document({ Items: items }));
    expect(source).toContain('"id": { operators: "==" | "=in="; value: number };');
    expect(source).toContain('"owner.name": { operators: "=like="; value: string };');
    expect(source).toContain('"state": { operators: "=="; value: "OPEN" | "CLOSED" };');
    expect(source).toContain('"when": { operators: "=gt="; value: string | Date };');
    expect(source).not.toContain("hidden");
  });

  it("rejects operators and value types it cannot type", () => {
    expect(() => generateFilterTypes(one({ operators: ["=~="] }))).toThrow("Unsupported operators");
    expect(() => generateFilterTypes(one({ schema: { type: "object" } }))).toThrow("Unsupported filter value type");
  });
});

describe("filter catalog", () => {
  it("classifies every selector once, keeping enum values and selectors without operators", () => {
    expect(filterCatalog(document({ Items: items }))).toEqual({
      Items: {
        hidden: { kind: "boolean", operators: [], wildcards: false },
        id: { kind: "number", operators: ["==", "=in="], wildcards: false },
        "owner.name": { kind: "string", operators: ["=like="], wildcards: true },
        state: { kind: "enum", values: ["OPEN", "CLOSED"], operators: ["=="], wildcards: false },
        when: { kind: "dateTime", operators: ["=gt="], wildcards: false },
      },
    });
  });

  it("keeps numeric enum values and treats a date as a string", () => {
    expect(filterCatalog(one({ schema: { type: "integer", enum: [1, 2] } })).Items.id).toMatchObject({
      kind: "enum",
      values: [1, 2],
    });
    expect(filterCatalog(one({ schema: { type: "string", format: "date" } })).Items.id.kind).toBe("string");
  });

  it("passes a picker through unchecked", () => {
    const picker = { resource: "instruments", identity: "globalId", globalIdPrefix: "IN" };
    expect(filterCatalog(one({ picker })).Items.id.picker).toEqual(picker);
  });

  it.each([
    [{ schema: { oneOf: [{ type: "integer" }, { type: "string" }] } }, "with oneOf"],
    [{ schema: { $ref: "#/components/schemas/Name" } }, "with $ref"],
    [{ schema: { type: ["string", "number"] } }, "Unsupported filter value type"],
    [{ wildcards: "true" }, "wildcards must be a boolean"],
    [{ schema: undefined }, "Missing value schema"],
    [{ schema: {} }, "Unsupported filter value type"],
    [{ schema: { type: "file" } }, "Unsupported filter value type"],
    [{ schema: { type: "constructor" } }, "Unsupported filter value type"],
    [{ schema: { type: ["string", "null", "null"] } }, "Unsupported filter value type"],
    [{ schema: { anyOf: [{ type: "string" }] } }, "with anyOf"],
    [{ schema: { allOf: [{ type: "string" }] } }, "with allOf"],
    [{ schema: { type: "string", oneOf: [{ type: "string" }] } }, "with oneOf"],
    [{ wildcards: undefined }, "wildcards must be a boolean"],
    [{ wildcards: null }, "wildcards must be a boolean"],
    [{ schema: { type: ["string", "number"], enum: ["OPEN", 1] } }, "Unsupported filter value type"],
    [{ schema: { type: "object", enum: ["OPEN"] } }, "Unsupported filter value type"],
    [{ schema: { enum: ["OPEN"] } }, "Unsupported filter value type"],
    [{ schema: { type: "string", enum: ["OPEN", 1] } }, "Unsupported enum"],
    [{ schema: { type: "boolean", enum: [true] } }, "Unsupported enum"],
    [{ schema: { type: "string", format: 42 } }, "format must be a string"],
    [{ title: 1 }, "title must be a string"],
    [{ viaTitle: false }, "viaTitle must be a string"],
  ])("rejects the selector %j", (selector, message) => {
    expect(() => filterCatalog(one(selector))).toThrow(message);
  });

  it.each([undefined, null, [], "selectors"])("rejects the selector map %j", (selectors) => {
    expect(() => filterCatalog(document({ Items: { selectors } }))).toThrow(
      "x-rspace-filters.Items: selectors must be an object",
    );
  });

  it("accepts a filter without selectors", () => {
    expect(filterCatalog(document({ Items: filter({}) }))).toEqual({ Items: {} });
  });

  it("rejects a selector name beyond one relationship hop", () => {
    const deep = filter({ "a.b.c": { operators: ["=="], wildcards: false, schema: { type: "string" } } });
    expect(() => filterCatalog(document({ Items: deep }))).toThrow("Unsupported selector name");
  });
});

describe("filtered where parameters", () => {
  it("finds component and inline where parameters with filter metadata", () => {
    const spec = document({ Items: items }, { ItemsWhere: where });
    expect([...filteredWhereParameters(spec)]).toEqual([["ItemsWhere", "Items"]]);
    expect([...filteredWhereOperations(spec)]).toEqual([
      ["listItems", "Items"],
      ["deleteManyItems", "Items"],
    ]);
  });

  it("rejects a missing filter", () => {
    expect(() => filteredWhereParameters(document({}, { ItemsWhere: where }))).toThrow("Missing filter Items");
  });
});

describe("where model rewrite", () => {
  const model = "/**\n * Generated by orval\n */\n\n/**\n * Filter.\n */\nexport type ItemsWhereParameter = string;\n";

  it("fails when Orval's model shape changes", () => {
    expect(() => rewriteWhereModel(model.replace("string", "number"), "ItemsWhere", "Items")).toThrow(
      "Unexpected model",
    );
  });
});
