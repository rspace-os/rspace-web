/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen, fireEvent } from "@testing-library/react";
import "@testing-library/jest-dom";
import RadioField from "../../RadioField";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("RadioField", () => {
  test("When a selection is made, the onChange handler is called", () => {
    const onChange = jest.fn<
      [{ target: { value: ?("firstOption" | "secondOption"), name: string } }],
      void
    >();
    render(
      <ThemeProvider theme={materialTheme}>
        <RadioField
          onChange={onChange}
          name="fieldName"
          options={[
            {
              label: "FirstOption",
              value: "firstOption",
              disabled: false,
              editing: false,
            },
            {
              label: "SecondOption",
              value: "secondOption",
              disabled: false,
              editing: false,
            },
          ]}
          value={null}
        />
      </ThemeProvider>
    );
    fireEvent.click(screen.getByRole("radio", { name: "FirstOption" }));
    expect(onChange).toHaveBeenLastCalledWith(
      expect.objectContaining({
        target: expect.objectContaining({
          name: "fieldName",
          value: "firstOption",
        }),
      })
    );
    expect(onChange).toHaveBeenCalledTimes(1);
  });
});
