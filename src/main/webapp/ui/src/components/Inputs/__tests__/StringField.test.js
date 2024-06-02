/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
/* eslint-disable no-undefined */
import React from "react";
import { render, cleanup } from "@testing-library/react";
import "@testing-library/jest-dom";
import StringField from "../StringField";
import TextField from "@mui/material/TextField";
import each from "jest-each";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../theme";

jest.mock("@mui/material/TextField", () => jest.fn(() => <div></div>));

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

const expectLabel = (text: string) => (container: Node) =>
  expect(container).toHaveTextContent(text);

const expectTextField = (value: string) => () =>
  expect(TextField).toHaveBeenCalledWith(
    expect.objectContaining({ value }),
    expect.anything()
  );

describe("StringField", () => {
  describe("Renders correctly", () => {
    each`
      disabled     | value    | noValueLabel | expectFn
      ${true}      | ${""}    | ${undefined} | ${expectLabel("None")}
      ${true}      | ${""}    | ${"foo"}     | ${expectLabel("foo")}
      ${true}      | ${"bar"} | ${undefined} | ${expectTextField("bar")}
      ${true}      | ${"bar"} | ${"foo"}     | ${expectTextField("bar")}
      ${false}     | ${""}    | ${undefined} | ${expectTextField("")}
      ${false}     | ${""}    | ${"foo"}     | ${expectTextField("")}
      ${false}     | ${"bar"} | ${undefined} | ${expectTextField("bar")}
      ${false}     | ${"bar"} | ${"foo"}     | ${expectTextField("bar")}
      ${undefined} | ${""}    | ${undefined} | ${expectTextField("")}
      ${undefined} | ${""}    | ${"foo"}     | ${expectTextField("")}
      ${undefined} | ${"bar"} | ${undefined} | ${expectTextField("bar")}
      ${undefined} | ${"bar"} | ${"foo"}     | ${expectTextField("bar")}
    `.test(
      '{disabled = $disabled, value = "$value", noValueLabel = $noValueLabel}',
      ({
        disabled,
        value,
        noValueLabel,
        expectFn,
      }: {|
        disabled: typeof undefined | boolean,
        value: string,
        noValueLabel: typeof undefined | string,
        expectFn: (container: Node) => void,
      |}) => {
        const { container } = render(
          <ThemeProvider theme={materialTheme}>
            <StringField
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
