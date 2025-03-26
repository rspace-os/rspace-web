/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "../../../../../../__mocks__/matchMedia";
import React from "react";
import { render, cleanup, fireEvent, screen } from "@testing-library/react";
import "@testing-library/jest-dom";
import Fields from "../Fields";
import { makeMockSample } from "../../../../../stores/models/__tests__/SampleModel/mocking";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../../theme";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

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
      jest
        .spyOn(activeResult, "setAttributesDirty")
        .mockImplementation(() => {});

      const { container } = render(
        <ThemeProvider theme={materialTheme}>
          <Fields onErrorStateChange={() => {}} sample={activeResult} />
        </ThemeProvider>
      );

      const input = screen.getByDisplayValue(INITIAL_VALUE);
      fireEvent.input(input, {
        target: {
          checkValidity: () => false,
        },
      });
      expect(container).toHaveTextContent(
        "Invalid value. Please enter a valid value."
      );
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
      jest
        .spyOn(activeResult, "setAttributesDirty")
        .mockImplementation(() => {});

      render(
        <ThemeProvider theme={materialTheme}>
          <Fields onErrorStateChange={() => {}} sample={activeResult} />
        </ThemeProvider>
      );

      const input = screen.getByDisplayValue(INITIAL_VALUE);
      expect(input).toHaveAttribute("step", "any");
    });
  });
});
