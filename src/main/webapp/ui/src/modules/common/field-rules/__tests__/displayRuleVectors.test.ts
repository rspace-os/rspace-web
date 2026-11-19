import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import * as v from "valibot";
import { describe, expect, test } from "vitest";
import {
  createDisplayRule,
  type FieldSnapshot,
  FieldSnapshotSchema,
  parseDisplayRule,
  SourceKindSchema,
  serializeDisplayRule,
  storedDisplayRuleLength,
} from "../displayRule";
import { parseElnChoiceValues } from "../elnChoiceValues";
import { displayRuleVisibility } from "../evaluateDisplayRules";
import { canonicalRuleNumber } from "../ruleValues";
import {
  DisplayRuleErrorSchema,
  MAX_DISPLAY_RULE_JSON_LENGTH,
  MAX_DISPLAY_RULE_VALUES,
  validateDisplayRules,
} from "../validateDisplayRules";

/*
 * The vectors are shared with the Java FieldDisplayRuleVectorsTest so that the
 * server and the browser agree on every case.
 */
const VECTORS_PATH = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../../../../../../../../../src/test/resources/fieldrules/display-rule-vectors.json",
);

/** A field as the vectors describe it, turned into a snapshot the way an adapter would. */
const VectorFieldSchema = v.pipe(
  v.object({
    key: v.nullable(v.string()),
    type: v.string(),
    rule: v.nullish(v.unknown()),
    allowedOptions: v.optional(v.array(v.string()), []),
    selectedOptions: v.optional(v.array(v.string()), []),
    sourceKind: v.optional(SourceKindSchema),
    value: v.optional(v.string()),
  }),
  v.transform(
    ({ key, type, rule, allowedOptions, selectedOptions, sourceKind, value }): FieldSnapshot =>
      v.parse(FieldSnapshotSchema, {
        key,
        canBeSource: type === "RADIO" || type === "CHOICE",
        rule: rule ?? null,
        allowedOptions,
        selectedOptions,
        // Absent in older vectors, so the defaults from canBeSource are exercised too.
        ...(sourceKind === undefined ? {} : { sourceKind }),
        ...(value === undefined ? {} : { value }),
      }),
  ),
);

const VectorsSchema = v.object({
  description: v.string(),
  rules: v.object({
    valid: v.array(v.object({ name: v.string(), json: v.string(), canonical: v.string() })),
    invalid: v.array(v.object({ name: v.string(), json: v.string() })),
  }),
  visibility: v.array(
    v.object({ name: v.string(), fields: v.array(VectorFieldSchema), expected: v.array(v.boolean()) }),
  ),
  validation: v.array(
    v.object({
      name: v.string(),
      fields: v.array(VectorFieldSchema),
      expectedErrors: v.array(DisplayRuleErrorSchema),
    }),
  ),
  limits: v.object({
    description: v.string(),
    maxValues: v.number(),
    maxJsonLength: v.number(),
    cases: v.array(
      v.object({
        name: v.string(),
        valueCount: v.number(),
        pad: v.string(),
        padCount: v.number(),
        tailLength: v.number(),
        jsonLength: v.number(),
        expectedErrors: v.array(DisplayRuleErrorSchema),
      }),
    ),
  }),
  elnChoice: v.array(v.object({ data: v.string(), expected: v.array(v.string()) })),
  canonicalNumbers: v.object({
    description: v.string(),
    cases: v.array(v.object({ value: v.string(), canonical: v.nullable(v.string()) })),
  }),
});

const vectors = v.parse(VectorsSchema, JSON.parse(fs.readFileSync(VECTORS_PATH, "utf8")));

describe("display rule vectors shared with Java", () => {
  // Compared as JSON values, not strings: each side serialises with its own JSON library.
  test.each(vectors.rules.valid)("serialises to the canonical JSON value: $name", ({ json, canonical }) => {
    expect(JSON.parse(serializeDisplayRule(parseDisplayRule(json)))).toEqual(JSON.parse(canonical));
  });

  test.each(vectors.rules.invalid)("rejects: $name", ({ json }) => {
    expect(() => parseDisplayRule(json)).toThrow(v.ValiError);
  });

  test.each(vectors.visibility)("visibility: $name", ({ fields, expected }) => {
    expect(displayRuleVisibility(fields)).toEqual(expected);
  });

  test.each(vectors.validation)("validation: $name", ({ fields, expectedErrors }) => {
    expect(validateDisplayRules(fields)).toEqual(expectedErrors);
  });

  test("limits match the shared vectors", () => {
    expect(MAX_DISPLAY_RULE_VALUES).toBe(vectors.limits.maxValues);
    expect(MAX_DISPLAY_RULE_JSON_LENGTH).toBe(vectors.limits.maxJsonLength);
  });

  test.each(vectors.limits.cases)(
    "limits: $name",
    ({ valueCount, pad, padCount, tailLength, jsonLength, expectedErrors }) => {
      const values = Array.from(
        { length: valueCount },
        (_unused, i) => pad.repeat(padCount) + String(i).padStart(5, "0"),
      );
      if (tailLength > 0) values.push("x".repeat(tailLength));
      const sourceKey = "00000000-0000-4000-8000-000000000001";
      const rule = createDisplayRule(sourceKey, "IS_ANY_OF", values);
      const fields: Array<FieldSnapshot> = [
        { key: sourceKey, canBeSource: true, rule: null, allowedOptions: values, selectedOptions: [] },
        {
          key: "00000000-0000-4000-8000-000000000002",
          canBeSource: false,
          rule,
          allowedOptions: [],
          selectedOptions: [],
        },
      ];

      expect(storedDisplayRuleLength(rule)).toBe(jsonLength);
      expect(validateDisplayRules(fields)).toEqual(expectedErrors);
    },
  );

  test.each(vectors.elnChoice)("ELN choice data '$data'", ({ data, expected }) => {
    expect(parseElnChoiceValues(data)).toEqual(expected);
  });

  test.each(vectors.canonicalNumbers.cases)("canonical number of '$value'", ({ value, canonical }) => {
    expect(canonicalRuleNumber(value)).toBe(canonical);
  });
});

/*
 * Generated rather than a vector: a chain far deeper than any real template,
 * listed dependent first, must resolve without exhausting the stack (the Java
 * test does the same).
 */
test("a deep chain listed dependent first is all shown and valid", () => {
  const length = 20_000;
  const chainKey = (i: number) => `00000000-0000-4000-8000-${String(i).padStart(12, "0")}`;
  const fields: Array<FieldSnapshot> = Array.from({ length }, (_unused, i) => ({
    key: chainKey(i),
    canBeSource: true,
    rule: i === length - 1 ? null : createDisplayRule(chainKey(i + 1), "IS_ANY_OF", ["Yes"]),
    allowedOptions: ["Yes"],
    selectedOptions: ["Yes"],
  }));

  expect(displayRuleVisibility(fields)).toEqual(new Array(length).fill(true));
  expect(validateDisplayRules(fields)).toEqual([]);
});
