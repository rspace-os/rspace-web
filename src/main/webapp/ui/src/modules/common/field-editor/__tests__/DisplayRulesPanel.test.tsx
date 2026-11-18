import { describe, expect, it, vi } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import { ThemeProvider } from "@mui/material/styles";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { expectAccessible } from "@/__tests__/accessibility";
import { createRealI18nWrapper, renderWithRealI18n } from "@/__tests__/helpers/realI18n";
import { createDisplayRule } from "@/modules/common/field-rules/displayRule";
import common from "@/modules/common/i18n/locales/en-US/common.json";
import materialTheme from "@/theme";
import DisplayRulesPanel from "../DisplayRulesPanel";
import type { RuleChange, RuleEditorField } from "../ruleEditing";

const gmo: RuleEditorField = {
  key: "gmo",
  name: "Is this a GMO?",
  canBeSource: true,
  options: ["Yes", "No"],
  rule: null,
};
const level: RuleEditorField = {
  key: "level",
  name: "Containment level",
  canBeSource: true,
  options: ["CL1", "CL3"],
  rule: createDisplayRule("gmo", "IS_ANY_OF", ["Yes"]),
};
const permit: RuleEditorField = { key: "permit", name: "GMO permit", canBeSource: false, options: [], rule: null };
const notes: RuleEditorField = { key: "notes", name: "Notes", canBeSource: false, options: [], rule: null };

/** Applies changes the way a host model would, so the panel sees its own edits. */
function Harness({
  initial,
  onChange,
  editable = true,
}: {
  initial: Array<RuleEditorField>;
  onChange: (changes: ReadonlyArray<RuleChange>) => void;
  editable?: boolean;
}) {
  const [fields, setFields] = useState(initial);
  return (
    <ThemeProvider theme={materialTheme}>
      <DisplayRulesPanel
        fields={fields}
        editable={editable}
        onChange={(changes) => {
          onChange(changes);
          setFields((current) =>
            current.map((field) => {
              const change = changes.find(({ key }) => key === field.key);
              return change ? { ...field, rule: change.rule } : field;
            }),
          );
        }}
      />
    </ThemeProvider>
  );
}

const renderPanel = (initial: Array<RuleEditorField>, onChange = vi.fn(), editable = true) =>
  renderWithRealI18n(<Harness initial={initial} onChange={onChange} editable={editable} />, {
    resources: { common },
    defaultNS: "common",
  });

