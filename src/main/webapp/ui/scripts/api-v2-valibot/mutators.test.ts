import type { OpenApiDocument } from "orval";
import { describe, expect, it } from "vitest";
import { mutatorOverrides } from "./mutators.ts";
import { fixture } from "./test-helper.ts";

const json = { content: { "application/json": { schema: { type: "object" } } } };
const png = { content: { "image/png": { schema: { type: "string", format: "binary" } } } };
const noContent = { description: "No content" };
const etag = { headers: { ETag: { schema: { type: "string" } } } };

function document(responses: Record<string, unknown>, components: Record<string, unknown> = {}): OpenApiDocument {
  return fixture({
    components: { responses: components },
    paths: { "/api/v2/items": { get: { operationId: "readItem", responses } } },
  });
}
const rules = { headers: [], etagExclusions: [] };

describe("mutator overrides", () => {
  it("selects apiV2BlobFetch for binary-only successes", () => {
    expect(mutatorOverrides(document({ 200: png }), rules)).toEqual({ readItem: "apiV2BlobFetch" });
  });

  it("keeps apiV2Fetch for a JSON success declared through a response reference", () => {
    const spec = document({ 200: { $ref: "#/components/responses/CountResult" } }, { CountResult: json });
    expect(mutatorOverrides(spec, rules)).toEqual({});
  });

  it("keeps apiV2Fetch for a 204-only operation", () => {
    expect(mutatorOverrides(document({ 204: noContent }), rules)).toEqual({});
  });

  it("fails on a bodyless success other than 204", () => {
    expect(() => mutatorOverrides(document({ 200: noContent }), rules)).toThrow("only 204 may be bodyless");
  });

  it("fails on mixed JSON and binary successes", () => {
    expect(() => mutatorOverrides(document({ 200: json, 201: png }), rules)).toThrow("mixes JSON and binary");
    expect(() => mutatorOverrides(document({ 200: { content: { ...json.content, ...png.content } } }), rules)).toThrow(
      "mixes JSON and binary",
    );
  });

  it("fails on a binary success combined with a 204", () => {
    expect(() => mutatorOverrides(document({ 200: png, 204: noContent }), rules)).toThrow("bodyless 204");
  });

  it("fails on an ETag that is neither read nor excluded", () => {
    const spec = document({ 200: { ...json, ...etag } });
    expect(() => mutatorOverrides(spec, rules)).toThrow("declares an ETag");
    expect(mutatorOverrides(spec, { headers: ["readItem"], etagExclusions: [] })).toEqual({
      readItem: "apiV2HeadersFetch",
    });
    expect(mutatorOverrides(spec, { headers: [], etagExclusions: ["readItem"] })).toEqual({});
  });

  it("fails on rules for unknown operations", () => {
    expect(() => mutatorOverrides(document({ 200: json }), { headers: ["missing"], etagExclusions: [] })).toThrow(
      "unknown operation: missing",
    );
  });
});
