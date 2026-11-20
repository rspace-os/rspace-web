import { describe, expect, it, vi } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import { ThemeProvider } from "@mui/material/styles";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { expectAccessible } from "@/__tests__/accessibility";
import { renderWithRealI18n, wrapWithRealI18n } from "@/__tests__/helpers/realI18n";
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
  onIncompleteChange,
  editable,
}: {
  initial: Array<RuleEditorField>;
  onChange: (key: string, rule: FieldDisplayRule | null) => void;
  onIncompleteChange: (key: string, incomplete: boolean) => void;
  editable: boolean;
}) {
  const [fields, setFields] = useState(initial);
  return (
    <ThemeProvider theme={materialTheme}>
      {fields.map((field) => (
        <section key={field.key} aria-label={field.name}>
          <button
            type="button"
            aria-label={`Delete ${field.name}`}
            // as hosts do, rules that read the deleted field are cleared with it
            onClick={() =>
              setFields((current) =>
                current
                  .filter((other) => other.key !== field.key)
                  .map((other) => (other.rule?.sourceFieldKey === field.key ? { ...other, rule: null } : other)),
              )
            }
          />
          <button
            type="button"
            aria-label={`Remove the first option of ${field.name}`}
            onClick={() =>
              setFields((current) =>
                current.map((other) =>
                  other.key === field.key ? { ...other, options: other.options.slice(1) } : other,
                ),
              )
            }
          />
          <button
            type="button"
            aria-label={`Change the kind of ${field.name}`}
            // as hosts do once the author confirms, rules that read the retyped field are cleared
            onClick={() =>
              setFields((current) =>
                current.map((other) =>
                  other.key === field.key
                    ? other.sourceKind === "NUMBER"
                      ? { ...other, sourceKind: "OPTIONS", options: ["Light", "Heavy"] }
                      : { ...other, sourceKind: "NUMBER", options: [] }
                    : other.rule?.sourceFieldKey === field.key
                      ? { ...other, rule: null }
                      : other,
                ),
              )
            }
          />
          <FieldDisplayCondition
            fieldKey={field.key}
            fields={fields}
            editable={editable}
            onChange={(rule) => {
              onChange(field.key, rule);
              setFields((current) => current.map((other) => (other.key === field.key ? { ...other, rule } : other)));
            }}
            onIncompleteChange={(incomplete) => onIncompleteChange(field.key, incomplete)}
          />
        </section>
      ))}
    </ThemeProvider>
  );
}

const renderConditions = (initial: Array<RuleEditorField>, { editable = true } = {}) => {
  const onChange = vi.fn();
  const onIncompleteChange = vi.fn();
  const view = renderWithRealI18n(
    <Harness initial={initial} onChange={onChange} onIncompleteChange={onIncompleteChange} editable={editable} />,
    { resources: { common }, defaultNS: "common" },
  );
  return view.then((rendered) => ({ ...rendered, onChange, onIncompleteChange }));
};

const card = (name: string) => within(screen.getByRole("region", { name }));
const conditional = (name: string) =>
  card(name).getByRole("radio", { name: "Only when another field has certain values" });

