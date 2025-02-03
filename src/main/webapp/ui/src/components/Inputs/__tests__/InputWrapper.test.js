/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
/* eslint-disable no-undefined */
import React from "react";
import { render, cleanup, type Element } from "@testing-library/react";
import "@testing-library/jest-dom";
import each from "jest-each";
import InputWrapper from "../InputWrapper";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../theme";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

const expectText = (text: string) => (container: Element) => {
  expect(container).toHaveTextContent("Nothing here" + text);
};

const expectNothing = expectText("");
const expectHelpText = expectText("help");
const expectCounter = expectText("3 / 2");
const expectCountError = expectText("No more than 2 characters permitted.");

describe("InputWrapper", () => {
  describe("Renders correctly", () => {
    each`
      disabled     | maxLength    | error        | value        | helperText   | expectFn
      ${undefined} | ${undefined} | ${undefined} | ${undefined} | ${undefined} | ${expectNothing}
      ${undefined} | ${undefined} | ${undefined} | ${undefined} | ${null}      | ${expectNothing}
      ${undefined} | ${undefined} | ${undefined} | ${undefined} | ${"help"}    | ${expectNothing}
      ${undefined} | ${undefined} | ${undefined} | ${"foo"}     | ${undefined} | ${expectNothing}
      ${undefined} | ${undefined} | ${undefined} | ${"foo"}     | ${null}      | ${expectNothing}
      ${undefined} | ${undefined} | ${undefined} | ${"foo"}     | ${"help"}    | ${expectNothing}
      ${undefined} | ${undefined} | ${false}     | ${undefined} | ${undefined} | ${expectNothing}
      ${undefined} | ${undefined} | ${false}     | ${undefined} | ${null}      | ${expectNothing}
      ${undefined} | ${undefined} | ${false}     | ${undefined} | ${"help"}    | ${expectNothing}
      ${undefined} | ${undefined} | ${false}     | ${"foo"}     | ${undefined} | ${expectNothing}
      ${undefined} | ${undefined} | ${false}     | ${"foo"}     | ${null}      | ${expectNothing}
      ${undefined} | ${undefined} | ${false}     | ${"foo"}     | ${"help"}    | ${expectNothing}
      ${undefined} | ${undefined} | ${true}      | ${undefined} | ${undefined} | ${expectNothing}
      ${undefined} | ${undefined} | ${true}      | ${undefined} | ${null}      | ${expectNothing}
      ${undefined} | ${undefined} | ${true}      | ${undefined} | ${"help"}    | ${expectHelpText}
      ${undefined} | ${undefined} | ${true}      | ${"foo"}     | ${undefined} | ${expectNothing}
      ${undefined} | ${undefined} | ${true}      | ${"foo"}     | ${null}      | ${expectNothing}
      ${undefined} | ${undefined} | ${true}      | ${"foo"}     | ${"help"}    | ${expectHelpText}
      ${undefined} | ${2}         | ${undefined} | ${undefined} | ${undefined} | ${expectNothing}
      ${undefined} | ${2}         | ${undefined} | ${undefined} | ${null}      | ${expectNothing}
      ${undefined} | ${2}         | ${undefined} | ${undefined} | ${"help"}    | ${expectNothing}
      ${undefined} | ${2}         | ${undefined} | ${"foo"}     | ${undefined} | ${expectCounter}
      ${undefined} | ${2}         | ${undefined} | ${"foo"}     | ${null}      | ${expectCounter}
      ${undefined} | ${2}         | ${undefined} | ${"foo"}     | ${"help"}    | ${expectCounter}
      ${undefined} | ${2}         | ${false}     | ${undefined} | ${undefined} | ${expectNothing}
      ${undefined} | ${2}         | ${false}     | ${undefined} | ${null}      | ${expectNothing}
      ${undefined} | ${2}         | ${false}     | ${undefined} | ${"help"}    | ${expectNothing}
      ${undefined} | ${2}         | ${false}     | ${"foo"}     | ${undefined} | ${expectCounter}
      ${undefined} | ${2}         | ${false}     | ${"foo"}     | ${null}      | ${expectCounter}
      ${undefined} | ${2}         | ${false}     | ${"foo"}     | ${"help"}    | ${expectCounter}
      ${undefined} | ${2}         | ${true}      | ${undefined} | ${undefined} | ${expectNothing}
      ${undefined} | ${2}         | ${true}      | ${undefined} | ${null}      | ${expectNothing}
      ${undefined} | ${2}         | ${true}      | ${undefined} | ${"help"}    | ${expectHelpText}
      ${undefined} | ${2}         | ${true}      | ${"foo"}     | ${undefined} | ${expectCountError}
      ${undefined} | ${2}         | ${true}      | ${"foo"}     | ${null}      | ${expectCountError}
      ${undefined} | ${2}         | ${true}      | ${"foo"}     | ${"help"}    | ${expectHelpText}
      ${false}     | ${undefined} | ${undefined} | ${undefined} | ${undefined} | ${expectNothing}
      ${false}     | ${undefined} | ${undefined} | ${undefined} | ${null}      | ${expectNothing}
      ${false}     | ${undefined} | ${undefined} | ${undefined} | ${"help"}    | ${expectNothing}
      ${false}     | ${undefined} | ${undefined} | ${"foo"}     | ${undefined} | ${expectNothing}
      ${false}     | ${undefined} | ${undefined} | ${"foo"}     | ${null}      | ${expectNothing}
      ${false}     | ${undefined} | ${undefined} | ${"foo"}     | ${"help"}    | ${expectNothing}
      ${false}     | ${undefined} | ${false}     | ${undefined} | ${undefined} | ${expectNothing}
      ${false}     | ${undefined} | ${false}     | ${undefined} | ${null}      | ${expectNothing}
      ${false}     | ${undefined} | ${false}     | ${undefined} | ${"help"}    | ${expectNothing}
      ${false}     | ${undefined} | ${false}     | ${"foo"}     | ${undefined} | ${expectNothing}
      ${false}     | ${undefined} | ${false}     | ${"foo"}     | ${null}      | ${expectNothing}
      ${false}     | ${undefined} | ${false}     | ${"foo"}     | ${"help"}    | ${expectNothing}
      ${false}     | ${undefined} | ${true}      | ${undefined} | ${undefined} | ${expectNothing}
      ${false}     | ${undefined} | ${true}      | ${undefined} | ${null}      | ${expectNothing}
      ${false}     | ${undefined} | ${true}      | ${undefined} | ${"help"}    | ${expectHelpText}
      ${false}     | ${undefined} | ${true}      | ${"foo"}     | ${undefined} | ${expectNothing}
      ${false}     | ${undefined} | ${true}      | ${"foo"}     | ${null}      | ${expectNothing}
      ${false}     | ${undefined} | ${true}      | ${"foo"}     | ${"help"}    | ${expectHelpText}
      ${false}     | ${2}         | ${undefined} | ${undefined} | ${undefined} | ${expectNothing}
      ${false}     | ${2}         | ${undefined} | ${undefined} | ${null}      | ${expectNothing}
      ${false}     | ${2}         | ${undefined} | ${undefined} | ${"help"}    | ${expectNothing}
      ${false}     | ${2}         | ${undefined} | ${"foo"}     | ${undefined} | ${expectCounter}
      ${false}     | ${2}         | ${undefined} | ${"foo"}     | ${null}      | ${expectCounter}
      ${false}     | ${2}         | ${undefined} | ${"foo"}     | ${"help"}    | ${expectCounter}
      ${false}     | ${2}         | ${false}     | ${undefined} | ${undefined} | ${expectNothing}
      ${false}     | ${2}         | ${false}     | ${undefined} | ${null}      | ${expectNothing}
      ${false}     | ${2}         | ${false}     | ${undefined} | ${"help"}    | ${expectNothing}
      ${false}     | ${2}         | ${false}     | ${"foo"}     | ${undefined} | ${expectCounter}
      ${false}     | ${2}         | ${false}     | ${"foo"}     | ${null}      | ${expectCounter}
      ${false}     | ${2}         | ${false}     | ${"foo"}     | ${"help"}    | ${expectCounter}
      ${false}     | ${2}         | ${true}      | ${undefined} | ${undefined} | ${expectNothing}
      ${false}     | ${2}         | ${true}      | ${undefined} | ${null}      | ${expectNothing}
      ${false}     | ${2}         | ${true}      | ${undefined} | ${"help"}    | ${expectHelpText}
      ${false}     | ${2}         | ${true}      | ${"foo"}     | ${undefined} | ${expectCountError}
      ${false}     | ${2}         | ${true}      | ${"foo"}     | ${null}      | ${expectCountError}
      ${false}     | ${2}         | ${true}      | ${"foo"}     | ${"help"}    | ${expectHelpText}
      ${true}      | ${undefined} | ${undefined} | ${undefined} | ${undefined} | ${expectNothing}
      ${true}      | ${undefined} | ${undefined} | ${undefined} | ${null}      | ${expectNothing}
      ${true}      | ${undefined} | ${undefined} | ${undefined} | ${"help"}    | ${expectNothing}
      ${true}      | ${undefined} | ${undefined} | ${"foo"}     | ${undefined} | ${expectNothing}
      ${true}      | ${undefined} | ${undefined} | ${"foo"}     | ${null}      | ${expectNothing}
      ${true}      | ${undefined} | ${undefined} | ${"foo"}     | ${"help"}    | ${expectNothing}
      ${true}      | ${undefined} | ${false}     | ${undefined} | ${undefined} | ${expectNothing}
      ${true}      | ${undefined} | ${false}     | ${undefined} | ${null}      | ${expectNothing}
      ${true}      | ${undefined} | ${false}     | ${undefined} | ${"help"}    | ${expectNothing}
      ${true}      | ${undefined} | ${false}     | ${"foo"}     | ${undefined} | ${expectNothing}
      ${true}      | ${undefined} | ${false}     | ${"foo"}     | ${null}      | ${expectNothing}
      ${true}      | ${undefined} | ${false}     | ${"foo"}     | ${"help"}    | ${expectNothing}
      ${true}      | ${undefined} | ${true}      | ${undefined} | ${undefined} | ${expectNothing}
      ${true}      | ${undefined} | ${true}      | ${undefined} | ${null}      | ${expectNothing}
      ${true}      | ${undefined} | ${true}      | ${undefined} | ${"help"}    | ${expectHelpText}
      ${true}      | ${undefined} | ${true}      | ${"foo"}     | ${undefined} | ${expectNothing}
      ${true}      | ${undefined} | ${true}      | ${"foo"}     | ${null}      | ${expectNothing}
      ${true}      | ${undefined} | ${true}      | ${"foo"}     | ${"help"}    | ${expectHelpText}
      ${true}      | ${2}         | ${undefined} | ${undefined} | ${undefined} | ${expectNothing}
      ${true}      | ${2}         | ${undefined} | ${undefined} | ${null}      | ${expectNothing}
      ${true}      | ${2}         | ${undefined} | ${undefined} | ${"help"}    | ${expectNothing}
      ${true}      | ${2}         | ${undefined} | ${"foo"}     | ${undefined} | ${expectNothing}
      ${true}      | ${2}         | ${undefined} | ${"foo"}     | ${null}      | ${expectNothing}
      ${true}      | ${2}         | ${undefined} | ${"foo"}     | ${"help"}    | ${expectNothing}
      ${true}      | ${2}         | ${false}     | ${undefined} | ${undefined} | ${expectNothing}
      ${true}      | ${2}         | ${false}     | ${undefined} | ${null}      | ${expectNothing}
      ${true}      | ${2}         | ${false}     | ${undefined} | ${"help"}    | ${expectNothing}
      ${true}      | ${2}         | ${false}     | ${"foo"}     | ${undefined} | ${expectNothing}
      ${true}      | ${2}         | ${false}     | ${"foo"}     | ${null}      | ${expectNothing}
      ${true}      | ${2}         | ${false}     | ${"foo"}     | ${"help"}    | ${expectNothing}
      ${true}      | ${2}         | ${true}      | ${undefined} | ${undefined} | ${expectNothing}
      ${true}      | ${2}         | ${true}      | ${undefined} | ${null}      | ${expectNothing}
      ${true}      | ${2}         | ${true}      | ${undefined} | ${"help"}    | ${expectHelpText}
      ${true}      | ${2}         | ${true}      | ${"foo"}     | ${undefined} | ${expectNothing}
      ${true}      | ${2}         | ${true}      | ${"foo"}     | ${null}      | ${expectNothing}
      ${true}      | ${2}         | ${true}      | ${"foo"}     | ${"help"}    | ${expectHelpText}
    `.test(
      "{disabled = $disabled, maxLength = $maxLength, error = $error, value = $value, helperText = $helperText}",
      ({
        disabled,
        maxLength,
        error,
        value,
        helperText,
        expectFn,
      }: {|
        disabled?: boolean,
        maxLength?: number,
        error?: boolean,
        value?: string,
        helperText?: ?string,
        expectFn: (container: Element) => void,
      |}) => {
        const { container } = render(
          <ThemeProvider theme={materialTheme}>
            <InputWrapper
              disabled={disabled}
              maxLength={maxLength}
              error={error}
              value={value}
              helperText={helperText}
            >
              <div>Nothing here</div>
            </InputWrapper>
          </ThemeProvider>
        );
        expectFn(container);
      }
    );
  });
});
