/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
/* eslint-disable no-undefined */
import React from "react";
import { render, cleanup } from "@testing-library/react";
import "@testing-library/jest-dom";
import ChoiceField from "../ChoiceField";
import Checkbox from "@mui/material/Checkbox";
import each from "jest-each";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../theme";

jest.mock("@mui/material/Checkbox", () => jest.fn(() => <div></div>));

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

const renderChoiceField = (props: {|
  disabled?: boolean,
  hideWhenDisabled?: boolean,
  value: Array<"foo" | "bar">,
|}) =>
  render(
    <ThemeProvider theme={materialTheme}>
      <ChoiceField
        onChange={() => {}}
        name="foo"
        options={[
          { label: "Foo", value: "foo", disabled: false, editing: false },
          { label: "Bar", value: "bar", disabled: false, editing: false },
        ]}
        {...props}
      />
    </ThemeProvider>
  );

const expectAllOptionsAreShown = () => {
  expect(Checkbox).toHaveBeenCalledTimes(2);
  expect(Checkbox).toHaveBeenCalledWith(
    expect.objectContaining({
      value: "foo",
    }),
    expect.anything()
  );
  expect(Checkbox).toHaveBeenCalledWith(
    expect.objectContaining({
      value: "bar",
    }),
    expect.anything()
  );
};

const expectNoOptions = () => {
  expect(Checkbox).not.toHaveBeenCalled();
};

const expectJustFoo = () => {
  expect(Checkbox).toHaveBeenCalledTimes(1);
  expect(Checkbox).toHaveBeenCalledWith(
    expect.objectContaining({
      value: "foo",
    }),
    expect.anything()
  );
};

describe("ChoiceField", () => {
  describe("Renders correctly", () => {
    each`
      disabled     | hideWhenDisabled | value      | expectFn
      ${true}      | ${true}          | ${[]}      | ${expectNoOptions}
      ${true}      | ${true}          | ${["foo"]} | ${expectJustFoo}
      ${true}      | ${false}         | ${[]}      | ${expectAllOptionsAreShown}
      ${true}      | ${false}         | ${["foo"]} | ${expectAllOptionsAreShown}
      ${true}      | ${undefined}     | ${[]}      | ${expectNoOptions}
      ${true}      | ${undefined}     | ${["foo"]} | ${expectJustFoo}
      ${false}     | ${true}          | ${[]}      | ${expectAllOptionsAreShown}
      ${false}     | ${true}          | ${["foo"]} | ${expectAllOptionsAreShown}
      ${false}     | ${false}         | ${[]}      | ${expectAllOptionsAreShown}
      ${false}     | ${false}         | ${["foo"]} | ${expectAllOptionsAreShown}
      ${false}     | ${undefined}     | ${[]}      | ${expectAllOptionsAreShown}
      ${false}     | ${undefined}     | ${["foo"]} | ${expectAllOptionsAreShown}
      ${undefined} | ${true}          | ${[]}      | ${expectAllOptionsAreShown}
      ${undefined} | ${true}          | ${["foo"]} | ${expectAllOptionsAreShown}
      ${undefined} | ${false}         | ${[]}      | ${expectAllOptionsAreShown}
      ${undefined} | ${false}         | ${["foo"]} | ${expectAllOptionsAreShown}
      ${undefined} | ${undefined}     | ${[]}      | ${expectAllOptionsAreShown}
      ${undefined} | ${undefined}     | ${["foo"]} | ${expectAllOptionsAreShown}
    `.test(
      "{disabled = $disabled, hideWhenDisabled = $hideWhenDisabled, value = $value}",
      ({
        disabled,
        hideWhenDisabled,
        value,
        expectFn,
      }: {|
        disabled: typeof undefined | boolean,
        hideWhenDisabled: typeof undefined | boolean,
        value: Array<"foo" | "bar">,
        expectFn: () => void,
      |}) => {
        renderChoiceField({ disabled, hideWhenDisabled, value });
        expectFn();
      }
    );
  });
});
