import { autorun } from "mobx";
import { describe, expect, it, vi } from "vitest";
import { ruleGraphFor } from "@/modules/common/field-editor/ruleEditing";
import { createDisplayRule } from "@/modules/common/field-rules/displayRule";
import { makeMockTemplate } from "@/stores/models/__tests__/TemplateModel/mocking";
import type FieldModel from "@/stores/models/FieldModel";
import type { FieldModelAttrs } from "@/stores/models/FieldModel";
import { templateRuleFields } from "../templateRuleFields";

vi.mock("@/common/InvApiService", () => ({ default: {} }));
vi.mock("@/stores/stores/getRootStore", () => ({
  default: () => ({
    uiStore: { setPageNavigationConfirmation: () => {}, setDirty: () => {} },
    unitStore: { getUnit: () => ({ label: "ml" }), assertValidUnitId: () => {} },
  }),
}));

const keyOf = (i: number) => `6f1c2e0a-0000-4000-8000-${String(i).padStart(12, "0")}`;

/** 150 saved fields: even ones are radios, each odd one is shown when the radio above it is Yes. */
const largeTemplate = () =>
  makeMockTemplate({
    fields: Array.from(
      { length: 150 },
      (_, i): FieldModelAttrs => ({
        id: i + 1,
        globalId: `SF${i + 1}`,
        name: `Field ${i}`,
        type: i % 2 === 0 ? "radio" : "string",
        definition: i % 2 === 0 ? { options: ["Yes", "No"] } : null,
        selectedOptions: null,
        columnIndex: i,
        attachment: null,
        mandatory: false,
        fieldKey: keyOf(i),
        displayRule: i % 2 === 1 ? createDisplayRule(keyOf(i - 1), "IS_ANY_OF", ["Yes"]) : null,
      }),
    ),
  });

describe("templateRuleFields", () => {
  /*
   * The old rules panel took about 3 s per keystroke on a 150-field template,
   * because it re-ran the validator for every candidate of every rule on each
   * render. The candidates now come from one graph per rule-relevant change.
   */
  it("keeps the same list, and so the same candidate graph, while a field of 150 is renamed", () => {
    const template = largeTemplate();
    const lists: Array<ReadonlyArray<unknown>> = [];
    const dispose = autorun(() => {
      lists.push(templateRuleFields(template));
    });
    const graph = ruleGraphFor(templateRuleFields(template));

    (template.fields[0] as FieldModel).setAttributesDirty({ name: "Field 0, renamed" });

    expect(lists).toHaveLength(1);
    expect(ruleGraphFor(templateRuleFields(template))).toBe(graph);
    expect(templateRuleFields(template)[0].name).toBe("Field 0, renamed");
    dispose();
  });

  it("gives a new list when a rule, an option or a field changes", () => {
    const template = largeTemplate();
    const lists: Array<ReadonlyArray<unknown>> = [];
    const dispose = autorun(() => {
      lists.push(templateRuleFields(template));
    });

    (template.fields[1] as FieldModel).setDisplayRule(null);
    (template.fields[0] as FieldModel).renameOption(0, "Yes!");
    template.addField({ type: "string", selectedOptions: [], columnIndex: null, attachment: null, mandatory: false });

    expect(lists).toHaveLength(4);
    expect(lists[3]).toHaveLength(151);
    dispose();
  });

  it("works out every field's candidates on 150 fields quickly", () => {
    const template = largeTemplate();
    const start = performance.now();
    const graph = ruleGraphFor(templateRuleFields(template));
    for (const field of templateRuleFields(template)) graph.sourcesFor(field.key);
    // a generous bound for slow CI machines; locally it takes a few milliseconds
    expect(performance.now() - start).toBeLessThan(100);
    // radios and text fields are all readable: a text field may read any other field, and a radio any
    // but itself and the text field that reads it
    expect(graph.sourcesFor(keyOf(1))).toHaveLength(149);
    expect(graph.sourcesFor(keyOf(0))).toHaveLength(148);
  });
});

describe("templateRuleFields source kinds", () => {
  it("passes the kind each type gives a rule to read, and the type menu's label", () => {
    const template = makeMockTemplate({
      fields: (["radio", "string", "number", "text"] as const).map(
        (type, i): FieldModelAttrs => ({
          id: i + 1,
          globalId: `SF${i + 1}`,
          name: type,
          type,
          definition: type === "radio" ? { options: ["Yes"] } : null,
          selectedOptions: null,
          columnIndex: i,
          attachment: null,
          mandatory: false,
          fieldKey: keyOf(i),
        }),
      ),
    });
    expect(templateRuleFields(template).map((field) => field.sourceKind)).toEqual([
      "OPTIONS",
      "TEXT",
      "NUMBER",
      "NONE",
    ]);
    expect(templateRuleFields(template).map((field) => field.typeLabel)).toEqual([
      "Radio",
      "Plain text",
      "Number",
      "Formatted text",
    ]);
  });
});
