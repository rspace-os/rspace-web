import { describe, expect, it } from "vitest";
import { applyComputedValues, type ComputedContext } from "../computedValues";
import type { InventoryOperation } from "../operationsConfig";

function opWith(computed: InventoryOperation["effect"]["computed"]): InventoryOperation {
  return {
    key: "passage",
    labelKey: "operations.passage.label",
    minSelected: 1,
    maxSelected: 1,
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
