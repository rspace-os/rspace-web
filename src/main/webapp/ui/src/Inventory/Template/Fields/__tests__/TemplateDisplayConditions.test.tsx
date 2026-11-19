import { afterEach, describe, expect, it, vi } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import { ThemeProvider } from "@mui/material/styles";
import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { observable, runInAction } from "mobx";
import { expectAccessible } from "@/__tests__/accessibility";
import { createDisplayRule } from "@/modules/common/field-rules/displayRule";
import FormSectionsContext from "@/stores/contexts/FormSections";
import { makeMockInstrumentTemplate } from "@/stores/models/__tests__/InstrumentTemplateModel/mocking";
import { makeMockTemplate, templateAttrs } from "@/stores/models/__tests__/TemplateModel/mocking";
import type FieldModel from "@/stores/models/FieldModel";
import type { FieldModelAttrs } from "@/stores/models/FieldModel";
import type TemplateModel from "@/stores/models/TemplateModel";
import { makeMockRootStore } from "@/stores/stores/__tests__/RootStore/mocking";
import { storesContext } from "@/stores/stores-context";
import materialTheme from "@/theme";
import StepperActions from "../../../components/Stepper/StepperActions";
import InstrumentTemplateFields from "../../../InstrumentTemplate/Fields/CustomFields";
import TemplateForm from "../../Form";
import CustomFields from "../CustomFields";

const { update, post, addAlert } = vi.hoisted(() => ({ update: vi.fn(), post: vi.fn(), addAlert: vi.fn() }));
vi.mock("@/common/InvApiService", () => ({ default: { update, post } }));
vi.mock("../SamplesList", () => ({ default: () => null }));
afterEach(() => {
  update.mockReset();
  post.mockReset();
  addAlert.mockReset();
});
vi.mock("@/stores/stores/getRootStore", () => ({
  default: () => ({
    unitStore: { assertValidUnitId: () => {}, getUnit: () => ({ label: "ml" }) },
    trackingStore: { trackEvent: () => {} },
    uiStore: { addAlert, setPageNavigationConfirmation: () => {}, setDirty: () => {}, unsetDirty: () => {} },
    searchStore: { search: { setActiveResult: () => Promise.resolve() } },
    peopleStore: { currentUser: null },
  }),
}));

const GMO_KEY = "6f1c2e0a-0000-4000-8000-000000000001";
const PERMIT_KEY = "6f1c2e0a-0000-4000-8000-000000000002";
const NOTES_KEY = "6f1c2e0a-0000-4000-8000-000000000003";

const savedField = (attrs: Partial<FieldModelAttrs> & Pick<FieldModelAttrs, "type">): FieldModelAttrs => ({
  selectedOptions: null,
  columnIndex: 1,
  attachment: null,
  mandatory: false,
  content: null,
  definition: null,
  ...attrs,
});

/** A saved template where "GMO permit" is shown when "Is this a GMO?" is Yes. */
const savedGmoTemplate = () => makeMockTemplate(savedGmoTemplateAttrs());
const savedGmoTemplateAttrs = () =>
  ({
    fields: [
      savedField({
        id: 1,
        globalId: "SF1",
        name: "Is this a GMO?",
        type: "radio",
        definition: { options: ["Yes", "No"] },
        fieldKey: GMO_KEY,
      }),
      savedField({
        id: 2,
        globalId: "SF2",
        name: "GMO permit",
        type: "string",
        mandatory: true,
        fieldKey: PERMIT_KEY,
        displayRule: createDisplayRule(GMO_KEY, "IS_ANY_OF", ["Yes"]),
      }),
    ],
  }) as Parameters<typeof makeMockTemplate>[0];

const newField = (attrs: Partial<FieldModelAttrs> & Pick<FieldModelAttrs, "type">): FieldModelAttrs => ({
  ...savedField(attrs),
  initial: true,
  editing: true,
});

/** A template being created, with a radio field rules can read and a field they can show. */
const newGmoTemplate = () => {
  const template = makeMockTemplate({ id: null, globalId: null });
  template.addField(newField({ name: "Is this a GMO?", type: "radio", definition: { options: ["Yes", "No"] } }));
  template.addField(newField({ name: "GMO permit", type: "string" }));
  template.currentlyEditableFields.add("fields");
  return template;
};

/** Renders the template's fields with the form's Save button. */
function renderEditor(template: TemplateModel, confirm = vi.fn(() => Promise.resolve(true))) {
  template.currentlyEditableFields.add("fields");
  const rootStore = makeMockRootStore({
    searchStore: { activeResult: template },
    uiStore: { isLarge: true, confirm, addAlert: () => {} },
  });
  const onErrorStateChange = vi.fn();
  const view = render(
    <ThemeProvider theme={materialTheme}>
      <storesContext.Provider value={rootStore}>
        <CustomFields onErrorStateChange={onErrorStateChange} />
        <StepperActions onSubmit={() => {}} />
      </storesContext.Provider>
    </ThemeProvider>,
  );
  return { ...view, confirm, onErrorStateChange };
}

const fieldCard = (name: string) => screen.getByRole("region", { name });
const INCOMPLETE = "inventory:template.displayRules.conditionIncomplete";
const CONDITIONAL = "common:fieldEditor.condition.conditional";
const OPTIONS = "common:fieldEditor.condition.valuesLabel";

