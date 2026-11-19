import { describe, expect, it, vi } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import { ThemeProvider } from "@mui/material/styles";
import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { observable, runInAction } from "mobx";
import * as v from "valibot";
import { expectAccessible } from "@/__tests__/accessibility";
import { createDisplayRule } from "@/modules/common/field-rules/displayRule";
import { makeMockInstrumentTemplate } from "@/stores/models/__tests__/InstrumentTemplateModel/mocking";
import { makeMockTemplate, templateAttrs } from "@/stores/models/__tests__/TemplateModel/mocking";
import type FieldModel from "@/stores/models/FieldModel";
import type { FieldModelAttrs } from "@/stores/models/FieldModel";
import type TemplateModel from "@/stores/models/TemplateModel";
import { makeMockRootStore } from "@/stores/stores/__tests__/RootStore/mocking";
import { storesContext } from "@/stores/stores-context";
import materialTheme from "@/theme";
import InstrumentTemplateFields from "../../../InstrumentTemplate/Fields/CustomFields";
import CustomFields from "../CustomFields";

const { update, post } = vi.hoisted(() => ({ update: vi.fn(), post: vi.fn() }));
vi.mock("@/common/InvApiService", () => ({ default: { update, post } }));
vi.mock("@/stores/stores/getRootStore", () => ({
  default: () => ({
    unitStore: { assertValidUnitId: () => {}, getUnit: () => ({ label: "ml" }) },
    trackingStore: { trackEvent: () => {} },
    uiStore: { addAlert: () => {}, setPageNavigationConfirmation: () => {}, setDirty: () => {}, unsetDirty: () => {} },
    searchStore: { search: { setActiveResult: () => Promise.resolve() } },
    peopleStore: { currentUser: null },
  }),
}));

const UUID = v.pipe(v.string(), v.uuid());
const GMO_KEY = "6f1c2e0a-0000-4000-8000-000000000001";
const PERMIT_KEY = "6f1c2e0a-0000-4000-8000-000000000002";

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

function renderEditor(template: TemplateModel, confirm = vi.fn(() => Promise.resolve(true))) {
  template.currentlyEditableFields.add("fields");
  const rootStore = makeMockRootStore({
    searchStore: { activeResult: template },
    uiStore: { isLarge: true, confirm, addAlert: () => {} },
  });
  const view = render(
    <ThemeProvider theme={materialTheme}>
      <storesContext.Provider value={rootStore}>
        <CustomFields onErrorStateChange={() => {}} />
      </storesContext.Provider>
    </ThemeProvider>,
  );
  return { ...view, confirm };
}

const fieldCard = (name: string) => screen.getByRole("region", { name });
const [gmo, permit] = [0, 1];
const fieldAt = (template: TemplateModel, index: number) => template.fields[index] as FieldModel;

