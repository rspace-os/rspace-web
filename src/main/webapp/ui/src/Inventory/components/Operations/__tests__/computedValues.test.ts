import { describe, expect, it } from "vitest";
import { applyComputedValues, type ComputedContext } from "../computedValues";
import type { InventoryOperation } from "../operationsConfig";

function opWith(computed: InventoryOperation["effect"]["computed"]): InventoryOperation {
  return {
    key: "passage",
    labelKey: "operations.passage.label",
    documentationStep: true,
    inputs: [],
    effect: {
      nameFrom: "sampleName",
      countFrom: "count",
      eachAmountFrom: "eachAmount",
      links: [],
      computed,
    },
  };
}

const resolveFieldName = (key: string): string => (key === "operations.passage.numberField" ? "Passage number" : key);

const passageComputed: InventoryOperation["effect"]["computed"] = [
  {
    fn: "increment",
    into: "passageNumber",
    args: {
      current: { parentSampleField: "operations.passage.numberField" },
      start: { constant: 1 },
    },
  },
];

describe("applyComputedValues", () => {
  it("increments a parent-sample field, writing the result into the target input", () => {
    const ctx: ComputedContext = {
      parentFields: [{ name: "Passage number", content: "2" }],
      values: {},
      resolveFieldName,
    };
    expect(applyComputedValues(opWith(passageComputed), ctx).passageNumber).toBe(3);
  });

  it("uses start when the parent sample lacks the field", () => {
    const ctx: ComputedContext = {
      parentFields: [{ name: "Something else", content: "9" }],
      values: {},
      resolveFieldName,
    };
    expect(applyComputedValues(opWith(passageComputed), ctx).passageNumber).toBe(1);
  });

  it("leaves the values untouched when there are no computed values", () => {
    const ctx: ComputedContext = { parentFields: [], values: { sampleName: "x" }, resolveFieldName };
    expect(applyComputedValues(opWith(undefined), ctx)).toEqual({ sampleName: "x" });
  });

  it("sources an argument from another input value", () => {
    const op = opWith([
      { fn: "increment", into: "next", args: { current: { input: "seed" }, start: { constant: 0 } } },
    ]);
    const ctx: ComputedContext = { parentFields: [], values: { seed: 41 }, resolveFieldName };
    expect(applyComputedValues(op, ctx).next).toBe(42);
  });

  it("chains: a later computed value reads an earlier one's result (config order)", () => {
    const op = opWith([
      { fn: "increment", into: "first", args: { current: { constant: 1 }, start: { constant: 0 } } },
      { fn: "increment", into: "second", args: { current: { input: "first" }, start: { constant: 0 } } },
    ]);
    const result = applyComputedValues(op, { parentFields: [], values: {}, resolveFieldName });
    expect(result.first).toBe(2);
    expect(result.second).toBe(3);
  });
});

describe("parentFields drawn from both of the parent's field lists", () => {
  it("finds a parentSampleField that came from extraFields, so Passage increments X to X+1 (regression)", () => {
    const result = applyComputedValues(opWith(passageComputed), {
      parentFields: [...[], ...[{ name: "Passage number", content: "2" }]],
      values: {},
      resolveFieldName,
    });
    expect(result.passageNumber).toBe(3);
  });
});

describe("parentSampleField matching", () => {
  const ctx = (parentFields: ComputedContext["parentFields"]): ComputedContext => ({
    parentFields,
    values: {},
    resolveFieldName,
  });

  it("matches by operation key in preference to a same-named decoy", () => {
    const values = applyComputedValues(
      opWith(passageComputed),
      ctx([
        { name: "Passage number", content: "99", operationFieldKey: null },
        { name: "Passagenummer", content: "4", operationFieldKey: "operations.passage.numberField" },
      ]),
    );
    expect(values.passageNumber).toEqual(5);
  });

  it("keeps incrementing when the stored name no longer matches the current locale's wording", () => {
    const values = applyComputedValues(
      opWith(passageComputed),
      ctx([{ name: "Passagenummer", content: "7", operationFieldKey: "operations.passage.numberField" }]),
    );
    expect(values.passageNumber).toEqual(8);
  });

  it("still matches a hand-created field by name when it carries no key", () => {
    const values = applyComputedValues(opWith(passageComputed), ctx([{ name: "Passage number", content: "3" }]));
    expect(values.passageNumber).toEqual(4);
  });

  it("falls back to the start value when neither key nor name is present", () => {
    const values = applyComputedValues(opWith(passageComputed), ctx([{ name: "Something else", content: "3" }]));
    expect(values.passageNumber).toEqual(1);
  });

  it("matches a keyed field gathered from either of the parent's two field lists", () => {
    // Both lists must be searched: a Passage number can be a template field or an ad-hoc custom
    // one. Asserting that a gather helper merely preserves the property would prove nothing - a
    // spread of both arrays passes such a test with the key matching removed entirely. This goes
    // through the lookup instead, with a same-named decoy in the other list
    // so only key matching can produce the right answer.
    for (const listName of ["fields", "extraFields"] as const) {
      const keyed = {
        name: "Passagenummer",
        content: "4",
        operationFieldKey: "operations.passage.numberField",
      };
      const decoy = { name: "Passage number", content: "99", operationFieldKey: null };
      const fields = listName === "fields" ? [keyed] : [decoy];
      const extraFields = listName === "extraFields" ? [keyed] : [decoy];
      const values = applyComputedValues(opWith(passageComputed), {
        parentFields: [...fields, ...extraFields],
        values: {},
        resolveFieldName,
      });
      expect(values.passageNumber, `keyed field in ${listName}`).toEqual(5);
    }
  });
});