/** Makes `name` shown only when "Is this a GMO?" is `option`, from its own card. */
async function showWhen(user: ReturnType<typeof userEvent.setup>, name: string, option: string) {
  await user.click(within(fieldCard(name)).getByRole("radio", { name: CONDITIONAL }));
  await user.click(within(fieldCard(name)).getByRole("combobox", { name: OPTIONS }));
  await user.click(screen.getByRole("option", { name: option }));
  await user.keyboard("{Escape}");
}

/** Removes the last option of `name`'s condition, leaving it incomplete. */
async function clearOptions(user: ReturnType<typeof userEvent.setup>, name: string) {
  await user.click(within(fieldCard(name)).getByRole("combobox", { name: OPTIONS }));
  await user.keyboard("{Backspace}{Escape}");
}
/** The identifiers the section badge counts: those last reported in error. */
function reportedErrors(onErrorStateChange: ReturnType<typeof vi.fn>): Set<string> {
  const inError = new Set<string>();
  for (const [identifier, value] of onErrorStateChange.mock.calls as Array<[string, boolean]>) {
    if (value) inError.add(identifier);
    else inError.delete(identifier);
  }
  return inError;
}
const [gmo, permit] = [0, 1];
const fieldAt = (template: TemplateModel, index: number) => template.fields[index] as FieldModel;

