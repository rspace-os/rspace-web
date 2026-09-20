import { faFlask } from "@fortawesome/free-solid-svg-icons/faFlask";
import { describe, expect, it } from "vitest";
import { applyComputedValues, type ComputedContext } from "../computedValues";
import type { InventoryOperation } from "../operations";

function opWith(computed: InventoryOperation["effect"]["computed"]): InventoryOperation {
  return {
    key: "passage",
    labelKey: "operations.passage.label",
    icon: faFlask,
    inputs: [],
    confirmSummary: [],
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

  it("matches the keyed field wherever it sits among the parent's fields", () => {
    const keyed = { name: "Passagenummer", content: "4", operationFieldKey: "operations.passage.numberField" };
    const decoy = { name: "Passage number", content: "99", operationFieldKey: null };
    expect(applyComputedValues(opWith(passageComputed), ctx([keyed, decoy])).passageNumber).toEqual(5);
  });
});
