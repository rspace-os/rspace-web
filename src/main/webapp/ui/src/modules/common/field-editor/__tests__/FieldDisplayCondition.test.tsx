import { describe, expect, it, vi } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import { ThemeProvider } from "@mui/material/styles";
import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { expectAccessible } from "@/__tests__/accessibility";
import { renderWithRealI18n } from "@/__tests__/helpers/realI18n";
import { createDisplayRule, type FieldDisplayRule } from "@/modules/common/field-rules/displayRule";
import common from "@/modules/common/i18n/locales/en-US/common.json";
import materialTheme from "@/theme";
import FieldDisplayCondition from "../FieldDisplayCondition";
import type { RuleEditorField } from "../ruleEditing";

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
const yes = createDisplayRule("gmo", "IS_ANY_OF", ["Yes"]);

/** Renders the condition of each field, storing rules the way a host model would. */
function Harness({
  initial,
  onChange,
  editable,
}: {
  initial: Array<RuleEditorField>;
  onChange: (key: string, rule: FieldDisplayRule | null) => void;
  editable: boolean;
}) {
  const [fields, setFields] = useState(initial);
  return (
    <ThemeProvider theme={materialTheme}>
      {fields.map((field) => (
        <section key={field.key} aria-label={field.name}>
          <FieldDisplayCondition
            fieldKey={field.key}
            fields={fields}
            editable={editable}
            onChange={(rule) => {
              onChange(field.key, rule);
              setFields((current) => current.map((other) => (other.key === field.key ? { ...other, rule } : other)));
            }}
          />
        </section>
      ))}
    </ThemeProvider>
  );
}

const renderConditions = (initial: Array<RuleEditorField>, { editable = true } = {}) => {
  const onChange = vi.fn();
  const view = renderWithRealI18n(<Harness initial={initial} onChange={onChange} editable={editable} />, {
    resources: { common },
    defaultNS: "common",
  });
  return view.then((rendered) => ({ ...rendered, onChange }));
};

const card = (name: string) => within(screen.getByRole("region", { name }));
const conditional = (name: string) =>
  card(name).getByRole("radio", { name: "Only when another field has certain values" });