describe("Template display conditions", () => {
  it("lets fields added in this session be used in a rule before the template is saved", async () => {
    const user = userEvent.setup();
    const template = makeMockTemplate({ id: null, globalId: null });
    template.addField(newField({ name: "Is this a GMO?", type: "radio", definition: { options: ["Yes", "No"] } }));
    template.addField(newField({ name: "GMO permit", type: "string" }));
    renderEditor(template);

    await showWhen(user, "GMO permit", "Yes");

    const [source, dependent] = [fieldAt(template, gmo), fieldAt(template, permit)];
    expect(source.fieldKey).toEqual(expect.any(String));
    expect(dependent.paramsForBackend).toMatchObject({
      newFieldRequest: true,
      fieldKey: dependent.fieldKey,
      displayRule: { sourceFieldKey: source.fieldKey, operator: "IS_ANY_OF", values: ["Yes"] },
    });
    expect(
      within(fieldCard("Is this a GMO?")).getByText("common:fieldEditor.condition.dependents"),
    ).toBeInTheDocument();
  });

  it("asks before deleting a field that a rule reads, then clears that rule", async () => {
    const user = userEvent.setup();
    const template = savedGmoTemplate();
    const { confirm } = renderEditor(template);

    await user.click(
      within(fieldCard("Is this a GMO?")).getByRole("button", {
        name: "inventory:fields.templateFields.customField.deleteField",
      }),
    );
    await user.click(
      screen.getByRole("menuitem", { name: "inventory:fields.templateFields.customField.deleteOptions.keep" }),
    );

    expect(confirm).toHaveBeenCalledWith(
      "inventory:template.displayRules.confirmDeleteField.title",
      "inventory:template.displayRules.confirmDeleteField.message",
      "common:actions.delete",
      "common:actions.cancel",
    );
    expect(fieldAt(template, gmo).deleteFieldRequest).toBe(true);
    expect(fieldAt(template, permit).paramsForBackend).toMatchObject({ fieldKey: PERMIT_KEY, displayRule: null });
  });

  it("keeps the field and its dependent rules when the author cancels the delete", async () => {
    const user = userEvent.setup();
    const template = savedGmoTemplate();
    renderEditor(
      template,
      vi.fn(() => Promise.resolve(false)),
    );

    await user.click(
      within(fieldCard("Is this a GMO?")).getByRole("button", {
        name: "inventory:fields.templateFields.customField.deleteField",
      }),
    );
    await user.click(
      screen.getByRole("menuitem", { name: "inventory:fields.templateFields.customField.deleteOptions.keep" }),
    );

    expect(fieldAt(template, gmo).deleteFieldRequest).toBe(false);
    expect(fieldAt(template, permit).displayRule).toEqual(createDisplayRule(GMO_KEY, "IS_ANY_OF", ["Yes"]));
  });

  it("asks before removing an option a rule uses, then drops the rule left without options", async () => {
    const user = userEvent.setup();
    const template = savedGmoTemplate();
    const { confirm } = renderEditor(template);

    const [removeYes] = within(fieldCard("Is this a GMO?")).getAllByRole("button", {
      name: "common:fieldEditor.options.remove",
    });
    await user.click(removeYes);

    expect(confirm).toHaveBeenCalledOnce();
    expect(fieldAt(template, gmo).options.map((option) => option.value)).toEqual(["No"]);
    expect(fieldAt(template, permit).displayRule).toBeNull();
  });

  it("explains why a new option a rule uses cannot be renamed", async () => {
    const user = userEvent.setup();
    renderEditor(newGmoTemplate());
    await user.click(
      within(fieldCard("Is this a GMO?")).getByRole("button", { name: "common:fieldEditor.options.add" }),
    );
    const [newOption] = within(fieldCard("Is this a GMO?"))
      .getAllByRole("textbox", {
        name: "common:fieldEditor.options.optionLabel",
      })
      .slice(-1);
    await user.type(newOption, "Maybe");
    await showWhen(user, "GMO permit", "Maybe");
    expect(within(fieldCard("Is this a GMO?")).getByText("inventory:template.displayRules.optionInUse")).toBeVisible();
  });

  it("gives no reason on a saved option a rule uses, since saved options can never be renamed", () => {
    renderEditor(savedGmoTemplate());
    const [yes] = within(fieldCard("Is this a GMO?")).getAllByRole("textbox", {
      name: "common:fieldEditor.options.optionLabel",
    });

    expect(yes).toHaveValue("Yes");
    expect(yes).toHaveAttribute("readonly");
    expect(
      within(fieldCard("Is this a GMO?")).queryByText("inventory:template.displayRules.optionInUse"),
    ).not.toBeInTheDocument();
  });

  it("moves focus to the name of a field when it is added", async () => {
    const user = userEvent.setup();
    renderEditor(newGmoTemplate());

    await user.click(screen.getByRole("button", { name: "inventory:fields.extraFields.addNewField" }));

    expect(
      within(fieldCard("common:fieldEditor.card.unnamed")).getByRole("textbox", {
        name: "common:fieldEditor.card.name",
      }),
    ).toHaveFocus();
  });

  it("asks before giving an unsaved source a type rules cannot read, then clears the rules that read it", async () => {
    const user = userEvent.setup();
    const template = makeMockTemplate({ id: null, globalId: null });
    template.addField(newField({ name: "Is this a GMO?", type: "radio", definition: { options: ["Yes", "No"] } }));
    template.addField(newField({ name: "GMO permit", type: "string" }));
    const source = fieldAt(template, gmo);
    fieldAt(template, permit).setDisplayRule(createDisplayRule(source.fieldKey ?? "", "IS_ANY_OF", ["Yes"]));
    const { confirm } = renderEditor(template);

    await user.click(
      within(fieldCard("Is this a GMO?")).getByRole("menuitem", { name: "common:fieldEditor.card.changeType" }),
    );
    await user.click(screen.getByRole("menuitem", { name: /^Number/ }));

    expect(confirm).toHaveBeenCalledOnce();
    expect(source.type).toBe("number");
    expect(fieldAt(template, permit).displayRule).toBeNull();
  });

  it("asks before giving an unsaved text source an option type, then clears the rules that read it", async () => {
    const user = userEvent.setup();
    const template = makeMockTemplate({ id: null, globalId: null });
    template.addField(newField({ name: "Batch code", type: "string" }));
    template.addField(newField({ name: "GMO permit", type: "string" }));
    const source = fieldAt(template, 0);
    fieldAt(template, permit).setDisplayRule(createDisplayRule(source.fieldKey ?? "", "CONTAINS", ["LOT"]));
    const { confirm } = renderEditor(template);

    await user.click(
      within(fieldCard("Batch code")).getByRole("menuitem", { name: "common:fieldEditor.card.changeType" }),
    );
    await user.click(screen.getByRole("menuitem", { name: /^Radio/ }));

    expect(confirm).toHaveBeenCalledOnce();
    expect(source.type).toBe("radio");
    expect(fieldAt(template, permit).displayRule).toBeNull();
  });

  it("keeps an unfinished edit when a retype of its source is cancelled, and drops it, allowing Save, when confirmed", async () => {
    const user = userEvent.setup();
    const template = makeMockTemplate({ id: null, globalId: null });
    template.addField(newField({ name: "Weight", type: "number" }));
    template.addField(newField({ name: "GMO permit", type: "string" }));
    const source = fieldAt(template, 0);
    const rule = createDisplayRule(source.fieldKey ?? "", "EQUALS", ["5"]);
    fieldAt(template, permit).setDisplayRule(rule);
    renderEditor(template, vi.fn<() => Promise<boolean>>().mockResolvedValueOnce(false).mockResolvedValueOnce(true));
    const card = within(fieldCard("GMO permit"));
    const save = screen.getByRole("button", { name: "common:actions.save" });
    const value = card.getByRole("textbox", { name: "common:fieldEditor.condition.valueLabel" });
    await user.type(value, "x");
    expect(save).toBeDisabled();
    const retypeToRadio = async () => {
      await user.click(
        within(fieldCard("Weight")).getByRole("menuitem", { name: "common:fieldEditor.card.changeType" }),
      );
      await user.click(screen.getByRole("menuitem", { name: /^Radio/ }));
    };

    await retypeToRadio();
    expect(source.type).toBe("number");
    expect(fieldAt(template, permit).displayRule).toEqual(rule);
    expect(value).toHaveValue("5x");
    expect(save).toBeDisabled();

    await retypeToRadio();
    expect(source.type).toBe("radio");
    expect(fieldAt(template, permit).displayRule).toBeNull();
    expect(card.getByRole("radio", { name: "common:fieldEditor.condition.always" })).toBeChecked();
    // a radio field needs an option before the template can be saved at all
    await user.click(within(fieldCard("Weight")).getByRole("button", { name: "common:fieldEditor.options.add" }));
    await user.type(
      within(fieldCard("Weight")).getByRole("textbox", { name: "common:fieldEditor.options.optionLabel" }),
      "Heavy",
    );
    expect(save).toBeEnabled();
  });

  it("stores a number condition, trimmed, on a field that reads a number field", async () => {
    const user = userEvent.setup();
    const template = makeMockTemplate({
      fields: [
        savedField({ id: 1, globalId: "SF1", name: "Weight", type: "number", fieldKey: GMO_KEY }),
        savedField({ id: 2, globalId: "SF2", name: "Heavy goods permit", type: "string", fieldKey: PERMIT_KEY }),
      ],
    });
    renderEditor(template);
    const card = within(fieldCard("Heavy goods permit"));

    await user.click(card.getByRole("radio", { name: CONDITIONAL }));
    await user.click(card.getByRole("combobox", { name: "common:fieldEditor.condition.operatorLabel" }));
    await user.click(screen.getByRole("option", { name: "common:fieldEditor.condition.operator.greaterThan" }));
    await user.type(card.getByRole("textbox", { name: "common:fieldEditor.condition.valueLabel" }), " 2 ");

    expect(fieldAt(template, permit).displayRule).toEqual(createDisplayRule(GMO_KEY, "GREATER_THAN", ["2"]));
  });

  it("keeps the saved rule while an edit leaves it incomplete", async () => {
    const user = userEvent.setup();
    const template = savedGmoTemplate();
    renderEditor(template);

    await clearOptions(user, "GMO permit");

    expect(fieldAt(template, permit).displayRule).toEqual(createDisplayRule(GMO_KEY, "IS_ANY_OF", ["Yes"]));
    expect(fieldAt(template, permit).paramsForBackend).not.toHaveProperty("displayRule");
  });

  it("refuses to save while a condition is incomplete, until it is completed or set to Always", async () => {
    const user = userEvent.setup();
    const { onErrorStateChange } = renderEditor(savedGmoTemplate());
    const save = screen.getByRole("button", { name: "common:actions.save" });
    const sectionError = `displayCondition:${PERMIT_KEY}`;
    expect(save).toBeEnabled();

    await clearOptions(user, "GMO permit");
    expect(save).toBeDisabled();
    expect(onErrorStateChange).toHaveBeenLastCalledWith(sectionError, true);

    await user.click(within(fieldCard("GMO permit")).getByRole("combobox", { name: OPTIONS }));
    await user.click(screen.getByRole("option", { name: "No" }));
    await user.keyboard("{Escape}");
    expect(save).toBeEnabled();
    expect(onErrorStateChange).toHaveBeenLastCalledWith(sectionError, false);

    await clearOptions(user, "GMO permit");
    expect(save).toBeDisabled();
    await user.click(
      within(fieldCard("GMO permit")).getByRole("radio", { name: "common:fieldEditor.condition.always" }),
    );
    expect(save).toBeEnabled();
    expect(onErrorStateChange).toHaveBeenLastCalledWith(sectionError, false);
  });

  it("keeps a name error on the badge while another new field's name is still blank", async () => {
    const user = userEvent.setup();
    const { onErrorStateChange } = renderEditor(newGmoTemplate());
    const nameOf = (card: HTMLElement) => within(card).getByRole("textbox", { name: "common:fieldEditor.card.name" });

    await user.clear(nameOf(fieldCard("GMO permit")));
    await user.clear(nameOf(fieldCard("Is this a GMO?")));
    const [first] = screen.getAllByRole("region", { name: "common:fieldEditor.card.unnamed" });
    await user.type(nameOf(first), "Permit");

    expect(reportedErrors(onErrorStateChange).size).toBe(1);
  });

  it("clears the name error from the badge when the field with the blank name is deleted", async () => {
    const user = userEvent.setup();
    const { onErrorStateChange } = renderEditor(newGmoTemplate());
    await user.clear(within(fieldCard("GMO permit")).getByRole("textbox", { name: "common:fieldEditor.card.name" }));
    expect(reportedErrors(onErrorStateChange).size).toBe(1);

    await user.click(
      within(fieldCard("common:fieldEditor.card.unnamed")).getByRole("button", {
        name: "inventory:fields.templateFields.customField.deleteNewField",
      }),
    );

    expect(reportedErrors(onErrorStateChange).size).toBe(0);
  });

  it("sends only the rules this edit changed, so a stale tab does not undo another author's change", async () => {
    const user = userEvent.setup();
    const attrs = savedGmoTemplateAttrs();
    const template = makeMockTemplate({
      ...attrs,
      fields: [
        ...(attrs?.fields ?? []),
        savedField({ id: 3, globalId: "SF3", name: "Notes", type: "string", fieldKey: NOTES_KEY }),
      ],
    });
    renderEditor(template);

    await showWhen(user, "Notes", "No");

    // the permit's rule was loaded and left alone; the server keeps whatever it now holds
    expect(fieldAt(template, permit).paramsForBackend).not.toHaveProperty("displayRule");
    expect(fieldAt(template, 2).paramsForBackend).toMatchObject({
      fieldKey: NOTES_KEY,
      displayRule: { sourceFieldKey: GMO_KEY, operator: "IS_ANY_OF", values: ["No"] },
    });

    await user.click(
      within(fieldCard("GMO permit")).getByRole("radio", { name: "common:fieldEditor.condition.always" }),
    );
    expect(fieldAt(template, permit).paramsForBackend).toHaveProperty("displayRule", null);
  });

  it("puts only the changed rules in the request that updates the template", async () => {
    const user = userEvent.setup();
    const attrs = savedGmoTemplateAttrs();
    const template = makeMockTemplate({
      ...attrs,
      fields: [
        ...(attrs?.fields ?? []),
        savedField({ id: 3, globalId: "SF3", name: "Notes", type: "string", fieldKey: NOTES_KEY }),
      ],
    });
    renderEditor(template);
    await showWhen(user, "Notes", "No");
    update.mockRejectedValueOnce(new Error("Only the request is under test"));

    await act(async () => {
      await template.update().catch(() => {});
    });

    expect(update).toHaveBeenCalledOnce();
    const [, , body] = update.mock.calls[0] as [string, number, { fields: Array<Record<string, unknown>> }];
    const sent = (key: string) => body.fields.find((field) => field.fieldKey === key);
    expect(sent(PERMIT_KEY)).not.toHaveProperty("displayRule");
    expect(sent(NOTES_KEY)).toHaveProperty("displayRule", {
      sourceFieldKey: GMO_KEY,
      operator: "IS_ANY_OF",
      values: ["No"],
    });
  });

  it("carries a selected default when its new option is renamed", async () => {
    const user = userEvent.setup();
    const template = makeMockTemplate({ id: null, globalId: null });
    template.addField(newField({ name: "Colour", type: "radio", definition: { options: [] } }));
    renderEditor(template);
    const card = fieldCard("Colour");

    await user.click(within(card).getByRole("button", { name: "common:fieldEditor.options.add" }));
    await user.type(within(card).getByRole("textbox", { name: "common:fieldEditor.options.optionLabel" }), "Re");
    await user.click(within(card).getByRole("radio", { name: "Re" }));
    await user.type(within(card).getByRole("textbox", { name: "common:fieldEditor.options.optionLabel" }), "d");

    expect(fieldAt(template, 0).selectedOptions).toEqual(["Red"]);
  });

  it.each([
    ["radio", ["A"], []],
    ["choice", ["A", "C"], ["C"]],
  ] as const)("drops a removed option from a %s field's default", async (type, selected, expected) => {
    const user = userEvent.setup();
    const template = makeMockTemplate({
      fields: [
        savedField({
          id: 1,
          globalId: "SF1",
          name: "Letters",
          type,
          definition: { options: ["A", "B", "C"] },
          selectedOptions: [...selected],
          fieldKey: GMO_KEY,
        }),
      ],
    });
    renderEditor(template);

    const [removeA] = within(fieldCard("Letters")).getAllByRole("button", {
      name: "common:fieldEditor.options.remove",
    });
    await user.click(removeA);

    expect(fieldAt(template, 0).selectedOptions).toEqual(expected);
  });

  it("is accessible", async () => {
    const { baseElement } = renderEditor(savedGmoTemplate());
    await expectAccessible(baseElement);
  });
});

