import { afterEach, describe, expect, test, vi } from "vitest";
import { createDisplayRule, type FieldSnapshot } from "../displayRule";
import { DISPLAY_RULE_ERROR_CODES, validateDisplayRules } from "../validateDisplayRules";

const SOURCE_KEY = "00000000-0000-4000-8000-000000000001";

describe("validateDisplayRules", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  // Serialising escapes each non-ASCII unit to six characters, so huge rules must not reach it.
  test("rejects values that together exceed the budget without serialising the rule", () => {
    const values = ["é".repeat(20_000), "è".repeat(20_000)];
    const fields: Array<FieldSnapshot> = [
      { key: SOURCE_KEY, canBeSource: true, rule: null, allowedOptions: values, selectedOptions: [] },
      {
        key: "00000000-0000-4000-8000-000000000002",
        canBeSource: false,
        rule: createDisplayRule(SOURCE_KEY, "IS_ANY_OF", values),
        allowedOptions: [],
        selectedOptions: [],
      },
    ];
    const stringify = vi.spyOn(JSON, "stringify");

    expect(validateDisplayRules(fields)).toEqual([{ fieldIndex: 1, code: DISPLAY_RULE_ERROR_CODES.ruleTooLong }]);
    expect(stringify).not.toHaveBeenCalled();
  });
});
