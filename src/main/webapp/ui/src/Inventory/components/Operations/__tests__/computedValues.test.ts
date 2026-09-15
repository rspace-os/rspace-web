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
    // The Passage "stuck at 1" bug: the passage number is a user-added custom field, so it is in the
    // parent's extraFields, not its template-defined `fields`. A caller that passes only `fields`
    // misses it and increment falls back to start (1); both lists have to be combined so increment
    // sees "2" -> 3.
    const result = applyComputedValues(opWith(passageComputed), {
      parentFields: [...[], ...[{ name: "Passage number", content: "2" }]],
      values: {},
      resolveFieldName,
    });
    expect(result.passageNumber).toBe(3);
  });
});

/**
 * A generated field's NAME is a localized resolution of its definition key, so name matching means
 * matching whatever the current locale says today. The key is exact and locale-independent, which is
 * what keeps a Passage lineage intact rather than forking it into one counter per locale (F6).
 */
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
        // A user's own field that happens to carry the current locale's wording.
        { name: "Passage number", content: "99", operationFieldKey: null },
        // The field the previous Passage actually generated.
        { name: "Passagenummer", content: "4", operationFieldKey: "operations.passage.numberField" },
      ]),
    );
    expect(values.passageNumber).toEqual(5);
  });

  it("keeps incrementing when the stored name no longer matches the current locale's wording", () => {
    // The exact regression: with only name matching this found nothing and restarted at 1, which
    // silently forks the culture's lineage instead of continuing it.
    const values = applyComputedValues(
      opWith(passageComputed),
      ctx([{ name: "Passagenummer", content: "7", operationFieldKey: "operations.passage.numberField" }]),
    );
    expect(values.passageNumber).toEqual(8);
  });

  it("still matches a hand-created field by name when it carries no key", () => {
    // The name fallback is permanent, not migration cover: this is how the first Passage of an
    // existing culture picks up the count the user has been keeping by hand.
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
    // spread of both arrays passes such a test with the key matching removed entirely (parallel
    // review, I11). This goes through the lookup instead, with a same-named decoy in the other list
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
