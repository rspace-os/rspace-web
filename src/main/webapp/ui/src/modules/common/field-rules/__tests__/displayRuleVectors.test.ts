import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, test } from "vitest";
import {
  createDisplayRule,
  type FieldDisplayRule,
  parseDisplayRule,
  type RuleField,
  serializeDisplayRule,
} from "../displayRule";
import { parseElnChoiceValues } from "../elnChoiceValues";
import { displayRuleVisibility } from "../evaluateDisplayRules";
import { validateDisplayRules } from "../validateDisplayRules";

/*
 * The vectors are shared with the Java FieldDisplayRuleVectorsTest so that the
 * server and the browser agree on every case.
 */
const VECTORS_PATH = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../../../../../../../../../src/test/resources/fieldrules/display-rule-vectors.json",
);

type VectorField = {
  key: string | null;
  type: string;
  rule?: FieldDisplayRule;
  allowedOptions?: Array<string>;
  selectedOptions?: Array<string>;
};

type Vectors = {
  rules: {
    valid: Array<{ name: string; json: string; canonical: string }>;
    invalid: Array<{ name: string; json: string }>;
  };
  visibility: Array<{ name: string; fields: Array<VectorField>; expected: Array<boolean> }>;
  validation: Array<{
    name: string;
    fields: Array<VectorField>;
    expectedErrors: Array<{ fieldIndex: number; code: string }>;
  }>;
  elnChoice: Array<{ data: string; expected: Array<string> }>;
};

const vectors = JSON.parse(fs.readFileSync(VECTORS_PATH, "utf8")) as Vectors;

const toRuleFields = (fields: Array<VectorField>): Array<RuleField> =>
  fields.map((field) => ({
    key: field.key,
    canBeSource: field.type === "RADIO" || field.type === "CHOICE",
    rule: field.rule ? parseDisplayRule(JSON.stringify(field.rule)) : null,
    allowedOptions: field.allowedOptions ?? [],
    selectedOptions: field.selectedOptions ?? [],
  }));

describe("display rule vectors shared with Java", () => {
  test.each(vectors.rules.valid)("parses and serialises canonically: $name", ({ json, canonical }) => {
    expect(serializeDisplayRule(parseDisplayRule(json))).toBe(canonical);
  });

  test.each(vectors.rules.invalid)("rejects: $name", ({ json }) => {
    expect(() => parseDisplayRule(json)).toThrow();
  });

  test.each(vectors.visibility)("visibility: $name", ({ fields, expected }) => {
    expect(displayRuleVisibility(toRuleFields(fields))).toEqual(expected);
  });

  test.each(vectors.validation)("validation: $name", ({ fields, expectedErrors }) => {
    expect(validateDisplayRules(toRuleFields(fields))).toEqual(expectedErrors);
  });

  test.each(vectors.elnChoice)("ELN choice data '$data'", ({ data, expected }) => {
    expect(parseElnChoiceValues(data)).toEqual(expected);
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
  const fields: Array<RuleField> = Array.from({ length }, (_unused, i) => ({
    key: chainKey(i),
    canBeSource: true,
    rule: i === length - 1 ? null : createDisplayRule(chainKey(i + 1), "IS_ANY_OF", ["Yes"]),
    allowedOptions: ["Yes"],
    selectedOptions: ["Yes"],
  }));

  expect(displayRuleVisibility(fields)).toEqual(new Array(length).fill(true));
  expect(validateDisplayRules(fields)).toEqual([]);
});
