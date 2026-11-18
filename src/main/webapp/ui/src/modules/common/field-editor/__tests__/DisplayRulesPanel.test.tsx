import { describe, expect, it, vi } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import { ThemeProvider } from "@mui/material/styles";
import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { expectAccessible } from "@/__tests__/accessibility";
import { renderWithRealI18n } from "@/__tests__/helpers/realI18n";
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

  it("is accessible", async () => {
    const { baseElement } = await renderPanel([gmo, level, { ...permit, rule: level.rule }]);
    await expectAccessible(baseElement);
  });
});