describe("DisplayRulesPanel", () => {
  it("lists identical rules once, with every field they show", async () => {
    await renderPanel([gmo, { ...permit, rule: level.rule }, { ...notes, rule: level.rule }]);
    const rule = screen.getByRole("listitem", { name: "When Is this a GMO? is Yes" });
    expect(within(rule).getByRole("button", { name: "GMO permit" })).toBeInTheDocument();
    expect(within(rule).getByRole("button", { name: "Notes" })).toBeInTheDocument();
    expect(screen.getAllByRole("listitem", { name: /^When/ })).toHaveLength(1);
  });

  it("builds a rule from fields that have never been saved, and keeps focus on the control used", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    await renderPanel([gmo, permit], onChange);

    await user.click(screen.getByRole("button", { name: "Add rule" }));
    await user.click(screen.getByRole("combobox", { name: "Options" }));
    await user.click(screen.getByRole("option", { name: "Yes" }));
    const targets = screen.getByRole("combobox", { name: "Show these fields" });
    await user.click(targets);
    await user.click(screen.getByRole("option", { name: "GMO permit" }));

    expect(onChange).toHaveBeenLastCalledWith([
      { key: "permit", rule: createDisplayRule("gmo", "IS_ANY_OF", ["Yes"]) },
    ]);
    expect(screen.getByRole("listitem", { name: "When Is this a GMO? is Yes" })).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "Show these fields" })).toHaveFocus();
  });

  it("does not apply a rule until it shows a field", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    await renderPanel([gmo, permit], onChange);

    await user.click(screen.getByRole("button", { name: "Add rule" }));
    await user.click(screen.getByRole("combobox", { name: "Options" }));
    await user.click(screen.getByRole("option", { name: "Yes" }));

    expect(onChange).not.toHaveBeenCalled();
    expect(screen.getByText(/This rule is not applied until/)).toBeInTheDocument();
  });

  it("does not offer a field that the source depends on as a field to show", async () => {
    const user = userEvent.setup();
    await renderPanel([gmo, level, permit]);

    await user.click(screen.getByRole("button", { name: "Add rule" }));
    const newRule = screen.getByRole("listitem", { name: "New rule 2" });
    await user.click(within(newRule).getByRole("combobox", { name: "Field" }));
    await user.click(screen.getByRole("option", { name: "Containment level" }));
    await user.click(within(newRule).getByRole("combobox", { name: "Show these fields" }));

    expect(screen.getAllByRole("option").map((option) => option.textContent)).toEqual(["GMO permit"]);
  });

  it("clears the rule of every field it shows when the rule is deleted", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    await renderPanel([gmo, { ...permit, rule: level.rule }, { ...notes, rule: level.rule }], onChange);

    await user.click(screen.getByRole("button", { name: "Delete rule: When Is this a GMO? is Yes" }));

    expect(onChange).toHaveBeenCalledWith([
      { key: "permit", rule: null },
      { key: "notes", rule: null },
    ]);
    expect(screen.getByText("No display rules. Every field is always shown.")).toBeInTheDocument();
  });

  it("only describes the rules when the template is not being edited", async () => {
    await renderPanel([gmo, { ...permit, rule: level.rule }], vi.fn(), false);
    expect(screen.getByText("When Is this a GMO? is Yes")).toBeInTheDocument();
    expect(screen.getByText("Shows GMO permit")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Add rule" })).not.toBeInTheDocument();
  });

  it("keeps a saved rule on its fields while an edit leaves it incomplete, and applies the edit once complete", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    await renderPanel([gmo, { ...permit, rule: level.rule }], onChange);

    const options = screen.getByRole("combobox", { name: "Options" });
    await user.click(options);
    await user.keyboard("{Backspace}");

    expect(onChange).not.toHaveBeenCalled();
    expect(screen.getByText(/Until then the saved rule stays as it was/)).toBeInTheDocument();
    expect(options).toHaveFocus();

    await user.click(screen.getByRole("option", { name: "No" }));
    expect(onChange).toHaveBeenCalledExactlyOnceWith([
      { key: "permit", rule: createDisplayRule("gmo", "IS_ANY_OF", ["No"]) },
    ]);
  });

  it("drops options from a draft that its source no longer offers", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    const wrapper = await createRealI18nWrapper({ resources: { common }, defaultNS: "common" });
    const panel = (fields: Array<RuleEditorField>) => (
      <ThemeProvider theme={materialTheme}>
        <DisplayRulesPanel fields={fields} editable onChange={onChange} />
      </ThemeProvider>
    );
    const { rerender } = render(panel([gmo, permit]), { wrapper });
    await user.click(screen.getByRole("button", { name: "Add rule" }));
    await user.click(screen.getByRole("combobox", { name: "Options" }));
    await user.click(screen.getByRole("option", { name: "Yes" }));
    await user.keyboard("{Escape}");

    rerender(panel([{ ...gmo, options: ["No"] }, permit]));
    await user.click(screen.getByRole("combobox", { name: "Show these fields" }));
    await user.click(screen.getByRole("option", { name: "GMO permit" }));

    expect(screen.queryByRole("button", { name: "Yes" })).not.toBeInTheDocument();
    expect(onChange).not.toHaveBeenCalled();
  });

  it("does not offer a target that another rule, applied since, would turn into a cycle", async () => {
    const user = userEvent.setup();
    await renderPanel([gmo, { ...level, rule: null }, permit]);

    await user.click(screen.getByRole("button", { name: "Add rule" }));
    const first = screen.getByRole("listitem", { name: "New rule 1" });
    await user.click(within(first).getByRole("combobox", { name: /^Field$/ }));
    await user.click(screen.getByRole("option", { name: "Containment level" }));

    await user.click(screen.getByRole("button", { name: "Add rule" }));
    const second = screen.getByRole("listitem", { name: "New rule 2" });
    await user.click(within(second).getByRole("combobox", { name: "Options" }));
    await user.click(screen.getByRole("option", { name: "Yes" }));
    await user.click(within(second).getByRole("combobox", { name: "Show these fields" }));
    await user.click(screen.getByRole("option", { name: "Containment level" }));
    await user.keyboard("{Escape}");

    await user.click(within(first).getByRole("combobox", { name: "Show these fields" }));
    expect(screen.getAllByRole("option").map((option) => option.textContent)).toEqual(["GMO permit"]);
  });

  it("completing an edit changes only the fields that rule had, not another rule that now matches", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    await renderPanel([gmo, { ...permit, rule: level.rule }, notes], onChange);

    // the saved rule on GMO permit becomes an incomplete edit; the model keeps "is Yes"
    const edited = screen.getByRole("listitem", { name: "When Is this a GMO? is Yes" });
    await user.click(within(edited).getByRole("combobox", { name: "Options" }));
    await user.keyboard("{Backspace}{Escape}");
    // another rule gives Notes the condition GMO permit still has saved
    await user.click(screen.getByRole("button", { name: "Add rule" }));
    const added = screen.getByRole("listitem", { name: /^New rule/ });
    await user.click(within(added).getByRole("combobox", { name: "Options" }));
    await user.click(screen.getByRole("option", { name: "Yes" }));
    await user.click(within(added).getByRole("combobox", { name: "Show these fields" }));
    await user.click(screen.getByRole("option", { name: "Notes" }));
    await user.keyboard("{Escape}");

    await user.click(within(edited).getByRole("combobox", { name: "Options" }));
    await user.click(screen.getByRole("option", { name: "No" }));

    expect(onChange).toHaveBeenLastCalledWith([{ key: "permit", rule: createDisplayRule("gmo", "IS_ANY_OF", ["No"]) }]);
    expect(onChange.mock.calls.flat(2)).not.toContainEqual({ key: "notes", rule: null });
  });

  it("keeps focus when a new rule becomes identical to a saved one and the two merge", async () => {
    const user = userEvent.setup();
    await renderPanel([gmo, { ...permit, rule: level.rule }, notes]);

    await user.click(screen.getByRole("button", { name: "Add rule" }));
    const added = screen.getByRole("listitem", { name: /^New rule/ });
    await user.click(within(added).getByRole("combobox", { name: "Options" }));
    await user.click(screen.getByRole("option", { name: "Yes" }));
    await user.click(within(added).getByRole("combobox", { name: "Show these fields" }));
    await user.click(screen.getByRole("option", { name: "Notes" }));

    expect(screen.getAllByRole("listitem", { name: "When Is this a GMO? is Yes" })).toHaveLength(1);
    expect(screen.getByRole("combobox", { name: "Show these fields" })).toHaveFocus();
  });

  it("keeps a merged card mounted and in place when another rule is edited afterwards", async () => {
    const user = userEvent.setup();
    const no = createDisplayRule("gmo", "IS_ANY_OF", ["No"]);
    await renderPanel([gmo, { ...permit, rule: level.rule }, { ...level, rule: no }, notes]);

    await user.click(screen.getByRole("button", { name: "Add rule" }));
    const added = screen.getByRole("listitem", { name: /^New rule/ });
    await user.click(within(added).getByRole("combobox", { name: "Options" }));
    await user.click(screen.getByRole("option", { name: "Yes" }));
    await user.click(within(added).getByRole("combobox", { name: "Show these fields" }));
    await user.click(screen.getByRole("option", { name: "Notes" }));
    await user.keyboard("{Escape}");
    const merged = screen.getByRole("listitem", { name: "When Is this a GMO? is Yes" });
    const position = screen.getAllByRole("listitem").indexOf(merged);

    const other = screen.getByRole("listitem", { name: "When Is this a GMO? is No" });
    await user.click(within(other).getByRole("combobox", { name: "Options" }));
    await user.click(screen.getByRole("option", { name: "Yes" }));

    expect(screen.getByRole("listitem", { name: "When Is this a GMO? is Yes" })).toBe(merged);
    expect(screen.getAllByRole("listitem").indexOf(merged)).toBe(position);
  });

  it("does not move a rule above the others the first time it is edited", async () => {
    const user = userEvent.setup();
    const wrapper = await createRealI18nWrapper({ resources: { common }, defaultNS: "common" });
    const panel = (fields: Array<RuleEditorField>) => (
      <ThemeProvider theme={materialTheme}>
        <DisplayRulesPanel fields={fields} editable onChange={vi.fn()} />
      </ThemeProvider>
    );
    // the rules arrive after the panel first renders, so none has a remembered place yet
    const { rerender } = render(panel([gmo, permit, { ...level, rule: null }]), { wrapper });
    const no = createDisplayRule("gmo", "IS_ANY_OF", ["No"]);
    rerender(panel([gmo, { ...permit, rule: level.rule }, { ...level, rule: no }]));

    const second = screen.getByRole("listitem", { name: "When Is this a GMO? is No" });
    await user.click(within(second).getByRole("combobox", { name: "Options" }));
    await user.click(screen.getByRole("option", { name: "Yes" }));

    expect(screen.getAllByRole("listitem")[1]).toBe(second);
  });

  it("keeps a saved rule's card in place, with focus, when its first field is removed", async () => {
    const user = userEvent.setup();
    const yes = level.rule;
    const no = createDisplayRule("gmo", "IS_ANY_OF", ["No"]);
    await renderPanel([gmo, { ...permit, rule: yes }, { ...level, rule: no }, { ...notes, rule: yes }]);

    const [first] = screen.getAllByRole("listitem");
    const targets = within(first).getByRole("combobox", { name: "Show these fields" });
    await user.click(targets);
    await user.keyboard("{ArrowLeft}{ArrowLeft}{Backspace}");

    const [stillFirst] = screen.getAllByRole("listitem");
    expect(within(stillFirst).queryByRole("button", { name: "GMO permit" })).not.toBeInTheDocument();
    expect(within(stillFirst).getByRole("button", { name: "Notes" })).toBeInTheDocument();
    expect(within(stillFirst).getByRole("combobox", { name: "Show these fields" })).toHaveFocus();
  });

  it("is accessible", async () => {
    const { baseElement } = await renderPanel([gmo, level, { ...permit, rule: level.rule }]);
    await expectAccessible(baseElement);
  });
});
