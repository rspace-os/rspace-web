import { describe, expect, test, vi } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { makeMockSample } from "../../../../../stores/models/__tests__/SampleModel/mocking";
import materialTheme from "../../../../../theme";
import Fields from "../Fields";

vi.mock("../../../../../components/Ketcher/KetcherDialog", () => ({
  default: vi.fn(() => <div></div>),
}));
describe("Fields", () => {
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
