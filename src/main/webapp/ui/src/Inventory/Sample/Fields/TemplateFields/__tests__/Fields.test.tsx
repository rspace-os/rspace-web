import { describe, expect, test, vi } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import { ThemeProvider } from "@mui/material/styles";
import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { makeMockSample } from "../../../../../stores/models/__tests__/SampleModel/mocking";
import materialTheme from "../../../../../theme";
import Fields from "../Fields";

vi.mock("../../../../../components/Ketcher/KetcherDialog", () => ({
  default: vi.fn(() => <div></div>),
}));
describe("Fields", () => {
  describe("Sample with number field behaves correctly.", () => {
    test("Checks validity of input", () => {
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
      const { container } = render(
        <ThemeProvider theme={materialTheme}>
          <Fields onErrorStateChange={() => {}} sample={activeResult} />
        </ThemeProvider>,
      );
      const input = screen.getByDisplayValue(INITIAL_VALUE);
      fireEvent.input(input, {
        target: {
          checkValidity: () => false,
        },
      });
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

  test("Sample with time field accepts a valid time", async () => {
    const user = userEvent.setup();
    const sample = makeMockSample({
      fields: [
        {
          attachment: null,
          columnIndex: 1,
          content: "",
          definition: null,
          globalId: "SF20",
          id: 20,
          mandatory: false,
          name: "Start time",
          selectedOptions: null,
          type: "time",
        },
      ],
    });
    sample.currentlyEditableFields.add("fields");
    render(
      <ThemeProvider theme={materialTheme}>
        <Fields onErrorStateChange={() => {}} sample={sample} />
      </ThemeProvider>,
    );

    await user.click(screen.getByRole("spinbutton", { name: "Hours" }));
    await user.keyboard("0930");

    expect(sample.fields[0].content).toBe("09:30");
    expect(sample.fields[0].validate().isOk).toBe(true);
  });
});
