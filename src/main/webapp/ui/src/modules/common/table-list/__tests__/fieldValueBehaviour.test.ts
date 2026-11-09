import { describe, expect, it } from "vitest";
import type { ResolvedCollectionConfig, ResolvedFieldConfig } from "@/modules/common/collection/config";
import { valueBehaviourOf } from "../fieldValueBehaviour";
import type { FilterValue } from "../types";
import { comparisonNodes, parsedValue } from "../utils/filterDrafts";

type Row = { value: unknown };
const capabilities = { sortable: true, filterOperators: [], supportsWildcards: false };
const label = { text: "Value" };
const field = (extra: Partial<ResolvedFieldConfig<Row>> & Pick<ResolvedFieldConfig<Row>, "type">) =>
  ({ name: "value", derivedLabel: label, capabilities, ...extra }) as ResolvedFieldConfig<Row>;
const custom = (type: "date" | "time") => ({
  origin: {
    kind: "runtimeField" as const,
    groupLabelKey: "tableList.fieldGroups.customFields" as const,
    runtimeValueType: type,
  },
});

describe("valueBehaviourOf", () => {
  it.each([
    ["text", field({ type: "text" }), "text", undefined, undefined],
    ["number", field({ type: "number" }), "number", undefined, "number"],
    ["dateTime", field({ type: "dateTime" }), "datetime-local", "any", "dateTime"],
    ["text with a custom date", field({ type: "text", ...custom("date") }), "date", undefined, "date"],
    ["text with a custom time", field({ type: "text", ...custom("time") }), "time", undefined, "time"],
    ["dateTime with a custom date", field({ type: "dateTime", ...custom("date") }), "date", "any", "dateTime"],
    ["select with a custom date", field({ type: "select", options: [], ...custom("date") }), "date", undefined, "date"],
    ["number with a custom date", field({ type: "number", ...custom("date") }), "number", undefined, "number"],
    ["an unknown field", undefined, "text", undefined, undefined],
  ] as const)("resolves the input and display of %s", (_, input, inputType, inputStep, displayFormat) => {
    const resolved = valueBehaviourOf(input);
    expect([resolved.inputType, resolved.behaviour.inputStep, resolved.displayFormat]).toEqual([
      inputType,
      inputStep,
      displayFormat,
    ]);
  });
});

describe("filter drafts", () => {
  const configWith = (f: ResolvedFieldConfig<Row> | undefined) =>
    ({ fields: f ? [f] : [] }) as unknown as ResolvedCollectionConfig<Row>;
  const draft = (value: FilterValue, f: ResolvedFieldConfig<Row> | undefined) =>
    comparisonNodes<Row>({ kind: "comparison", field: "value", operator: "equals", value }, configWith(f))[0].value;
  const instant = new Date("2026-03-01T10:00:00.000Z");

  it.each([
    ["a Date on a text field", instant, field({ type: "text" }), instant.toISOString()],
    ["a Date on an unknown field", instant, undefined, instant.toISOString()],
    ["a boolean on a dateTime field", true, field({ type: "dateTime" }), "true"],
  ] as const)("drafts %s", (_, value, f, expected) => {
    expect(draft(value, f)).toBe(expected);
  });

  it("keeps each list entry's original instant while its draft is unchanged", () => {
    const dateTime = field({ type: "dateTime" });
    const [first, second] = [new Date("2026-10-25T00:30:00.000Z"), new Date("2026-10-25T01:30:00.000Z")];
    const [rule] = comparisonNodes<Row>(
      { kind: "comparison", field: "value", operator: "in", value: [first, second] },
      configWith(dateTime),
    );
    expect(parsedValue({ ...rule, operator: "in" }, dateTime)).toEqual([first, second]);
  });

  it("parses an unchanged string original into its instant", () => {
    const dateTime = field({ type: "dateTime" });
    const original = "2026-03-01T10:00:00.000Z";
    const [rule] = comparisonNodes<Row>(
      { kind: "comparison", field: "value", operator: "equals", value: original },
      configWith(dateTime),
    );
    const parsed = parsedValue(rule, dateTime);
    expect(parsed).toBeInstanceOf(Date);
    expect((parsed as Date).getTime()).toBe(Date.parse(original));
  });
});
