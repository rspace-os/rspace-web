import { describe, expect, test, vi } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import { ThemeProvider } from "@mui/material/styles";
import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import FormSectionsContext from "../../../../../stores/contexts/FormSections";
import { makeMockSample } from "../../../../../stores/models/__tests__/SampleModel/mocking";
import type { FieldModelAttrs } from "../../../../../stores/models/FieldModel";
import type SampleModel from "../../../../../stores/models/SampleModel";
import materialTheme from "../../../../../theme";
import {
  StepperPanelHeader,
  setFormSectionError,
  useFormSectionError,
} from "../../../../components/Stepper/StepperPanelHeader";
import Fields from "../Fields";

const emptyMandatoryRadio: FieldModelAttrs = {
  attachment: null,
  columnIndex: 1,
  content: null,
  definition: { options: ["Option A", "Option B"] },
  globalId: "SF20",
  id: 20,
  mandatory: true,
  name: "Required choice",
  selectedOptions: [],
  type: "radio",
};

function CollapsedSection({ sample }: { sample: SampleModel }) {
  const formSectionError = useFormSectionError({ editing: true, globalId: sample.globalId });
  return (
    <ThemeProvider theme={materialTheme}>
      <FormSectionsContext.Provider
        value={{ isExpanded: () => false, setExpanded: () => {}, setAllExpanded: () => {} }}
      >
        <StepperPanelHeader
          onToggle={() => {}}
          open={false}
          title="Custom Fields"
          formSectionError={formSectionError}
          id="customFields"
          recordType="sample"
        />
        <Fields
          onErrorStateChange={(field, value) => setFormSectionError(formSectionError, field, value)}
          sample={sample}
        />
      </FormSectionsContext.Provider>
    </ThemeProvider>
  );
}

vi.mock("../../../../../components/Ketcher/KetcherDialog", () => ({
  default: vi.fn(() => <div></div>),
}));
describe("Fields", () => {
  describe("Empty mandatory fields are reported without any input.", () => {
    test("Reports the error on mount", () => {
      const activeResult = makeMockSample({ fields: [emptyMandatoryRadio] });
      activeResult.setEditable(new Set(["fields"]), true);
      const onErrorStateChange = vi.fn();
      render(
        <ThemeProvider theme={materialTheme}>
          <Fields onErrorStateChange={onErrorStateChange} sample={activeResult} />
        </ThemeProvider>,
      );
      expect(onErrorStateChange).toHaveBeenCalledWith("template_Required choice", true);
    });
    test("A collapsed section shows a badge when fields arrive after mount", () => {
      const activeResult = makeMockSample();
      activeResult.setEditable(new Set(["fields"]), true);
      render(<CollapsedSection sample={activeResult} />);
      expect(screen.getByRole("button", { name: "inventory:formSections.expandSection" })).not.toHaveTextContent("1");
      act(() => {
        activeResult.overrideFields([emptyMandatoryRadio]);
      });
      expect(screen.getByRole("button", { name: "inventory:formSections.expandSection" })).toHaveTextContent("1");
    });
  });
  describe("Sample with number field behaves correctly.", () => {
    test("Checks validity of input", async () => {
      const user = userEvent.setup();
      const INITIAL_VALUE = "2";
      const activeResult = makeMockSample({
        fields: [
          {
            attachment: null,
            columnIndex: 1,
            content: INITIAL_VALUE,
            definition: null,
            globalId: "SF19",
            id: 19,
            mandatory: false,
            name: "MyNumber",
            selectedOptions: null,
            type: "number",
          },
        ],
      });
      activeResult.setFieldEditable("fields", true);
      vi.spyOn(activeResult, "setAttributesDirty").mockImplementation(() => {});
      const { container } = render(
        <ThemeProvider theme={materialTheme}>
          <Fields onErrorStateChange={() => {}} sample={activeResult} />
        </ThemeProvider>,
      );
      const input = screen.getByDisplayValue<HTMLInputElement>(INITIAL_VALUE);
      vi.spyOn(input, "checkValidity").mockReturnValue(false);
      await user.clear(input);
      expect(container).toMatchTextContent("Invalid value. Please enter a valid value.");
    });
    test("Passed step='any' to input", () => {
      const INITIAL_VALUE = "2";
      const activeResult = makeMockSample({
        fields: [
          {
            attachment: null,
            columnIndex: 1,
            content: INITIAL_VALUE,
            definition: null,
            globalId: "SF19",
            id: 19,
            mandatory: false,
            name: "MyNumber",
            selectedOptions: null,
            type: "number",
          },
        ],
      });
      vi.spyOn(activeResult, "setAttributesDirty").mockImplementation(() => {});
      render(
        <ThemeProvider theme={materialTheme}>
          <Fields onErrorStateChange={() => {}} sample={activeResult} />
        </ThemeProvider>,
      );
      const input = screen.getByDisplayValue(INITIAL_VALUE);
      expect(input).toHaveAttribute("step", "any");
    });
  });
});
