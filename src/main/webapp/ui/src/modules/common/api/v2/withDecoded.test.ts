import { Temporal } from "@js-temporal/polyfill";
import * as v from "valibot";
import { describe, expect, it } from "vitest";
import { withDecoded, withoutDecoding } from "./withDecoded";

const instant = v.pipe(
  v.string(),
  v.isoTimestamp(),
  v.transform((value) => Temporal.Instant.from(value)),
);

const decode = <TSchema extends v.GenericSchema>(schema: TSchema, json: string) => {
  const raw: unknown = JSON.parse(json);
  return withDecoded(raw, v.parse(schema, raw));
};

describe("withDecoded", () => {
  it("decodes date-times inside arrays and records and leaves undeclared members alone", () => {
    const schema = v.object({
      docs: v.array(v.object({ start: instant })),
      byName: v.record(v.string(), v.object({ at: instant })),
    });
    expect(
      decode(
        schema,
        '{"docs":[{"start":"2026-01-01T00:00:00Z","note":"2026-01-02T00:00:00Z"}],"byName":{"a":{"at":"2026-01-03T00:00:00Z"}}}',
      ),
    ).toEqual({
      docs: [{ start: Temporal.Instant.from("2026-01-01T00:00:00Z"), note: "2026-01-02T00:00:00Z" }],
      byName: { a: { at: Temporal.Instant.from("2026-01-03T00:00:00Z") } },
    });
  });

  it("keeps a null date-time and never adds a parse default", () => {
    const schema = v.object({
      endedAt: v.nullable(instant),
      startedAt: v.optional(instant, "2026-01-01T00:00:00Z"),
    });
    expect(decode(schema, '{"endedAt":null}')).toEqual({ endedAt: null });
  });

  it("keeps an own __proto__ member a plain member", () => {
    const schema = v.looseObject({ at: instant });
    const decoded = decode(schema, '{"__proto__":{"polluted":true},"at":"2026-01-01T00:00:00Z"}');
    expect(Object.getPrototypeOf(decoded)).toBe(Object.prototype);
    expect(Object.getOwnPropertyDescriptor(decoded, "__proto__")?.value).toEqual({ polluted: true });
    expect(decoded).toHaveProperty("at", Temporal.Instant.from("2026-01-01T00:00:00Z"));
  });
});

describe("withoutDecoding", () => {
  it("accepts a schema that decodes nothing and rejects one that decodes a date-time at compile time", () => {
    const plain = v.object({ id: v.number() });
    expect(withoutDecoding(plain)).toBe(plain);
    // @ts-expect-error A schema that decodes a date-time cannot re-parse decoded transport output.
    withoutDecoding(v.object({ at: instant }));
  });
});
