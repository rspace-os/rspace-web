/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
/* eslint-disable no-undefined */
import React from "react";
import { render, cleanup } from "@testing-library/react";
import "@testing-library/jest-dom";
import NumberField from "../NumberField";
import TextField from "@mui/material/TextField";
import each from "jest-each";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../theme";

jest.mock("@mui/material/TextField", () => jest.fn(() => <div></div>));

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

const expectLabel = (text: string) => (container: Element) =>
  expect(container).toHaveTextContent(text);

const expectTextField = (value: string | number | null) => () =>
  expect(TextField).toHaveBeenCalledWith(
    expect.objectContaining({ value }),
    expect.anything()
  );

describe("NumberField", () => {
  describe("Renders correctly", () => {
    each`
      disabled     | value   | noValueLabel | expectFn
      ${true}      | ${0}    | ${undefined} | ${expectTextField(0)}
      ${true}      | ${0}    | ${"foo"}     | ${expectTextField(0)}
      ${true}      | ${""}   | ${undefined} | ${expectLabel("None")}
      ${true}      | ${""}   | ${"foo"}     | ${expectLabel("foo")}
      ${false}     | ${0}    | ${undefined} | ${expectTextField(0)}
      ${false}     | ${0}    | ${"foo"}     | ${expectTextField(0)}
      ${false}     | ${""}   | ${undefined} | ${expectTextField("")}
      ${false}     | ${""}   | ${"foo"}     | ${expectTextField("")}
      ${undefined} | ${0}    | ${undefined} | ${expectTextField(0)}
      ${undefined} | ${0}    | ${"foo"}     | ${expectTextField(0)}
      ${undefined} | ${""}   | ${undefined} | ${expectTextField("")}
      ${undefined} | ${""}   | ${"foo"}     | ${expectTextField("")}
    `.test(
      "{disabled = $disabled, value = $value, noValueLabel = $noValueLabel}",
      ({
        disabled,
        value,
        noValueLabel,
        expectFn,
      }: {
        disabled: typeof undefined | boolean;
        value: string | number;
        noValueLabel: typeof undefined | string;
        expectFn: (container: Element) => void;
      }) => {
        const { container } = render(
          <ThemeProvider theme={materialTheme}>
            <NumberField
              disabled={disabled}
              value={value}
              noValueLabel={noValueLabel}
            />
          </ThemeProvider>
        );
        expectFn(container);
      }
    );
  });
});
