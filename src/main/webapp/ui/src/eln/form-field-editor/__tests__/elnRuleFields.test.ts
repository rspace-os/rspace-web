import { describe, expect, it } from "vitest";
import { ruleGraphFor } from "@/modules/common/field-editor/ruleEditing";
import { createDisplayRule } from "@/modules/common/field-rules/displayRule";
import { type EditorField, removeOption, updateField } from "../editorModel";
import { elnRuleFields } from "../elnRuleFields";

const keyOf = (i: number) => `6f1c2e0a-0000-4000-8000-${String(i).padStart(12, "0")}`;

/** 150 saved fields: even ones are radios, each odd one is shown when the radio above it is Yes. */
const largeForm = (): Array<EditorField> =>
  Array.from({ length: 150 }, (_, i): EditorField => {
    const base = { key: keyOf(i), id: i + 1, name: `Field ${i}`, mandatory: false, canDelete: true };
    return i % 2 === 0
      ? {
          ...base,
          rule: null,
          type: "Radio",
          options: ["Yes", "No"],
          defaultValue: "",
          showAsPickList: false,
          sortAlphabetic: false,
        }
      : {
          ...base,
          rule: createDisplayRule(keyOf(i - 1), "IS_ANY_OF", ["Yes"]),
          type: "String",
          password: false,
          defaultValue: "",
        };
  });

const namesOf = (fields: ReadonlyArray<EditorField>) => (key: string) =>
  fields.find((field) => field.key === key)?.name ?? "";

describe("elnRuleFields", () => {
  /*
   * On a 150-field form the old rules panel took about 3 s per keystroke: it
   * worked out every rule's candidates again on each render. The candidates
   * now come from one graph per rule field list, which a rename keeps.
   */
  it("keeps the same list, and so the same candidate graph, while a field of 150 is renamed", () => {
    let fields = largeForm();
    const list = elnRuleFields(fields, (key) => namesOf(fields)(key));
    const graph = ruleGraphFor(list);

    fields = updateField(fields, keyOf(0), (field) => ({ ...field, name: "Is this a GMO?" }));
    const renamed = elnRuleFields(fields, (key) => namesOf(fields)(key), list);

    expect(renamed).toBe(list);
    expect(ruleGraphFor(renamed)).toBe(graph);
    expect(renamed[0].name).toBe("Is this a GMO?");
  });

  it("gives a new list when a rule or an option changes", () => {
    const fields = largeForm();
    const list = elnRuleFields(fields, namesOf(fields));

    const withoutRule = updateField(fields, keyOf(1), (field) => ({ ...field, rule: null }));
    const withoutOption = removeOption(fields, keyOf(0), 1);

    expect(elnRuleFields(withoutRule, namesOf(withoutRule), list)).not.toBe(list);
    expect(elnRuleFields(withoutOption, namesOf(withoutOption), list)).not.toBe(list);
  });

  it("works out every field's candidates on 150 fields quickly", () => {
    const fields = largeForm();
    const start = performance.now();
    const list = elnRuleFields(fields, namesOf(fields));
    const graph = ruleGraphFor(list);
    for (const field of list) graph.sourcesFor(field.key);
    // a generous bound for slow CI machines; locally it takes a few milliseconds
    expect(performance.now() - start).toBeLessThan(100);
    // a text field may read any of the 75 radios, and a radio any radio but itself
    expect(graph.sourcesFor(keyOf(1))).toHaveLength(75);
    expect(graph.sourcesFor(keyOf(0))).toHaveLength(74);
  });
});