/** GMO? → GMO permit (a radio shown when GMO? is Yes) → Notes (shown when the permit is Granted). */
const chainTemplate = () =>
  makeMockTemplate({
    fields: [
      savedField({
        id: 1,
        globalId: "SF1",
        name: "Is this a GMO?",
        type: "radio",
        definition: { options: ["Yes", "No"] },
        fieldKey: GMO_KEY,
      }),
      savedField({
        id: 2,
        globalId: "SF2",
        name: "GMO permit",
        type: "radio",
        definition: { options: ["Granted", "Pending"] },
        fieldKey: PERMIT_KEY,
        displayRule: createDisplayRule(GMO_KEY, "IS_ANY_OF", ["Yes"]),
      }),
      savedField({
        id: 3,
        globalId: "SF3",
        name: "Notes",
        type: "string",
        fieldKey: NOTES_KEY,
        displayRule: createDisplayRule(PERMIT_KEY, "IS_ANY_OF", ["Granted"]),
      }),
    ],
  });

/** Deletes a field, keeping it in existing samples when it is saved. */
async function deleteField(user: ReturnType<typeof userEvent.setup>, name: string, saved = true) {
  const card = fieldCard(name);
  if (!saved) {
    await user.click(
      within(card).getByRole("button", { name: "inventory:fields.templateFields.customField.deleteNewField" }),
    );
    return;
  }
  await user.click(
    within(card).getByRole("button", { name: "inventory:fields.templateFields.customField.deleteField" }),
  );
  await user.click(
    screen.getByRole("menuitem", { name: "inventory:fields.templateFields.customField.deleteOptions.keep" }),
  );
}