describe("Template display rules", () => {
  it("lets fields added in this session be used in a rule before the template is saved", async () => {
    const user = userEvent.setup();
    const template = makeMockTemplate({ id: null, globalId: null });
    template.addField(newField({ name: "Is this a GMO?", type: "radio", definition: { options: ["Yes", "No"] } }));
    template.addField(newField({ name: "GMO permit", type: "string" }));
    renderEditor(template);

    await user.click(screen.getByRole("button", { name: "common:fieldEditor.rules.addRule" }));
    await user.click(screen.getByRole("combobox", { name: "common:fieldEditor.rules.valuesLabel" }));
    await user.click(screen.getByRole("option", { name: "Yes" }));
    await user.click(screen.getByRole("combobox", { name: "common:fieldEditor.rules.targetsLabel" }));
    await user.click(screen.getByRole("option", { name: "GMO permit" }));

    const [source, dependent] = [fieldAt(template, gmo), fieldAt(template, permit)];
    expect(v.is(UUID, source.fieldKey)).toBe(true);
    expect(dependent.paramsForBackend).toMatchObject({
      newFieldRequest: true,
      fieldKey: dependent.fieldKey,
      displayRule: { sourceFieldKey: source.fieldKey, operator: "IS_ANY_OF", values: ["Yes"] },
    });
    expect(within(fieldCard("GMO permit")).getByText("common:fieldEditor.summary.isAnyOf")).toBeInTheDocument();
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
    await user.click(screen.getByRole("button", { name: "common:fieldEditor.rules.addRule" }));
    await user.click(screen.getByRole("combobox", { name: "common:fieldEditor.rules.valuesLabel" }));
    await user.click(screen.getByRole("option", { name: "Maybe" }));
    await user.click(screen.getByRole("combobox", { name: "common:fieldEditor.rules.targetsLabel" }));
    await user.click(screen.getByRole("option", { name: "GMO permit" }));
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

  it("keeps the saved rule in the save while an edit leaves it incomplete", async () => {
    const user = userEvent.setup();
    const template = savedGmoTemplate();
    renderEditor(template);

    await user.click(screen.getByRole("combobox", { name: "common:fieldEditor.rules.valuesLabel" }));
    await user.keyboard("{Backspace}");

    expect(fieldAt(template, permit).paramsForBackend).toMatchObject({
      displayRule: { sourceFieldKey: GMO_KEY, operator: "IS_ANY_OF", values: ["Yes"] },
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

const NOTES_KEY = "6f1c2e0a-0000-4000-8000-000000000003";

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

const ruleCards = () =>
  within(screen.getByRole("region", { name: "common:fieldEditor.rules.title" })).queryAllByRole("listitem");

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
  it("deletes a saved dependent without asking, leaving its source alone and removing its rule", async () => {
    const user = userEvent.setup();
    const template = savedGmoTemplate();
    const { confirm } = renderEditor(template);

    await deleteField(user, "GMO permit");

    expect(confirm).not.toHaveBeenCalled();
    expect(fieldAt(template, gmo).deleteFieldRequest).toBe(false);
    expect(fieldAt(template, gmo).options.map((option) => option.value)).toEqual(["Yes", "No"]);
    expect(ruleCards()).toHaveLength(0);
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

  it("drops an incomplete edit whose only field is deleted", async () => {
    const user = userEvent.setup();
    const template = savedGmoTemplate();
    renderEditor(template);

    await user.click(screen.getByRole("combobox", { name: "common:fieldEditor.rules.valuesLabel" }));
    await user.keyboard("{Backspace}{Escape}");
    expect(ruleCards()).toHaveLength(1);
    await deleteField(user, "GMO permit");

    expect(ruleCards()).toHaveLength(0);
  });
});

describe("Display rule drafts across saves and edit sessions", () => {
  const addDraft = async (user: ReturnType<typeof userEvent.setup>) => {
    await user.click(screen.getByRole("button", { name: "common:fieldEditor.rules.addRule" }));
  };
  const draftCards = () =>
    within(screen.getByRole("region", { name: "common:fieldEditor.rules.title" })).queryAllByRole("listitem", {
      name: "common:fieldEditor.rules.newRuleHeading",
    });

  it("keeps drafts and incomplete edits when a save fails", async () => {
    const user = userEvent.setup();
    const template = savedGmoTemplate();
    renderEditor(template);
    await addDraft(user);
    await user.click(screen.getAllByRole("combobox", { name: "common:fieldEditor.rules.valuesLabel" })[0]);
    await user.keyboard("{Backspace}{Escape}");
    const save = Promise.withResolvers<never>();
    update.mockReturnValueOnce(save.promise);

    let saving: Promise<void> = Promise.resolve();
    act(() => {
      saving = template.update().catch(() => {});
    });
    // while saving the panel is read-only
    expect(screen.queryByRole("button", { name: "common:fieldEditor.rules.addRule" })).not.toBeInTheDocument();
    await act(async () => {
      save.reject(new Error("The save failed"));
      await saving;
    });

    expect(update).toHaveBeenCalledOnce();
    expect(draftCards()).toHaveLength(1);
    expect(screen.getByText("common:fieldEditor.rules.incompleteEdit")).toBeInTheDocument();
  });

  it("starts without drafts for another template, or after leaving and re-entering edit mode", async () => {
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
        </storesContext.Provider>
      </ThemeProvider>,
    );

    await addDraft(user);
    act(() => runInAction(() => (searchStore.activeResult = second)));
    expect(draftCards()).toHaveLength(0);

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

  it("keeps drafts when creating the template fails, and starts afresh once it is created", async () => {
    const user = userEvent.setup();
    const template = newGmoTemplate();
    renderEditor(template);
    await addDraft(user);

    post.mockRejectedValueOnce(new Error("The create failed"));
    await act(async () => {
      await template.create().catch(() => {});
    });
    expect(draftCards()).toHaveLength(1);

    post.mockResolvedValueOnce({ data: { ...templateAttrs({ id: 9, globalId: "IT9" }), attachments: [] } });
    await act(async () => {
      await template.create();
    });
    expect(template.globalId).toBe("IT9");
    expect(draftCards()).toHaveLength(0);
  });
});

describe("Instrument template display rules", () => {
  it("gives new fields a key and mounts the rules panel above the fields", () => {
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

    expect(v.is(UUID, (template.fields[0] as FieldModel).fieldKey)).toBe(true);
    expect(screen.getByRole("region", { name: "common:fieldEditor.rules.title" })).toBeInTheDocument();
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
