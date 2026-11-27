import { Temporal } from "@js-temporal/polyfill";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ResolvedCollectionConfig, ResolvedFieldConfig } from "@/modules/common/collection/config";
import { valueBehaviourOf } from "../fieldValueBehaviour";
import type { FilterValue } from "../types";
import { comparisonNodes, isActive, parsedValue } from "../utils/filterDrafts";

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
  const instant = Temporal.Instant.from("2026-03-01T10:00:00Z");

  it.each([
    ["an instant on a text field", instant, field({ type: "text" }), "2026-03-01T10:00:00.000Z"],
    ["an instant on an unknown field", instant, undefined, "2026-03-01T10:00:00.000Z"],
    ["a boolean on a dateTime field", true, field({ type: "dateTime" }), "true"],
  ] as const)("drafts %s", (_, value, f, expected) => {
    expect(draft(value, f)).toBe(expected);
  });

  it("keeps each list entry's original instant while its draft is unchanged", () => {
    const dateTime = field({ type: "dateTime" });
    const [first, second] = [
      Temporal.Instant.from("2026-10-25T00:30:00.000Z"),
      Temporal.Instant.from("2026-10-25T01:30:00.000Z"),
    ];
    const [rule] = comparisonNodes<Row>(
      { kind: "comparison", field: "value", operator: "in", value: [first, second] },
      configWith(dateTime),
    );
    const parsed = parsedValue({ ...rule, operator: "in" }, dateTime) as readonly unknown[];
    expect(parsed[0]).toBe(first);
    expect(parsed[1]).toBe(second);
  });

  it("parses an unchanged string original into its instant", () => {
    const dateTime = field({ type: "dateTime" });
    const original = "2026-03-01T10:00:00.000Z";
    const [rule] = comparisonNodes<Row>(
      { kind: "comparison", field: "value", operator: "equals", value: original },
      configWith(dateTime),
    );
    const parsed = parsedValue(rule, dateTime);
    expect(parsed).toBeInstanceOf(Temporal.Instant);
    expect(String(parsed)).toBe("2026-03-01T10:00:00Z");
  });

  it("does not apply a rule that is still picking a custom field", () => {
    const rule = { id: 1, field: "value", operator: "exists", value: "" } as const;
    expect(isActive<Row>(rule)).toBe(true);
    expect(isActive<Row>({ ...rule, pickingGroup: "" })).toBe(false);
  });

  describe("in Europe/Berlin", () => {
    afterEach(() => {
      vi.restoreAllMocks();
    });
    const dateTime = field({ type: "dateTime" });
    const edited = (value: string) => {
      vi.spyOn(Temporal.Now, "timeZoneId").mockReturnValue("Europe/Berlin");
      return parsedValue<Row>({ id: 1, field: "value", operator: "equals", value }, dateTime);
    };

    it("drafts the local clock with milliseconds", () => {
      vi.spyOn(Temporal.Now, "timeZoneId").mockReturnValue("Europe/Berlin");
      expect(draft(Temporal.Instant.from("2026-10-25T01:30:00.250Z"), dateTime)).toBe("2026-10-25T02:30:00.250");
    });

    it("moves an edited time in the spring gap forward", () => {
      expect(String(edited("2026-03-29T02:30:00.000"))).toBe("2026-03-29T01:30:00Z");
    });

    it("resolves an edited time in the autumn overlap to its earlier instant", () => {
      expect(String(edited("2026-10-25T02:30:00.000"))).toBe("2026-10-25T00:30:00Z");
    });

    it("keeps a partial draft that is not a date and time as text", () => {
      expect(edited("2026-13-01T10:00")).toBe("2026-13-01T10:00");
    });
  });
});