describe("Deleting fields while editing display rules", () => {
  it("lets the template be saved once a field with an incomplete condition is deleted", async () => {
    const user = userEvent.setup();
    renderEditor(savedGmoTemplate());
    await clearOptions(user, "GMO permit");
    expect(screen.getByRole("button", { name: "common:actions.save" })).toBeDisabled();

    await deleteField(user, "GMO permit");

    expect(screen.getByRole("button", { name: "common:actions.save" })).toBeEnabled();
  });

  it("keeps a draft whose only field to read is deleted, saying so and refusing to save", async () => {
    const user = userEvent.setup();
    const template = newGmoTemplate();
    renderEditor(template);
    await user.click(within(fieldCard("GMO permit")).getByRole("radio", { name: CONDITIONAL }));

    await deleteField(user, "Is this a GMO?", false);

    expect(within(fieldCard("GMO permit")).getByRole("radio", { name: CONDITIONAL })).toBeChecked();
    const source = within(fieldCard("GMO permit")).getByRole("combobox", {
      name: "common:fieldEditor.condition.sourceLabel",
    });
    expect(source).toHaveAccessibleDescription("common:fieldEditor.condition.noOtherFields");
    expect(screen.getByRole("button", { name: "common:actions.save" })).toBeDisabled();
    expect(template.submittable.isOk).toBe(false);
    await user.click(source);
    expect(screen.getByRole("option", { name: "common:fieldEditor.condition.noOtherFields" })).toHaveAttribute(
      "aria-disabled",
      "true",
    );
  });

  it("deletes a saved dependent without asking, leaving its source alone and removing its rule", async () => {
    const user = userEvent.setup();
    const template = savedGmoTemplate();
    const { confirm } = renderEditor(template);

    await deleteField(user, "GMO permit");

    expect(confirm).not.toHaveBeenCalled();
    expect(fieldAt(template, gmo).deleteFieldRequest).toBe(false);
    expect(fieldAt(template, gmo).options.map((option) => option.value)).toEqual(["Yes", "No"]);
    expect(
      within(fieldCard("Is this a GMO?")).queryByText("common:fieldEditor.condition.dependents"),
    ).not.toBeInTheDocument();
  });

  it("sends no rule for a dependent and its source that are both deleted", async () => {
    const user = userEvent.setup();
    const template = savedGmoTemplate();
    const { confirm } = renderEditor(template);

    await deleteField(user, "GMO permit");
    await deleteField(user, "Is this a GMO?");

    expect(confirm).not.toHaveBeenCalled();
    for (const field of template.fields as Array<FieldModel>) {
      expect(field.paramsForBackend).toMatchObject({ deleteFieldRequest: true });
      expect(field.paramsForBackend).not.toHaveProperty("displayRule");
    }
  });

  it("asks before deleting the middle of a chain, then clears the rule that read it", async () => {
    const user = userEvent.setup();
    const template = chainTemplate();
    const { confirm } = renderEditor(template);

    await deleteField(user, "GMO permit");

    expect(confirm).toHaveBeenCalledOnce();
    expect(fieldAt(template, 2).paramsForBackend).toHaveProperty("displayRule", null);
    expect(fieldAt(template, gmo).deleteFieldRequest).toBe(false);
    expect(fieldAt(template, gmo).paramsForBackend).not.toHaveProperty("displayRule");
  });

  it("removes an unsaved source from an unsaved template and clears the rule that read it", async () => {
    const user = userEvent.setup();
    const template = makeMockTemplate({ id: null, globalId: null });
    template.addField(newField({ name: "Is this a GMO?", type: "radio", definition: { options: ["Yes", "No"] } }));
    template.addField(newField({ name: "GMO permit", type: "string" }));
    const [source, dependent] = [fieldAt(template, gmo), fieldAt(template, permit)];
    dependent.setDisplayRule(createDisplayRule(source.fieldKey ?? "", "IS_ANY_OF", ["Yes"]));
    const { confirm } = renderEditor(template);

    await deleteField(user, "Is this a GMO?", false);

    expect(confirm).toHaveBeenCalledOnce();
    expect(template.fields).toEqual([dependent]);
    expect(dependent.displayRule).toBeNull();
  });
});

