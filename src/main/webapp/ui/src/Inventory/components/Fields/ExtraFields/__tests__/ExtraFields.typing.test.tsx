import { ThemeProvider } from "@mui/material/styles";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@/__tests__/customQueries";
import materialTheme from "../../../../../theme";

vi.mock("@/common/InvApiService", () => ({ default: {} }));
// cut the RootStore -> SearchStore -> TemplateModel module cycle, which is not
// exercised by this list-level test
vi.mock("@/stores/stores/getRootStore", () => ({
  default: () => ({
    unitStore: { getUnit: () => ({ label: "ml" }) },
  }),
}));
vi.mock("../../Link/LinkTargetBrowser", () => ({ default: () => null }));
vi.mock("../../Link/ElnRecordPicker", () => ({ default: () => null }));
vi.mock("../../Link/linkTargetExists", () => ({
  checkLinkTargetExists: vi.fn(() => Promise.resolve(true)),
}));

// SampleModel must load before the model -> RootStore import chain, otherwise
// the TemplateModel/SampleModel module cycle hits an uninitialised
// SAMPLE_FIELDS during module evaluation
import { makeMockSample } from "../../../../../stores/models/__tests__/SampleModel/mocking";
import ExtraFields from "../ExtraFields";

/**
 * Regression test: while a brand-new extra field is being created, UpdateField
 * live-syncs the typed name into the model so the record-level Save validates
 * it. The list row must NOT be keyed by the (now mutating) name, or every
 * keystroke remounts the editor and the in-flight keystrokes are dropped:
 * typing "name" left just "n" in the field.
 */
describe("ExtraFields new-field name typing", () => {
  it("keeps every keystroke while typing the name of a brand-new field", async () => {
    const sample = makeMockSample();
    // mirrors NewField's "Add new field" button
    sample.addExtraField({
      id: null,
      globalId: null,
      name: "",
      type: "text",
      content: "",
      parentGlobalId: sample.globalId,
      editing: true,
      initial: true,
    } as Parameters<typeof sample.addExtraField>[0]);
    const user = userEvent.setup();
    render(
      <ThemeProvider theme={materialTheme}>
        <ExtraFields onErrorStateChange={vi.fn()} result={sample} />
      </ThemeProvider>,
    );

    const nameField = () => screen.getByRole("textbox", { name: "inventory:fields.extraFields.fields.fieldName" });
    await user.click(nameField());
    await user.keyboard("name");

    expect(nameField()).toHaveValue("name");
    // mid-edit values live only in the editor; the model is untouched and
    // reports itself invalid, so the record-level Save is greyed out instead
    // of silently saving a half-finished field
    expect(sample.extraFields[0].name).toBe("");
    expect(sample.extraFields[0].isValid.isOk).toBe(false);
    // and the user can carry on to Apply the new field
    const applyButton = screen.getByRole("button", { name: "inventory:fields.extraFields.updateField.updateLabel" });
    expect(applyButton).toBeEnabled();

    await user.click(applyButton);

    // Apply commits the staged values and unblocks the record-level Save
    expect(sample.extraFields[0].name).toBe("name");
    expect(sample.extraFields[0].isValid.isOk).toBe(true);
  });
});
