import { describe, expect, it, vi } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import { ThemeProvider } from "@mui/material/styles";
import { fireEvent, screen, within } from "@testing-library/react";
import type { ComponentProps } from "react";
import { renderWithRealI18n } from "@/__tests__/helpers/realI18n";
import ConfirmProvider from "@/components/ConfirmProvider";
import { createDisplayRule } from "@/modules/common/field-rules/displayRule";
import common from "@/modules/common/i18n/locales/en-US/common.json";
import workspace from "@/modules/common/i18n/locales/en-US/workspace.json";
import materialTheme from "@/theme";
import type { EditorField } from "../editorModel";
import FormFieldEditor from "../FormFieldEditor";

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

const COUNT = 30;
const keyOf = (i: number) => `6f1c2e0a-0000-4000-8000-${String(i).padStart(12, "0")}`;

/** Even fields are radios; each odd field is shown when the radio above it is Yes. */
const fields = Array.from({ length: COUNT }, (_, i): EditorField => {
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

const rename = (from: string, to: string) =>
  fireEvent.change(within(screen.getByRole("region", { name: from })).getByRole("textbox", { name: "Name" }), {
    target: { value: to },
  });

describe("Renaming a field of a large form", () => {
  it("re-renders only the display conditions that can show that name", async () => {
    await renderWithRealI18n(
      <ThemeProvider theme={materialTheme}>
        <ConfirmProvider>
          <FormFieldEditor
            initialFields={fields}
            onSave={async (saved) => ({ fields: saved, version: 1 })}
            onCheck={async () => ({ version: 0, fields: null })}
            editable
          />
        </ConfirmProvider>
      </ThemeProvider>,
      { resources: { common, workspace }, defaultNS: "workspace" },
    );
    expect(renders.size).toBe(COUNT);

    renders.clear();
    rename("Field 3", "Notes");
    // a text field: its own card, and the field it reads, which lists it as a dependent
    expect([...renders.keys()].sort()).toEqual([keyOf(2), keyOf(3)]);

    renders.clear();
    rename("Field 4", "Is this a GMO?");
    // a radio: its own card, and the field that reads it
    expect([...renders.keys()].sort()).toEqual([keyOf(4), keyOf(5)]);
    expect(
      within(screen.getByRole("region", { name: "Field 5" })).getByRole("combobox", { name: "Field" }),
    ).toHaveTextContent("Is this a GMO?");

    // a condition being drafted can read any radio, so it shows a renamed one's new name too
    fireEvent.click(
      within(screen.getByRole("region", { name: "Field 2" })).getByRole("radio", {
        name: "Only when another field has certain values",
      }),
    );
    renders.clear();
    rename("Is this a GMO?", "GMO status");
    expect([...renders.keys()].sort()).toEqual([keyOf(2), keyOf(4), keyOf(5)]);
  });
});