describe("Display condition drafts across saves and edit sessions", () => {
  /** Leaves an incomplete condition on "GMO permit", whether or not it had a rule. */
  const addDraft = async (user: ReturnType<typeof userEvent.setup>) => {
    const conditional = within(fieldCard("GMO permit")).getByRole("radio", { name: CONDITIONAL });
    if ((conditional as HTMLInputElement).checked) await clearOptions(user, "GMO permit");
    else await user.click(conditional);
  };
  const draftCards = () => screen.queryAllByText("common:fieldEditor.condition.valuesRequired");

  it("sends no update while a condition is incomplete", async () => {
    const user = userEvent.setup();
    const template = savedGmoTemplate();
    renderEditor(template);
    await addDraft(user);

    await expect(template.update()).rejects.toThrow(INCOMPLETE);

    expect(update).not.toHaveBeenCalled();
    expect(draftCards()).toHaveLength(1);
  });

  it("sends nothing on Retry after a refused save when a condition was left unfinished since", async () => {
    const user = userEvent.setup();
    const template = savedGmoTemplate();
    renderEditor(template);
    update.mockRejectedValueOnce({
      response: { status: 400, data: { data: { validationErrors: [{ field: "name", message: "Taken" }] } } },
    });
    await act(async () => {
      await template.update().catch(() => {});
    });
    const [[{ retryFunction }]] = addAlert.mock.calls as Array<[{ retryFunction: () => Promise<void> }]>;
    await addDraft(user);

    await act(async () => {
      await expect(retryFunction()).resolves.toBeUndefined();
    });

    expect(update).toHaveBeenCalledOnce();
    // a failed upload leaves a progress bar in the button, which joins its name
    expect(screen.getByRole("button", { name: /common:actions\.save/ })).toBeDisabled();
    expect(screen.getByRole("alert")).toHaveTextContent(INCOMPLETE);
  });

  it("rejects the Retry with the error of a save that fails again", async () => {
    const template = savedGmoTemplate();
    renderEditor(template);
    update.mockRejectedValueOnce({
      response: { status: 400, data: { data: { validationErrors: [{ field: "name", message: "Taken" }] } } },
    });
    await act(async () => {
      await template.update().catch(() => {});
    });
    const [[{ retryFunction }]] = addAlert.mock.calls as Array<[{ retryFunction: () => Promise<void> }]>;
    const serverError = { response: { status: 500, data: {} } };
    update.mockRejectedValueOnce(serverError);

    await act(async () => {
      await expect(retryFunction()).rejects.toBe(serverError);
    });

    expect(update).toHaveBeenCalledTimes(2);
  });

  it("sends the create again on Retry after the server refuses a new template", async () => {
    const user = userEvent.setup();
    const template = newGmoTemplate();
    renderEditor(template);
    post.mockRejectedValueOnce({
      response: { status: 400, data: { data: { validationErrors: [{ field: "name", message: "Taken" }] } } },
    });
    await act(async () => {
      await template.create().catch(() => {});
    });
    const [[{ retryFunction }]] = addAlert.mock.calls as Array<[{ retryFunction: () => Promise<void> }]>;
    await user.type(
      within(fieldCard("GMO permit")).getByRole("textbox", { name: "common:fieldEditor.card.name" }),
      "s",
    );
    post.mockResolvedValueOnce({ data: { ...templateAttrs({ id: 9, globalId: "IT9" }), attachments: [] } });

    await act(async () => {
      await retryFunction();
    });

    expect(post).toHaveBeenCalledTimes(2);
    expect(update).not.toHaveBeenCalled();
    expect(template.globalId).toBe("IT9");
  });

  it("drops a draft when the template's fields are replaced by new models with the same keys", async () => {
    const user = userEvent.setup();
    const template = savedGmoTemplate();
    template.setAttributes({ editing: true });
    renderEditor(template);
    await addDraft(user);
    expect(draftCards()).toHaveLength(1);

    act(() => template.overrideFields(savedGmoTemplateAttrs()?.fields ?? []));

    expect(fieldAt(template, permit).fieldKey).toBe(PERMIT_KEY);
    expect(draftCards()).toHaveLength(0);
    expect(screen.getByRole("button", { name: "common:actions.save" })).toBeEnabled();
  });

  it("starts without drafts, and can be saved, for another template or after leaving and re-entering edit mode", async () => {
    const user = userEvent.setup();
    const first = savedGmoTemplate();
    const second = makeMockTemplate({ ...savedGmoTemplateAttrs(), id: 2, globalId: "IT2" });
    for (const template of [first, second]) {
      template.currentlyEditableFields.add("fields");
      template.setAttributes({ editing: true });
    }
    const searchStore = observable({ activeResult: first }, { activeResult: observable.ref });
    render(
      <ThemeProvider theme={materialTheme}>
        <storesContext.Provider
          value={makeMockRootStore({ searchStore, uiStore: { isLarge: true, confirm: vi.fn() } })}
        >
          <CustomFields onErrorStateChange={() => {}} />
          <StepperActions onSubmit={() => {}} />
        </storesContext.Provider>
      </ThemeProvider>,
    );

    await addDraft(user);
    act(() => runInAction(() => (searchStore.activeResult = second)));
    expect(draftCards()).toHaveLength(0);
    expect(screen.getByRole("button", { name: "common:actions.save" })).toBeEnabled();

    await addDraft(user);
    act(() => second.setAttributes({ editing: false }));
    act(() => second.setAttributes({ editing: true }));
    expect(draftCards()).toHaveLength(0);

    // two templates that are both being created have no global id to tell them apart
    const [third, fourth] = [newGmoTemplate(), newGmoTemplate()];
    act(() => runInAction(() => (searchStore.activeResult = third)));
    await addDraft(user);
    act(() => runInAction(() => (searchStore.activeResult = fourth)));
    expect(draftCards()).toHaveLength(0);
  });

  it("sends no create while a condition is incomplete, and keeps a completed one when creating fails", async () => {
    const user = userEvent.setup();
    const template = newGmoTemplate();
    renderEditor(template);
    await addDraft(user);

    await expect(template.create()).rejects.toThrow(INCOMPLETE);
    expect(post).not.toHaveBeenCalled();
    expect(draftCards()).toHaveLength(1);

    await user.click(within(fieldCard("GMO permit")).getByRole("combobox", { name: OPTIONS }));
    await user.click(screen.getByRole("option", { name: "Yes" }));
    await user.keyboard("{Escape}");
    post.mockRejectedValueOnce(new Error("The create failed"));
    await act(async () => {
      await template.create().catch(() => {});
    });
    expect(post).toHaveBeenCalledOnce();
    expect(within(fieldCard("GMO permit")).getByRole("button", { name: "Yes" })).toBeInTheDocument();

    post.mockResolvedValueOnce({ data: { ...templateAttrs({ id: 9, globalId: "IT9" }), attachments: [] } });
    await act(async () => {
      await template.create();
    });
    expect(template.globalId).toBe("IT9");
  });
});

