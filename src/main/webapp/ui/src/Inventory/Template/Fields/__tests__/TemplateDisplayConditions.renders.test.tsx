import { describe, expect, it, vi } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import { ThemeProvider } from "@mui/material/styles";
import { fireEvent, render, screen, within } from "@testing-library/react";
import type { ComponentProps } from "react";
import { createDisplayRule } from "@/modules/common/field-rules/displayRule";
import { makeMockTemplate } from "@/stores/models/__tests__/TemplateModel/mocking";
import type { FieldModelAttrs } from "@/stores/models/FieldModel";
import { makeMockRootStore } from "@/stores/stores/__tests__/RootStore/mocking";
import { storesContext } from "@/stores/stores-context";
import materialTheme from "@/theme";
import CustomFields from "../CustomFields";

const renders = vi.hoisted(() => new Map<string, number>());

/** Counts how often each field's display condition renders. */
vi.mock("@/modules/common/field-editor/FieldDisplayCondition", async (importOriginal) => {
  const { default: FieldDisplayCondition } =
    await importOriginal<typeof import("@/modules/common/field-editor/FieldDisplayCondition")>();
  return {
    default: function CountedFieldDisplayCondition(props: ComponentProps<typeof FieldDisplayCondition>) {
      renders.set(props.fieldKey, (renders.get(props.fieldKey) ?? 0) + 1);
      return FieldDisplayCondition(props);
    },
  };
});
vi.mock("@/common/InvApiService", () => ({ default: {} }));
vi.mock("@/stores/stores/getRootStore", () => ({
  default: () => ({
    unitStore: { assertValidUnitId: () => {}, getUnit: () => ({ label: "ml" }) },
    uiStore: { addAlert: () => {}, setPageNavigationConfirmation: () => {}, setDirty: () => {}, unsetDirty: () => {} },
  }),
}));

const COUNT = 30;
const keyOf = (i: number) => `6f1c2e0a-0000-4000-8000-${String(i).padStart(12, "0")}`;

/** Even fields are radios; each odd field is shown when the radio above it is Yes. */
const template = () => {
  const record = makeMockTemplate({
    fields: Array.from(
      { length: COUNT },
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
  record.currentlyEditableFields.add("fields");
  return record;
};

const rename = (from: string, to: string) =>
  fireEvent.change(within(screen.getByRole("region", { name: from })).getByRole("textbox", { name: /card\.name/ }), {
    target: { value: to },
  });

describe("Renaming a field of a large template", () => {
  it("re-renders only the display conditions that show that name", () => {
    render(
      <ThemeProvider theme={materialTheme}>
        <storesContext.Provider
          value={makeMockRootStore({
            searchStore: { activeResult: template() },
            uiStore: { isLarge: true, confirm: vi.fn() },
          })}
        >
          <CustomFields onErrorStateChange={() => {}} />
        </storesContext.Provider>
      </ThemeProvider>,
    );
    expect(renders.size).toBe(COUNT);

    renders.clear();
    rename("Field 3", "Notes");
    // a text field: its own card, and the field it reads, which lists it as a dependent
    expect([...renders.keys()].sort()).toEqual([keyOf(2), keyOf(3)]);

    renders.clear();
    rename("Field 4", "Is this a GMO?");
    // a radio: its own card and the one field that reads it, not the other cards that could read it
    expect([...renders.keys()].sort()).toEqual([keyOf(4), keyOf(5)]);
    expect(
      within(screen.getByRole("region", { name: "Field 5" })).getByRole("combobox", { name: /sourceLabel/ }),
    ).toHaveTextContent("Is this a GMO?");
  });
});
