import { Temporal } from "@js-temporal/polyfill";
import { describe, expect, it } from "vitest";
import { dropInvalidMeRules, filterValueEquals, hasPreset, togglePreset, withoutPresets } from "../presets";
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
    const replacement = { ...mine, value: "someone" };
    expect(withoutPresets({ kind: "and", children: [mine, other] }, [replacement, mine])).toEqual(other);
  });

  it("neither presses nor removes an OR-nested preset", () => {
    const expression: FilterExpression<Item> = { kind: "or", children: [mine, other] };
    expect(hasPreset(expression, mine)).toBe(false);
    expect(withoutPresets(expression, [mine])).toEqual(expression);
    expect(togglePreset(expression, mine)).toEqual({ kind: "and", children: [expression, mine] });
  });

  it("drops invalid current-user values even within OR groups and reports them", () => {
    const expression: FilterExpression<Item> = { kind: "or", children: [{ ...mine, value: "42" }, other] };
    expect(dropInvalidMeRules(expression, ["owner"])).toEqual({ expression: other, dropped: ["owner"] });
    expect(dropInvalidMeRules(mine, ["owner"])).toEqual({ expression: mine, dropped: [] });
  });
});

describe("filterValueEquals", () => {
  const instant = (value: string) => Temporal.Instant.from(value);

  it("compares instants by time, whatever their offset", () => {
    expect(filterValueEquals(instant("2026-08-01T10:00:00Z"), instant("2026-08-01T12:00:00+02:00"))).toBe(true);
    expect(filterValueEquals(instant("2026-08-01T10:00:00Z"), instant("2026-08-01T10:00:00.001Z"))).toBe(false);
    expect(filterValueEquals(instant("2026-08-01T10:00:00Z"), "2026-08-01T10:00:00Z")).toBe(false);
  });

  it("compares lists element by element", () => {
    const list = [instant("2026-08-01T10:00:00Z"), instant("2026-08-02T10:00:00Z")];
    expect(filterValueEquals(list, [instant("2026-08-01T10:00:00Z"), instant("2026-08-02T10:00:00Z")])).toBe(true);
    expect(filterValueEquals(list, [...list].reverse())).toBe(false);
    expect(filterValueEquals(list, list.slice(0, 1))).toBe(false);
    expect(filterValueEquals(["a", 1], ["a", 1])).toBe(true);
    expect(filterValueEquals(["a"], "a")).toBe(false);
  });

  it("matches an instant list preset built separately", () => {
    const rule = { kind: "comparison", field: "name", operator: "in" } as const;
    const expression: FilterExpression<Item> = { ...rule, value: [instant("2026-08-01T10:00:00Z")] };
    expect(hasPreset(expression, { ...rule, value: [instant("2026-08-01T10:00:00.000Z")] })).toBe(true);
  });
});