describe("Display conditions in the template form", () => {
  it("marks the collapsed Custom Fields section when a stored condition cannot be saved as it is", () => {
    // the field this rule reads is not in the template
    const template = makeMockTemplate({ fields: savedGmoTemplateAttrs()?.fields?.slice(1) });
    render(
      <ThemeProvider theme={materialTheme}>
        <storesContext.Provider
          value={makeMockRootStore({
            searchStore: {
              activeResult: template,
              search: { activeResult: template, processingContextActions: false, fetcher: { basketSearch: false } },
            },
            uiStore: { isLarge: true, confirm: vi.fn() },
            unitStore: {
              getUnit: () => ({ id: 1, label: "items", category: "dimensionless", description: "" }),
              unitsOfCategory: () => [],
            },
          })}
        >
          <FormSectionsContext.Provider
            value={{
              isExpanded: (_type, section) => section !== "customFields",
              setExpanded: () => {},
              setAllExpanded: () => {},
            }}
          >
            <TemplateForm />
          </FormSectionsContext.Provider>
        </storesContext.Provider>
      </ThemeProvider>,
    );

    act(() => {
      template.currentlyEditableFields.add("fields");
      template.setAttributes({ editing: true });
    });

    const section = screen.getByRole("region", { name: "inventory:formSections.customFields" });
    expect(within(section).getByLabelText("inventory:formSections.expandSection")).toHaveTextContent("1");
  });
});

