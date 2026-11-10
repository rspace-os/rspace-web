import { describe, expect, it } from "vitest";
import { dropInvalidMeRules, hasPreset, togglePreset, withoutPresets } from "../presets";
import type { FilterExpression } from "../types";

type Item = { owner: string; name: string };
const mine = { kind: "comparison", field: "owner", operator: "equals", value: "me" } as const;
const other = { kind: "comparison", field: "name", operator: "equals", value: "microscope" } as const;

describe("query presets", () => {
  it("recognizes a rule supplied by the panel and removes only that conjunct", () => {
    const expression: FilterExpression<Item> = { kind: "and", children: [other, { kind: "and", children: [mine] }] };
    expect(hasPreset(expression, mine)).toBe(true);
    expect(togglePreset(expression, mine)).toEqual(other);
    expect(togglePreset(other, mine)).toEqual({ kind: "and", children: [other, mine] });
  });

  it("neither presses nor removes an OR-nested preset", () => {
    const expression: FilterExpression<Item> = { kind: "or", children: [mine, other] };
    expect(hasPreset(expression, mine)).toBe(false);
    expect(withoutPresets(expression, [mine])).toEqual(expression);
    expect(togglePreset(expression, mine)).toEqual({ kind: "and", children: [expression, mine] });
  });

  it("swaps exclusive presets without editing an OR subtree", () => {
    const replacement = { ...mine, value: "someone" };
    expect(
      togglePreset(withoutPresets({ kind: "and", children: [mine, other] }, [mine, replacement]), replacement),
    ).toEqual({ kind: "and", children: [other, replacement] });
  });

  it("drops invalid current-user values even within OR groups and reports them", () => {
    const expression: FilterExpression<Item> = { kind: "or", children: [{ ...mine, value: "42" }, other] };
    expect(dropInvalidMeRules(expression, ["owner"])).toEqual({ expression: other, dropped: ["owner"] });
    expect(dropInvalidMeRules(mine, ["owner"])).toEqual({ expression: mine, dropped: [] });
  });
});
