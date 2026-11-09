import { stripTypeScriptTypes } from "node:module";
import * as v from "valibot";
import { describe, expect, it } from "vitest";
import { generate } from "./test-helper.mjs";

function document(schemas, paths = {}) {
  return { security: [{ bearerAuth: [] }], components: { schemas }, paths };
}
// Runtime checks evaluate the emitted expressions; type assertions are checked separately by tsc.
function compile(schemas) {
  const source = generate(document(schemas))
    .schemas.replace(/^import .*;$/gm, "")
    .split("type Assert<")[0]
    .replace(/export const (\w+): v.GenericSchema<unknown, Models\.\w+>/g, "const $1")
    .replaceAll("export const", "const");
  return new Function(
    "v",
    `${stripTypeScriptTypes(source)}\nreturn {${Object.keys(schemas)
      .map((name) => `${name}: ${name}Schema`)
      .join(",")}}`,
  )(v);
}
function schema(fragment) {
  return compile({ Value: fragment }).Value;
}

describe("schema conversion", () => {
  it.each([
    [{ type: "string" }, "x", 1],
    [{ type: "boolean" }, false, "false"],
    [{ type: "number" }, 1.5, "1"],
    [{ type: "integer" }, 1, 1.5],
    [{ type: "null" }, null, undefined],
    [{ type: ["string", "null"] }, null, 1],
    [{ type: ["integer", "string"] }, "one", false],
    [{ const: "fixed" }, "fixed", "other"],
    [{ const: false }, false, true],
    [{ const: null }, null, 0],
    [{ enum: ["a", "b"] }, "a", "c"],
    [{ enum: ["a", null] }, null, "c"],
    [{ type: "string", format: "date-time" }, "2026-11-07T12:00:00Z", "yesterday"],
    [{ type: "string", format: "date" }, "2026-11-07", "November"],
    [{ type: "string", format: "email" }, "a@example.com", "a"],
    [{ type: "string", format: "uri" }, "anything", 1],
    [{ type: "string", format: "uri-reference" }, "relative/path", 1],
    [{ type: "string", minLength: 2 }, "ab", "a"],
    [{ type: "string", maxLength: 2 }, "ab", "abc"],
    [{ type: "string", pattern: "^a" }, "abc", "bcd"],
    [{ type: "number", minimum: 2 }, 2, 1],
    [{ type: "number", maximum: 2 }, 2, 3],
    [{ type: "number", exclusiveMinimum: 2 }, 2.1, 2],
    [{ type: "array", items: { type: "string" }, minItems: 1 }, ["a"], []],
    [{ type: "array", items: { type: "string" }, maxItems: 1 }, ["a"], ["a", "b"]],
    [{ type: "array", items: { type: "string" } }, ["a"], [1]],
  ])("validates rule %j", (fragment, valid, invalid) => {
    const value = schema(fragment);
    expect(v.safeParse(value, valid).success).toBe(true);
    expect(v.safeParse(value, invalid).success).toBe(false);
  });

  it("keeps idn-email unvalidated while retaining email validation", () => {
    const fragment = { type: "string", format: "idn-email" };
    const output = generate(document({ Value: fragment }));
    expect(output.native).toContain("v.string()");
    expect(output.native).not.toMatch(/v\.(?:pipe|email|rfcEmail)\(/);
    const value = schema(fragment);
    for (const address of ["o'brien@uni.ac.uk", "admin@localhost", "用户@例子.公司", "not-an-address"]) {
      expect(v.safeParse(value, address).success).toBe(true);
    }
    expect(v.safeParse(value, 42).success).toBe(false);
    expect(generate(document({ Value: { type: "string", format: "email" } })).native).toContain("v.email()");
  });

  it("validates quoted and prototype-named members", () => {
    const fragment = JSON.parse(
      '{"type":"object","properties":{"odd-key":{"type":"string"},"__proto__":{"type":"number"}},"required":["__proto__"]}',
    );
    expect(v.safeParse(schema(fragment), JSON.parse('{"__proto__":2}')).success).toBe(true);
    expect(v.safeParse(schema(fragment), {}).success).toBe(false);
  });
  it("keeps projections optional according to the spec, but checks selected fields", () => {
    const value = schema({
      type: "object",
      properties: { id: { type: "integer" }, name: { type: "string" } },
      required: ["id"],
      additionalProperties: false,
    });
    expect(v.safeParse(value, { id: 1 }).success).toBe(true);
    expect(v.safeParse(value, { id: 1, name: 3 }).success).toBe(false);
    const selected = v.required(v.pick(value, ["name"]));
    expect(v.safeParse(selected, {}).success).toBe(false);
    expect(v.safeParse(selected, { name: "selected" }).success).toBe(true);
    expect(v.safeParse(selected, { name: 3 }).success).toBe(false);
    expect(v.safeParse(value, { id: 1, added: true }).success).toBe(true);
  });
  it.each([true, { type: "string" }])(
    "preserves dictionary values with additionalProperties %j",
    (additionalProperties) => {
      for (const properties of [undefined, { id: { type: "integer" } }]) {
        const value = schema({ type: "object", ...(properties ? { properties } : {}), additionalProperties });
        const input = { ...(properties ? { id: 1 } : {}), arbitrary: "kept" };
        expect(v.parse(value, input)).toEqual(input);
        if (additionalProperties !== true) expect(v.safeParse(value, { arbitrary: 5 }).success).toBe(false);
      }
    },
  );
  it("merges allOf objects and references with required members", () => {
    const { Value } = compile({
      Base: { type: "object", properties: { id: { type: "integer" } }, required: ["id"] },
      Value: {
        allOf: [
          { $ref: "#/components/schemas/Base" },
          { type: "object", properties: { name: { type: "string" } }, required: ["name"] },
        ],
      },
    });
    expect(v.safeParse(Value, { id: 1, name: "a" }).success).toBe(true);
    expect(v.safeParse(Value, { id: 1 }).success).toBe(false);
  });
  it.each(["anyOf", "oneOf"])("converts %s to a union", (keyword) => {
    const value = schema({ [keyword]: [{ type: "integer" }, { type: "string" }] });
    expect(v.safeParse(value, 1).success).toBe(true);
    expect(v.safeParse(value, "a").success).toBe(true);
    expect(v.safeParse(value, false).success).toBe(false);
  });
  it("handles direct and mutual cycles and noncyclic consumers", () => {
    const { Tree, Consumer } = compile({
      Consumer: { $ref: "#/components/schemas/Tree" },
      Tree: {
        type: "object",
        properties: { children: { type: "array", items: { $ref: "#/components/schemas/Branch" } } },
      },
      Branch: {
        type: "object",
        properties: { tree: { $ref: "#/components/schemas/Tree" }, self: { $ref: "#/components/schemas/Branch" } },
      },
    });
    expect(v.safeParse(Tree, { children: [{ tree: { children: [] }, self: {} }] }).success).toBe(true);
    expect(v.safeParse(Consumer, { children: "bad" }).success).toBe(false);
  });
  it("emits readonly for readonly arrays", () => {
    expect(
      generate(document({ Value: { type: "array", items: { type: "string" }, readOnly: true } })).native,
    ).toContain("v.readonly()");
  });
  it("ignores the documented annotations", () => {
    const value = schema({
      type: "integer",
      format: "int64",
      title: "x",
      description: "x",
      example: 1,
      examples: [1],
      default: 1,
      deprecated: true,
      readOnly: true,
      writeOnly: false,
      nullable: false,
      "x-rspace-fields": { arbitrary: true },
    });
    expect(v.safeParse(value, 1).success).toBe(true);
  });
  it.each(["not", "discriminator", "uniqueItems", "exclusiveMaximum", "minProperties", "unknownKeyword", "$defs"])(
    "rejects unsupported keyword %s",
    (key) => {
      expect(() => schema({ type: "string", [key]: {} })).toThrow(`Unsupported keyword ${key}`);
    },
  );
  it("rejects unsupported keywords nested below composition", () => {
    expect(() => schema({ allOf: [{ type: "object", properties: { name: { type: "string", not: {} } } }] })).toThrow(
      "Unsupported keyword not",
    );
  });
  it("rejects nullable true, unknown formats, missing refs and non-object allOf", () => {
    expect(() => schema({ nullable: true })).toThrow("nullable");
    expect(() => schema({ type: "string", format: "uuid" })).toThrow("Unsupported format");
    expect(() => schema({ $ref: "#/components/schemas/Missing" })).toThrow("Missing component");
    expect(() => schema({ allOf: [{ type: "string" }] })).toThrow("object members only");
  });
});

describe("operation registry", () => {
  it.each([
    ["explicit browser-session override", [{ bearerAuth: [] }], [{ browserSession: [] }], true],
    ["inherited browser session", [{ browserSession: [] }], undefined, true],
    ["public override of browser session", [{ browserSession: [] }], [], false],
    ["bearer override of browser session", [{ browserSession: [] }], [{ bearerAuth: [] }], false],
    ["combined session and bearer requirement", undefined, [{ browserSession: [], bearerAuth: [] }], false],
    ["alternative session and bearer requirements", undefined, [{ browserSession: [] }, { bearerAuth: [] }], false],
    ["anonymous alternative to browser session", undefined, [{ browserSession: [] }, {}], false],
  ])("marks session-only operations from %s", (_name, topLevelSecurity, operationSecurity, isSessionOnly) => {
    const spec = document(
      {},
      {
        "/": {
          post: {
            operationId: "mintToken",
            responses: {},
            ...(operationSecurity === undefined ? {} : { security: operationSecurity }),
          },
        },
      },
    );
    if (topLevelSecurity === undefined) delete spec.security;
    else spec.security = topLevelSecurity;
    const entry = generate(spec).operations.split('["mintToken"]: ')[1];
    expect(entry.startsWith("{session: true, ")).toBe(isSessionOnly);
    expect(entry.includes("public: true")).toBe(operationSecurity?.length === 0);
  });

  it.each([
    ["explicit public override", [{ bearerAuth: [] }], [], true],
    ["inherited public security", [], undefined, true],
    ["default public security", undefined, undefined, true],
    ["explicit protected override", [], [{ bearerAuth: [] }], false],
    ["inherited protected security", [{ bearerAuth: [] }], undefined, false],
    ["explicit protected security", undefined, [{ bearerAuth: [] }], false],
  ])("marks public operations from %s", (_name, topLevelSecurity, operationSecurity, isPublic) => {
    const spec = document(
      {},
      {
        "/": {
          get: {
            operationId: "getValue",
            responses: {},
            ...(operationSecurity === undefined ? {} : { security: operationSecurity }),
          },
        },
      },
    );
    if (topLevelSecurity === undefined) delete spec.security;
    else spec.security = topLevelSecurity;
    const output = generate(spec).operations;
    expect(output).toContain("public?: true");
    expect(output).toContain(
      isPublic ? '["getValue"]: {public: true, responses: {}}' : '["getValue"]: {responses: {}}',
    );
  });
  it("registers requests, JSON success statuses and media types, including empty operations", () => {
    const output = generate(
      document(
        { Value: { type: "string" } },
        {
          "/items": {
            parameters: [{ name: "page", in: "query", schema: { type: "integer" } }],
            post: {
              operationId: "create",
              requestBody: { content: { "application/json": { schema: { $ref: "#/components/schemas/Value" } } } },
              responses: {
                200: {
                  content: {
                    "application/json": { schema: { $ref: "#/components/schemas/Value" } },
                    "application/vnd.test+json": { schema: { $ref: "#/components/schemas/Value" } },
                  },
                },
                201: { content: { "application/json": { schema: { $ref: "#/components/schemas/Value" } } } },
                204: {},
                400: { content: { "application/json": { schema: { type: "string" } } } },
              },
            },
          },
          "/blob": {
            get: {
              operationId: "download",
              responses: {
                200: { content: { "application/octet-stream": { schema: { type: "string", format: "binary" } } } },
              },
            },
          },
        },
      ),
    ).operations;
    expect(output).toContain('["create"]: {request: native.CreateBody');
    expect(output).toContain('["200"]:');
    expect(output).toContain('["201"]:');
    expect(output).toContain('["application/vnd.test+json"]: native.CreateResponse');
    expect(output).not.toContain('["400"]:');
    expect(output).not.toContain('["204"]:');
    expect(output).toContain('["download"]: {responses: {}}');
  });
  it("skips binary JSON schemas", () => {
    const output = generate(
      document(
        {},
        {
          "/": {
            post: {
              operationId: "upload",
              requestBody: { content: { "application/json": { schema: { type: "string", format: "binary" } } } },
              responses: {},
            },
          },
        },
      ),
    ).operations;
    expect(output).toContain('["upload"]: {responses: {}}');
  });
  it("rejects duplicate operation IDs and unsupported inline schemas", () => {
    const operation = { operationId: "same", responses: {} };
    expect(() => generate(document({}, { "/": { get: operation, post: operation } }))).toThrow("duplicate operationId");
    expect(() =>
      generate(
        document(
          {},
          { "/": { get: { operationId: "get", parameters: [{ name: "q", schema: { not: {} } }], responses: {} } } },
        ),
      ),
    ).toThrow("Unsupported keyword not");
  });
});

it("fails on unsupported inline error-response validation too", () => {
  expect(() =>
    generate(
      document(
        {},
        {
          "/": {
            get: {
              operationId: "get",
              responses: { 400: { content: { "application/json": { schema: { type: "object", not: {} } } } } },
            },
          },
        },
      ),
    ),
  ).toThrow("Unsupported keyword not");
});

it("does not silently drop union sibling validation", () => {
  expect(() => schema({ anyOf: [{ type: "string" }], maxLength: 5 })).toThrow("Unsupported union sibling maxLength");
});

it("preserves literal values that resemble native validation expressions", () => {
  const literal = "literal, v.url() and v.lazy((): v.GenericSchema => Value)";
  expect(v.parse(schema({ const: literal }), literal)).toBe(literal);
});