describe("Instrument template display conditions", () => {
  it("refuses to save while a condition has no field to read", async () => {
    const user = userEvent.setup();
    const template = makeMockInstrumentTemplate();
    template.currentlyEditableFields.add("fields");
    template.addField(newField({ name: "Calibrated?", type: "radio", definition: { options: ["Yes"] } }));
    render(
      <ThemeProvider theme={materialTheme}>
        <storesContext.Provider
          value={makeMockRootStore({
            searchStore: { activeResult: template },
            uiStore: { isLarge: true, confirm: vi.fn() },
          })}
        >
          <InstrumentTemplateFields onErrorStateChange={() => {}} />
        </storesContext.Provider>
      </ThemeProvider>,
    );
    expect(template.submittable.isOk).toBe(true);

    await user.click(within(fieldCard("Calibrated?")).getByRole("radio", { name: CONDITIONAL }));

    expect(template.submittable.isOk).toBe(false);
    await expect(template.update()).rejects.toThrow(INCOMPLETE);
    await expect(template.create()).rejects.toThrow(INCOMPLETE);
    expect(update).not.toHaveBeenCalled();
    expect(post).not.toHaveBeenCalled();
  });

  it("moves focus to the name of a field when it is added", async () => {
    const user = userEvent.setup();
    const template = makeMockInstrumentTemplate();
    template.currentlyEditableFields.add("fields");
    render(
      <ThemeProvider theme={materialTheme}>
        <storesContext.Provider
          value={makeMockRootStore({
            searchStore: { activeResult: template },
            uiStore: { isLarge: true, confirm: vi.fn() },
          })}
        >
          <InstrumentTemplateFields onErrorStateChange={() => {}} />
        </storesContext.Provider>
      </ThemeProvider>,
    );

    await user.click(screen.getByRole("button", { name: "inventory:fields.extraFields.addNewField" }));

    expect(
      within(fieldCard("common:fieldEditor.card.unnamed")).getByRole("textbox", {
        name: "common:fieldEditor.card.name",
      }),
    ).toHaveFocus();
  });
});

describe("Display conditions outside a secure context", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("shows a template and adds a field without crypto.randomUUID", async () => {
    const { getRandomValues } = globalThis.crypto;
    vi.stubGlobal("crypto", { getRandomValues: getRandomValues.bind(globalThis.crypto) });
    expect(globalThis.crypto.randomUUID).toBeUndefined();
    const user = userEvent.setup();
    const template = savedGmoTemplate();
    renderEditor(template);

    await user.click(screen.getByRole("button", { name: "inventory:fields.extraFields.addNewField" }));

    expect(fieldAt(template, 2).fieldKey).toEqual(expect.any(String));
    expect(
      within(fieldCard("common:fieldEditor.card.unnamed")).getByRole("group", {
        name: "common:fieldEditor.condition.title",
      }),
    ).toBeInTheDocument();
  });

  it("adds a field to an instrument template without crypto.randomUUID", async () => {
    const { getRandomValues } = globalThis.crypto;
    vi.stubGlobal("crypto", { getRandomValues: getRandomValues.bind(globalThis.crypto) });
    expect(globalThis.crypto.randomUUID).toBeUndefined();
    const user = userEvent.setup();
    const template = makeMockInstrumentTemplate();
    template.currentlyEditableFields.add("fields");
    render(
      <ThemeProvider theme={materialTheme}>
        <storesContext.Provider
          value={makeMockRootStore({
            searchStore: { activeResult: template },
            uiStore: { isLarge: true, confirm: vi.fn() },
          })}
        >
          <InstrumentTemplateFields onErrorStateChange={() => {}} />
        </storesContext.Provider>
      </ThemeProvider>,
    );

    await user.click(screen.getByRole("button", { name: "inventory:fields.extraFields.addNewField" }));

    expect((template.fields[0] as FieldModel).fieldKey).toEqual(expect.any(String));
    expect(
      within(fieldCard("common:fieldEditor.card.unnamed")).getByRole("group", {
        name: "common:fieldEditor.condition.title",
      }),
    ).toBeInTheDocument();
  });
});
