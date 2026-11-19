import { expect, it } from "vitest";
import { generate } from "./generate.ts";
import type { Content, OpenApiDocument, SchemaObject } from "./openapi.ts";
import { fixture } from "./test-helper.ts";

const native =
  'import * as v from "valibot";\nexport const SendBody = v.string();\nexport const SendResponse = v.string();\nexport const Binary = v.file();\n';
function spec(
  request: Content,
  responses: Record<string, { content?: Content }>,
  schemas: Record<string, SchemaObject> = {},
): OpenApiDocument {
  return fixture({
    components: { schemas },
    paths: {
      "/": {
        post: {
          operationId: "send",
          requestBody: { content: request },
          responses,
        },
      },
    },
  });
}
const media = (schema: SchemaObject): Content => ({
  "application/json": { schema },
});
it("rejects distinct JSON request media schemas", () => {
  expect(() =>
    generate(
      spec(
        {
          ...media({ type: "string" }),
          "application/vendor+json": { schema: { type: "integer" } },
        },
        {},
      ),
      native,
    ),
  ).toThrow("Distinct JSON request schemas");
});
it("rejects distinct success status and media schemas", () => {
  expect(() =>
    generate(
      spec(
        {},
        {
          200: { content: media({ type: "string" }) },
          201: { content: media({ type: "integer" }) },
        },
      ),
      native,
    ),
  ).toThrow("Distinct JSON success schemas");
  expect(() =>
    generate(
      spec(
        {},
        {
          200: {
            content: {
              ...media({ type: "string" }),
              "application/vendor+json": { schema: { type: "integer" } },
            },
          },
        },
      ),
      native,
    ),
  ).toThrow("Distinct JSON success schemas");
});
it("compares schema keys independently of their order", () => {
  const source = generate(
    spec(
      {},
      {
        200: { content: media({ type: "string", minLength: 1 }) },
        201: { content: media({ minLength: 1, type: "string" }) },
      },
    ),
    native,
  ).operations;
  expect(source).toContain('"200":');
  expect(source).toContain('"201":');
});
it("skips referenced and inline binary bodies, binary responses and 204", () => {
  const content = media({ $ref: "#/components/schemas/Binary" });
  const source = generate(
    spec(
      content,
      { 200: { content }, 204: { content: media({ type: "string" }) } },
      { Binary: { type: "string", format: "binary" } },
    ),
    native,
  ).operations;
  expect(source).toContain('"send": {public: true, responses: {}}');
  const inline = generate(spec(media({ type: "string", format: "binary" }), {}), native).operations;
  expect(inline).toContain('"send": {public: true, responses: {}}');
});
it("fails when the native export required by the registry is absent", () => {
  expect(() => generate(spec(media({ type: "string" }), {}), "")).toThrow("Missing native operation schema SendBody");
});
it("rejects schema-less JSON media", () => {
  expect(() => generate(spec({ "application/json": {} }, {}), native)).toThrow("Missing JSON schema");
});