describe("FieldDisplayCondition", () => {
  it("applies a condition once it has a field, an operator and an option", async () => {
    const user = userEvent.setup();
    const { onChange } = await renderConditions([gmo, permit]);

    await user.click(conditional("GMO permit"));
    expect(card("GMO permit").getByRole("combobox", { name: "Field" })).toHaveTextContent("Is this a GMO?");
    expect(card("GMO permit").getByText(/This condition is not applied until/)).toBeInTheDocument();
    expect(onChange).not.toHaveBeenCalled();

    await user.click(card("GMO permit").getByRole("combobox", { name: "Condition" }));
    await user.click(screen.getByRole("option", { name: "is none of" }));
    await user.click(card("GMO permit").getByRole("combobox", { name: "Options" }));
    await user.click(screen.getByRole("option", { name: "No" }));

    expect(onChange).toHaveBeenCalledExactlyOnceWith("permit", createDisplayRule("gmo", "IS_NONE_OF", ["No"]));
    expect(card("GMO permit").queryByRole("alert")).not.toBeInTheDocument();
  });

  it("applies an operator without options as soon as it is chosen", async () => {
    const user = userEvent.setup();
    const { onChange } = await renderConditions([gmo, permit]);

    await user.click(conditional("GMO permit"));
    await user.click(card("GMO permit").getByRole("combobox", { name: "Condition" }));
    await user.click(screen.getByRole("option", { name: "has a value" }));

    expect(onChange).toHaveBeenCalledExactlyOnceWith("permit", createDisplayRule("gmo", "HAS_VALUE", []));
    expect(card("GMO permit").queryByRole("combobox", { name: "Options" })).not.toBeInTheDocument();
  });

  it("keeps the stored rule while an edit leaves the condition incomplete", async () => {
    const user = userEvent.setup();
    const { onChange } = await renderConditions([gmo, { ...permit, rule: yes }]);

    const options = card("GMO permit").getByRole("combobox", { name: "Options" });
    await user.click(options);
    await user.keyboard("{Backspace}");

    expect(onChange).not.toHaveBeenCalled();
    expect(card("GMO permit").getByText(/Until then the saved condition stays as it was/)).toBeInTheDocument();
    expect(options).toHaveFocus();

    await user.click(screen.getByRole("option", { name: "No" }));
    expect(onChange).toHaveBeenCalledExactlyOnceWith("permit", createDisplayRule("gmo", "IS_ANY_OF", ["No"]));
  });

  it("clears the rule and any draft when Always is chosen", async () => {
    const user = userEvent.setup();
    const { onChange } = await renderConditions([gmo, { ...permit, rule: yes }]);

    await user.click(card("GMO permit").getByRole("combobox", { name: "Options" }));
    await user.keyboard("{Backspace}{Escape}");
    await user.click(card("GMO permit").getByRole("radio", { name: "Always" }));

    expect(onChange).toHaveBeenCalledExactlyOnceWith("permit", null);
    expect(card("GMO permit").getByRole("radio", { name: "Always" })).toBeChecked();
    expect(card("GMO permit").queryByRole("combobox", { name: "Field" })).not.toBeInTheDocument();
    expect(card("GMO permit").queryByText(/not applied/)).not.toBeInTheDocument();
  });

  it("does not offer the field itself, or a field that depends on it, as the field to read", async () => {
    const user = userEvent.setup();
    const colour: RuleEditorField = { key: "colour", name: "Colour", canBeSource: true, options: ["Red"], rule: null };
    await renderConditions([gmo, level, colour, permit]);

    // level reads gmo, so gmo reading level would be a cycle
    await user.click(conditional("Is this a GMO?"));
    await user.click(card("Is this a GMO?").getByRole("combobox", { name: "Field" }));
    expect(screen.getAllByRole("option").map((option) => option.textContent)).toEqual(["Colour"]);
    await user.keyboard("{Escape}");

    await user.click(conditional("GMO permit"));
    await user.click(card("GMO permit").getByRole("combobox", { name: "Field" }));
    expect(screen.getAllByRole("option").map((option) => option.textContent)).toEqual([
      "Is this a GMO?",
      "Containment level",
      "Colour",
    ]);
  });

  it("explains, and disables the condition, when no radio or choice field can be read", async () => {
    await renderConditions([gmo, permit]);

    const onlyWhen = conditional("Is this a GMO?");
    expect(onlyWhen).toBeDisabled();
    expect(onlyWhen).toHaveAccessibleDescription(
      "Add a radio or choice field first. Rules can only read those fields.",
    );
  });

  it("hints when the field it reads comes after it", async () => {
    await renderConditions([{ ...permit, rule: yes }, gmo]);
    expect(card("GMO permit").getByText("Placing it below Is this a GMO? reads better.")).toBeInTheDocument();
  });

  it("can be operated with the keyboard alone", async () => {
    const user = userEvent.setup();
    const { onChange } = await renderConditions([gmo, permit]);

    card("GMO permit").getByRole("radio", { name: "Always" }).focus();
    await user.keyboard("{ArrowRight}");
    expect(conditional("GMO permit")).toBeChecked();

    await user.tab();
    expect(card("GMO permit").getByRole("combobox", { name: "Field" })).toHaveFocus();
    await user.tab();
    await user.tab();
    expect(card("GMO permit").getByRole("combobox", { name: "Options" })).toHaveFocus();
    await user.keyboard("{ArrowDown}");
    expect(screen.getByRole("listbox")).toBeInTheDocument();
    await user.keyboard("{ArrowDown}{Enter}");

    expect(onChange).toHaveBeenCalledExactlyOnceWith("permit", yes);
  });

  it("shows only the summary, and which fields depend on a field, when not editable", async () => {
    await renderConditions([gmo, { ...permit, rule: yes }], { editable: false });

    expect(card("GMO permit").getByText("Shown when Is this a GMO? is Yes")).toBeInTheDocument();
    expect(card("Is this a GMO?").getByText("Other fields depend on this field: GMO permit")).toBeInTheDocument();
    expect(screen.queryByRole("radio")).not.toBeInTheDocument();
  });

  it("is accessible", async () => {
    const user = userEvent.setup();
    const { baseElement } = await renderConditions([gmo, level, { ...permit, rule: yes }]);
    await user.click(card("GMO permit").getByRole("combobox", { name: "Options" }));
    await user.keyboard("{Backspace}{Escape}");
    await expectAccessible(baseElement);
  });
});
