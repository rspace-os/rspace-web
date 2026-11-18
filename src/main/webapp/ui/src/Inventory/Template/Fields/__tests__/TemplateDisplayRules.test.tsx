import { describe, expect, it, vi } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import { ThemeProvider } from "@mui/material/styles";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expectAccessible } from "@/__tests__/accessibility";
import { createDisplayRule } from "@/modules/common/field-rules/displayRule";
import { makeMockInstrumentTemplate } from "@/stores/models/__tests__/InstrumentTemplateModel/mocking";
import { makeMockTemplate } from "@/stores/models/__tests__/TemplateModel/mocking";
import type FieldModel from "@/stores/models/FieldModel";
import type { FieldModelAttrs } from "@/stores/models/FieldModel";
import type TemplateModel from "@/stores/models/TemplateModel";
import { makeMockRootStore } from "@/stores/stores/__tests__/RootStore/mocking";
import { storesContext } from "@/stores/stores-context";
import materialTheme from "@/theme";
import InstrumentTemplateFields from "../../../InstrumentTemplate/Fields/CustomFields";
import CustomFields from "../CustomFields";

vi.mock("@/common/InvApiService", () => ({ default: {} }));
vi.mock("@/stores/stores/getRootStore", () => ({
  default: () => ({
    unitStore: { assertValidUnitId: () => {}, getUnit: () => ({ label: "ml" }) },
    trackingStore: { trackEvent: () => {} },
    uiStore: { addAlert: () => {}, setPageNavigationConfirmation: () => {}, setDirty: () => {} },
  }),
}));

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
const savedGmoTemplate = () =>
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
        type: "string",
        mandatory: true,
        fieldKey: PERMIT_KEY,
        displayRule: { sourceFieldKey: GMO_KEY, operator: "IS_ANY_OF", values: ["Yes"] },
      }),
    ],
  } as Parameters<typeof makeMockTemplate>[0]);

const newField = (attrs: Partial<FieldModelAttrs> & Pick<FieldModelAttrs, "type">): FieldModelAttrs => ({
  ...savedField(attrs),
  initial: true,
  editing: true,
});

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
    expect(source.fieldKey).toMatch(/^[0-9a-f-]{36}$/);
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

  it("asks before giving an unsaved source a type rules cannot read, then clears the rules that read it", async () => {
    const user = userEvent.setup();
    const template = makeMockTemplate({ id: null, globalId: null });
    template.addField(newField({ name: "Is this a GMO?", type: "radio", definition: { options: ["Yes", "No"] } }));
    template.addField(newField({ name: "GMO permit", type: "string" }));
    const source = fieldAt(template, gmo);
    fieldAt(template, permit).setAttributes({
      displayRule: createDisplayRule(source.fieldKey ?? "", "IS_ANY_OF", ["Yes"]),
    });
    const { confirm } = renderEditor(template);

    await user.click(
      within(fieldCard("Is this a GMO?")).getByRole("menuitem", { name: "common:fieldEditor.card.changeType" }),
    );
    await user.click(screen.getByRole("menuitem", { name: /^Number/ }));

    expect(confirm).toHaveBeenCalledOnce();
    expect(source.type).toBe("number");
    expect(fieldAt(template, permit).displayRule).toBeNull();
  });

  it("is accessible", async () => {
    const { baseElement } = renderEditor(savedGmoTemplate());
    await expectAccessible(baseElement);
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

    expect((template.fields[0] as FieldModel).fieldKey).toMatch(/^[0-9a-f-]{36}$/);
    expect(screen.getByRole("region", { name: "common:fieldEditor.rules.title" })).toBeInTheDocument();
  });
});
