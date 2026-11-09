import { expect, it } from "vitest";
import { generate } from "./generate.mjs";

const native =
  'import * as v from "valibot";\nexport const SendBody = v.string();\nexport const SendResponse = v.string();\nexport const Binary = v.file();\n';
function spec(request, responses, schemas = {}) {
  return {
    components: { schemas },
    paths: { "/": { post: { operationId: "send", requestBody: { content: request }, responses } } },
  };
}
const media = (schema) => ({ "application/json": { schema } });
it("rejects distinct JSON request media schemas", () => {
  expect(() =>
    generate(
      spec({ ...media({ type: "string" }), "application/vendor+json": { schema: { type: "integer" } } }, {}),
      native,
    ),
  ).toThrow("Distinct JSON request schemas");
});
it("rejects distinct success status and media schemas", () => {
  expect(() =>
    generate(
      spec({}, { 200: { content: media({ type: "string" }) }, 201: { content: media({ type: "integer" }) } }),
      native,
    ),
  ).toThrow("Distinct JSON success schemas");
  expect(() =>
    generate(
      spec(
        {},
        {
          200: {
            content: { ...media({ type: "string" }), "application/vendor+json": { schema: { type: "integer" } } },
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
  expect(source).toContain('["200"]:');
  expect(source).toContain('["201"]:');
});
it("skips referenced binary bodies, binary responses and 204", () => {
  const content = media({ $ref: "#/components/schemas/Binary" });
  const source = generate(
    spec(
      content,
      { 200: { content }, 204: { content: media({ type: "string" }) } },
      { Binary: { type: "string", format: "binary" } },
    ),
    native,
  ).operations;
  expect(source).toContain('["send"]: {public: true, responses: {}}');
});
it("fails when the native export required by the registry is absent", () => {
  expect(() => generate(spec(media({ type: "string" }), {}), "")).toThrow("Missing native operation schema SendBody");
});
it("rejects schema-less JSON media", () => {
  expect(() => generate(spec({ "application/json": {} }, {}), native)).toThrow("Missing JSON schema");
});