describe("FieldDisplayCondition", () => {
  it("applies a condition once it has a field, an operator and an option, reporting it incomplete until then", async () => {
    const user = userEvent.setup();
    const { onChange, onIncompleteChange } = await renderConditions([gmo, permit]);

    await user.click(conditional("GMO permit"));
    expect(card("GMO permit").getByRole("combobox", { name: "Field" })).toHaveTextContent("Is this a GMO?");
    expect(onIncompleteChange).toHaveBeenCalledExactlyOnceWith("permit", true);
    expect(onChange).not.toHaveBeenCalled();

    await user.click(card("GMO permit").getByRole("combobox", { name: "Condition" }));
    await user.click(screen.getByRole("option", { name: "is none of" }));
    await user.click(card("GMO permit").getByRole("combobox", { name: "Options" }));
    await user.click(screen.getByRole("option", { name: "No" }));

    expect(onChange).toHaveBeenCalledExactlyOnceWith("permit", createDisplayRule("gmo", "IS_NONE_OF", ["No"]));
    expect(onIncompleteChange).toHaveBeenLastCalledWith("permit", false);
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
    const { onChange, onIncompleteChange } = await renderConditions([gmo, { ...permit, rule: yes }]);

    const options = card("GMO permit").getByRole("combobox", { name: "Options" });
    await user.click(options);
    await user.keyboard("{Backspace}");

    expect(onChange).not.toHaveBeenCalled();
    expect(onIncompleteChange).toHaveBeenCalledExactlyOnceWith("permit", true);
    expect(options).toHaveAccessibleDescription("Choose at least one option.");
    expect(options).toHaveFocus();

    await user.click(screen.getByRole("option", { name: "No" }));
    expect(onChange).toHaveBeenCalledExactlyOnceWith("permit", createDisplayRule("gmo", "IS_ANY_OF", ["No"]));
  });

  it("clears the rule and any draft when Always is chosen", async () => {
    const user = userEvent.setup();
    const { onChange, onIncompleteChange } = await renderConditions([gmo, { ...permit, rule: yes }]);

    await user.click(card("GMO permit").getByRole("combobox", { name: "Options" }));
    await user.keyboard("{Backspace}{Escape}");
    await user.click(card("GMO permit").getByRole("radio", { name: "Always" }));

    expect(onChange).toHaveBeenCalledExactlyOnceWith("permit", null);
    expect(card("GMO permit").getByRole("radio", { name: "Always" })).toBeChecked();
    expect(card("GMO permit").queryByRole("combobox", { name: "Field" })).not.toBeInTheDocument();
    expect(onIncompleteChange).toHaveBeenLastCalledWith("permit", false);
  });

  it("restores a saved condition that the arrow keys moved away from", async () => {
    const user = userEvent.setup();
    const { onChange } = await renderConditions([gmo, { ...permit, rule: yes }]);

    conditional("GMO permit").focus();
    await user.keyboard("{ArrowUp}");
    expect(card("GMO permit").getByRole("radio", { name: "Always" })).toBeChecked();
    await user.keyboard("{ArrowDown}");

    expect(onChange.mock.calls).toEqual([
      ["permit", null],
      ["permit", yes],
    ]);
    expect(card("GMO permit").getByRole("button", { name: "Yes" })).toBeInTheDocument();
  });

  it("restores both the saved condition and an unfinished edit of it after a round trip through Always", async () => {
    const user = userEvent.setup();
    const { onChange, onIncompleteChange } = await renderConditions([gmo, { ...permit, rule: yes }]);

    await user.click(card("GMO permit").getByRole("combobox", { name: "Options" }));
    await user.keyboard("{Backspace}{Escape}");
    conditional("GMO permit").focus();
    await user.keyboard("{ArrowUp}");
    await user.keyboard("{ArrowDown}");

    expect(onChange.mock.calls).toEqual([
      ["permit", null],
      ["permit", yes],
    ]);
    expect(onIncompleteChange.mock.calls).toEqual([
      ["permit", true],
      ["permit", false],
      ["permit", true],
    ]);
    expect(card("GMO permit").getByRole("combobox", { name: "Options" })).toHaveAccessibleDescription(
      "Choose at least one option.",
    );
  });

  it("restores what still applies of a saved condition that no longer validates after a round trip through Always", async () => {
    const user = userEvent.setup();
    // "Maybe" is not an option of the source, as if it was removed while Always was chosen
    const { onChange } = await renderConditions([
      gmo,
      { ...permit, rule: createDisplayRule("gmo", "IS_ANY_OF", ["Yes", "Maybe"]) },
    ]);

    conditional("GMO permit").focus();
    await user.keyboard("{ArrowUp}");
    await user.keyboard("{ArrowDown}");

    expect(onChange).toHaveBeenLastCalledWith("permit", yes);
    expect(card("GMO permit").getByRole("button", { name: "Yes" })).toBeInTheDocument();
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

  it("offers the condition when no other field can be read, with a field menu that says so", async () => {
    const user = userEvent.setup();
    const { onIncompleteChange } = await renderConditions([gmo, permit]);

    await user.click(conditional("Is this a GMO?"));
    const field = card("Is this a GMO?").getByRole("combobox", { name: "Field" });
    // the menu skips its disabled item, so the description is what says there is nothing to choose
    expect(field).toHaveAccessibleDescription("No other fields");
    expect(onIncompleteChange).toHaveBeenCalledExactlyOnceWith("gmo", true);

    await user.click(field);
    const option = screen.getByRole("option", { name: "No other fields" });
    expect(screen.getAllByRole("option")).toEqual([option]);
    expect(option).toHaveAttribute("aria-disabled", "true");
  });

  it("says why a complete condition that cannot be stored is not applied", async () => {
    const user = userEvent.setup();
    // together the two options are longer than a stored rule may be
    const [long, longer] = ["a".repeat(20_000), "b".repeat(20_000)];
    const { onChange, onIncompleteChange } = await renderConditions([
      { ...gmo, options: [long, longer] },
      { ...permit, rule: createDisplayRule("gmo", "IS_ANY_OF", [long]) },
    ]);

    const options = card("GMO permit").getByRole("combobox", { name: "Options" });
    await user.click(options);
    await user.click(screen.getByRole("option", { name: longer }));

    expect(options).toHaveAccessibleDescription("This condition cannot be applied. Choose fewer or shorter options.");
    expect(onChange).not.toHaveBeenCalled();
    expect(onIncompleteChange).toHaveBeenCalledExactlyOnceWith("permit", true);
  });

  it("keeps saying a refused condition is not applied after another edit would let it be stored", async () => {
    const user = userEvent.setup();
    const [long, longer] = ["a".repeat(20_000), "b".repeat(20_000)];
    const { onChange } = await renderConditions([
      { ...gmo, options: [long, longer] },
      { ...permit, rule: createDisplayRule("gmo", "IS_ANY_OF", [long]) },
    ]);
    const options = card("GMO permit").getByRole("combobox", { name: "Options" });
    await user.click(options);
    await user.click(screen.getByRole("option", { name: longer }));
    await user.keyboard("{Escape}");

    // the draft now holds only the shorter option, which could be stored, but it is still not applied
    await user.click(screen.getByRole("button", { name: "Remove the first option of Is this a GMO?" }));

    expect(options).toHaveAccessibleDescription("This condition cannot be applied. Choose fewer or shorter options.");
    expect(onChange).not.toHaveBeenCalled();
  });

  it("drops a refused edit that has become storable, restoring the stored rule, on its way back from Always", async () => {
    const user = userEvent.setup();
    // too long to be stored as part of a rule
    const huge = "a".repeat(40_000);
    const { onChange, onIncompleteChange } = await renderConditions([
      { ...gmo, options: [huge, "No", "Yes"] },
      { ...permit, rule: yes },
    ]);
    const options = () => card("GMO permit").getByRole("combobox", { name: "Options" });
    for (const option of [huge, "No"]) {
      await user.click(options());
      await user.click(screen.getByRole("option", { name: option }));
    }
    conditional("GMO permit").focus();
    await user.keyboard("{ArrowUp}");

    // the refused edit could now be stored as "Yes" or "No", which the author never saw accepted
    await user.click(screen.getByRole("button", { name: "Remove the first option of Is this a GMO?" }));
    await user.click(conditional("GMO permit"));

    expect(onChange.mock.calls).toEqual([
      ["permit", null],
      ["permit", yes],
    ]);
    expect(onIncompleteChange.mock.calls).toEqual([
      ["permit", true],
      ["permit", false],
    ]);
    expect(card("GMO permit").getByRole("button", { name: "Yes" })).toBeInTheDocument();
    expect(card("GMO permit").queryByRole("button", { name: "No" })).not.toBeInTheDocument();
    expect(options()).not.toHaveAccessibleDescription();
  });

  it("drops a refused edit that has become storable on its way back from Always when no rule was stored", async () => {
    const user = userEvent.setup();
    const huge = "a".repeat(40_000);
    const { onChange } = await renderConditions([{ ...gmo, options: [huge, "No", "Yes"] }, permit]);
    await user.click(conditional("GMO permit"));
    const options = () => card("GMO permit").getByRole("combobox", { name: "Options" });
    for (const option of [huge, "No"]) {
      await user.click(options());
      await user.click(screen.getByRole("option", { name: option }));
    }
    conditional("GMO permit").focus();
    await user.keyboard("{ArrowUp}");

    await user.click(screen.getByRole("button", { name: "Remove the first option of Is this a GMO?" }));
    await user.click(conditional("GMO permit"));

    expect(onChange).not.toHaveBeenCalled();
    expect(card("GMO permit").queryByRole("button", { name: "No" })).not.toBeInTheDocument();
    expect(options()).toHaveAccessibleDescription("Choose at least one option.");
  });

  it("forgets what Always set aside once it is brought back, so it does not come back later", async () => {
    const user = userEvent.setup();
    const colour: RuleEditorField = { key: "colour", name: "Colour", canBeSource: true, options: ["Red"], rule: null };
    const { onChange } = await renderConditions([gmo, colour, { ...permit, rule: yes }]);
    conditional("GMO permit").focus();
    await user.keyboard("{ArrowUp}");
    await user.keyboard("{ArrowDown}");
    await user.click(card("GMO permit").getByRole("combobox", { name: "Field" }));
    await user.click(screen.getByRole("option", { name: "Colour" }));
    await user.click(card("GMO permit").getByRole("combobox", { name: "Options" }));
    await user.click(screen.getByRole("option", { name: "Red" }));
    // deleting the source clears the rule, so the radio falls back to Always without being chosen
    await user.click(screen.getByRole("button", { name: "Delete Colour" }));
    expect(card("GMO permit").getByRole("radio", { name: "Always" })).toBeChecked();

    await user.click(conditional("GMO permit"));

    expect(onChange).toHaveBeenLastCalledWith("permit", createDisplayRule("colour", "IS_ANY_OF", ["Red"]));
    expect(card("GMO permit").getByRole("combobox", { name: "Options" })).toHaveAccessibleDescription(
      "Choose at least one option.",
    );
  });

  it("clears the field it was showing, and reports the new one, when given another field", async () => {
    const calls: Array<[string, boolean]> = [];
    const orphan = (key: string): RuleEditorField => ({ ...permit, key, name: key, rule: yes });
    const fields = [orphan("first"), orphan("second")];
    const conditionOf = (key: string) =>
      wrapWithRealI18n(
        <FieldDisplayCondition
          fieldKey={key}
          fields={fields}
          editable
          onChange={() => {}}
          onIncompleteChange={(incomplete) => calls.push([key, incomplete])}
        />,
        { resources: { common }, defaultNS: "common" },
      );
    const { rerender } = render(await conditionOf("first"));

    rerender(await conditionOf("second"));

    expect(calls).toEqual([
      ["first", true],
      ["first", false],
      ["second", true],
    ]);
  });

  it("stops reporting an unfinished condition once its field is deleted", async () => {
    const user = userEvent.setup();
    const { onIncompleteChange } = await renderConditions([gmo, permit]);
    await user.click(conditional("GMO permit"));

    await user.click(screen.getByRole("button", { name: "Delete GMO permit" }));

    expect(onIncompleteChange.mock.calls).toEqual([
      ["permit", true],
      ["permit", false],
    ]);
  });

  it("says when the field a saved condition read is gone, and reports the condition incomplete", async () => {
    const colour: RuleEditorField = { key: "colour", name: "Colour", canBeSource: true, options: ["Red"], rule: null };
    const { onIncompleteChange } = await renderConditions([{ ...permit, rule: yes }, colour]);

    expect(conditional("GMO permit")).toBeChecked();
    expect(card("GMO permit").getByRole("combobox", { name: "Field" })).toHaveAccessibleDescription(
      "The field this condition read no longer exists. Choose another field.",
    );
    expect(onIncompleteChange).toHaveBeenCalledExactlyOnceWith("permit", true);
  });

  it("names the choice once, by its legend", async () => {
    await renderConditions([gmo, permit]);
    expect(card("GMO permit").getByRole("group", { name: "Display this field when…" })).toBeInTheDocument();
    expect(card("GMO permit").getByRole("radiogroup")).not.toHaveAccessibleName();
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

describe("FieldDisplayCondition with text and number sources", () => {
  const batch: RuleEditorField = {
    key: "batch",
    name: "Batch code",
    canBeSource: true,
    sourceKind: "TEXT",
    options: [],
    rule: null,
  };
  const weight: RuleEditorField = {
    key: "weight",
    name: "Weight",
    canBeSource: true,
    sourceKind: "NUMBER",
    options: [],
    rule: null,
  };
  const notes: RuleEditorField = { key: "notes", name: "Notes", canBeSource: false, options: [], rule: null };

  async function chooseSource(user: ReturnType<typeof userEvent.setup>, name: string) {
    await user.click(card("Notes").getByRole("combobox", { name: "Field" }));
    await user.click(screen.getByRole("option", { name }));
  }

  it("offers text and number fields, but not fields no rule can read", async () => {
    const user = userEvent.setup();
    await renderConditions([gmo, batch, weight, permit, notes]);

    await user.click(conditional("Notes"));
    await user.click(card("Notes").getByRole("combobox", { name: "Field" }));

    expect(screen.getAllByRole("option").map((option) => option.textContent)).toEqual([
      "Is this a GMO?",
      "Batch code",
      "Weight",
    ]);
  });

  it("lists the conditions of a number field and applies a trimmed number, explaining one that is not a number", async () => {
    const user = userEvent.setup();
    const { onChange, onIncompleteChange } = await renderConditions([gmo, weight, notes]);
    await user.click(conditional("Notes"));
    await chooseSource(user, "Weight");

    await user.click(card("Notes").getByRole("combobox", { name: "Condition" }));
    expect(screen.getAllByRole("option").map((option) => option.textContent)).toEqual([
      "is",
      "is not",
      "is less than",
      "is at most",
      "is greater than",
      "is at least",
      "has a value",
      "has no value",
    ]);
    await user.click(screen.getByRole("option", { name: "is greater than" }));
    const value = card("Notes").getByRole("textbox", { name: "Value" });
    expect(value).toHaveAttribute("type", "text");
    expect(value).not.toHaveAttribute("inputmode");
    expect(value).toHaveAccessibleDescription("Enter a value.");

    await user.type(value, "five");
    expect(value).toHaveAccessibleDescription("Enter a number, such as 5, -2 or 2.5.");
    expect(onChange).not.toHaveBeenCalled();
    expect(onIncompleteChange).toHaveBeenLastCalledWith("notes", true);

    await user.clear(value);
    await user.type(value, "5 ");
    expect(onChange).toHaveBeenCalledExactlyOnceWith("notes", createDisplayRule("weight", "GREATER_THAN", ["5"]));
    expect(onIncompleteChange).toHaveBeenLastCalledWith("notes", false);
    expect(value).toHaveValue("5 ");
  });

  it("applies a text condition as typed, keeping spaces between words in the box", async () => {
    const user = userEvent.setup();
    const { onChange } = await renderConditions([batch, notes]);
    await user.click(conditional("Notes"));

    await user.click(card("Notes").getByRole("combobox", { name: "Condition" }));
    await user.click(screen.getByRole("option", { name: "contains" }));
    const value = card("Notes").getByRole("textbox", { name: "Value" });
    await user.type(value, "LOT A");

    expect(value).toHaveValue("LOT A");
    expect(onChange).toHaveBeenLastCalledWith("notes", createDisplayRule("batch", "CONTAINS", ["LOT A"]));
  });

  it("starts the condition again when a field of another kind is chosen", async () => {
    const user = userEvent.setup();
    const { onChange } = await renderConditions([
      gmo,
      batch,
      { ...notes, rule: createDisplayRule("batch", "EQUALS", ["LOT"]) },
    ]);

    await chooseSource(user, "Is this a GMO?");

    expect(card("Notes").getByRole("combobox", { name: "Condition" })).toHaveTextContent("is any of");
    expect(card("Notes").queryByRole("textbox", { name: "Value" })).not.toBeInTheDocument();
    expect(card("Notes").getByRole("combobox", { name: "Options" })).toHaveAccessibleDescription(
      "Choose at least one option.",
    );
    expect(onChange).not.toHaveBeenCalled();
  });

  it("drops an unfinished edit, reporting nothing incomplete, once the field it reads changes kind", async () => {
    const user = userEvent.setup();
    const { onIncompleteChange } = await renderConditions([
      weight,
      { ...notes, rule: createDisplayRule("weight", "EQUALS", ["5"]) },
    ]);
    await user.type(card("Notes").getByRole("textbox", { name: "Value" }), "x");
    expect(onIncompleteChange).toHaveBeenLastCalledWith("notes", true);

    await user.click(screen.getByRole("button", { name: "Change the kind of Weight" }));

    expect(card("Notes").getByRole("radio", { name: "Always" })).toBeChecked();
    expect(onIncompleteChange).toHaveBeenLastCalledWith("notes", false);
  });

  it("does not bring back a condition set aside by Always once the field it read changes kind", async () => {
    const user = userEvent.setup();
    const { onChange } = await renderConditions([gmo, { ...notes, rule: createDisplayRule("gmo", "HAS_VALUE", []) }]);
    await user.click(card("Notes").getByRole("radio", { name: "Always" }));

    await user.click(screen.getByRole("button", { name: "Change the kind of Is this a GMO?" }));
    await user.click(conditional("Notes"));

    expect(onChange.mock.calls).toEqual([["notes", null]]);
    expect(card("Notes").getByRole("textbox", { name: "Value" })).toHaveAccessibleDescription("Enter a value.");
  });

  it("does not bring back a condition set aside by Always once the field it read changes kind and back", async () => {
    const user = userEvent.setup();
    const { onChange } = await renderConditions([gmo, { ...notes, rule: createDisplayRule("gmo", "HAS_VALUE", []) }]);
    await user.click(card("Notes").getByRole("radio", { name: "Always" }));

    const changeKind = screen.getByRole("button", { name: "Change the kind of Is this a GMO?" });
    await user.click(changeKind);
    await user.click(changeKind);
    await user.click(conditional("Notes"));

    expect(onChange.mock.calls).toEqual([["notes", null]]);
    expect(card("Notes").getByRole("combobox", { name: "Condition" })).toHaveTextContent("is any of");
  });

  it("quotes text values in the summary, but not numbers", async () => {
    await renderConditions(
      [
        batch,
        weight,
        { ...notes, rule: createDisplayRule("batch", "CONTAINS", ["LOT"]) },
        { ...permit, rule: createDisplayRule("weight", "EQUALS", ["5"]) },
      ],
      { editable: false },
    );

    expect(card("Notes").getByText("Shown when Batch code contains “LOT”")).toBeInTheDocument();
    expect(card("GMO permit").getByText("Shown when Weight is 5")).toBeInTheDocument();
  });
});
