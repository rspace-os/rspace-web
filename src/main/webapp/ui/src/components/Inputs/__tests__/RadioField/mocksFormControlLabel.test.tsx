 
import {
  describe,
  expect,
  beforeEach,
  it,
  vi,
} from "vitest";
import React from "react";
import {
  render,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import RadioField from "../../RadioField";
import FormControlLabel from "@mui/material/FormControlLabel";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";

vi.mock("@mui/material/FormControlLabel", () => ({
  default: vi.fn(() => <div></div>),
}));

beforeEach(() => {
  vi.clearAllMocks();
});


const renderRadioField = (props: {
  disabled?: boolean;
  hideWhenDisabled?: boolean;
  value: "foo" | "bar" | null;
}) =>
  render(
    <ThemeProvider theme={materialTheme}>
      <RadioField
        onChange={() => {}}
        name="foo"
        options={[
          { label: "Foo", value: "foo", editing: false },
          { label: "Bar", value: "bar", editing: false },
        ]}
        {...props}
      />
    </ThemeProvider>
  );

const expectAllOptionsAreShown = () => {
  expect(FormControlLabel).toHaveBeenCalledTimes(2);
  expect(FormControlLabel).toHaveBeenCalledWith(
    expect.objectContaining({
      value: "foo",
    }),
    expect.anything()
  );
  expect(FormControlLabel).toHaveBeenCalledWith(
    expect.objectContaining({
      value: "bar",
    }),
    expect.anything()
  );
};

const expectNoOptions = () => {
  expect(FormControlLabel).not.toHaveBeenCalled();
};

const expectJustFoo = () => {
  expect(FormControlLabel).toHaveBeenCalledTimes(1);
  expect(FormControlLabel).toHaveBeenCalledWith(
    expect.objectContaining({
      value: "foo",
    }),
    expect.anything()
  );
};

describe("RadioField", () => {
  describe("Renders correctly", () => {
    it.each`
      disabled     | hideWhenDisabled | value      | expectFn
      ${true}      | ${true}          | ${null}    | ${expectNoOptions}
      ${true}      | ${true}          | ${"foo"}   | ${expectJustFoo}
      ${true}      | ${false}         | ${null}    | ${expectAllOptionsAreShown}
      ${true}      | ${false}         | ${"foo"}   | ${expectAllOptionsAreShown}
      ${true}      | ${undefined}     | ${null}    | ${expectNoOptions}
      ${true}      | ${undefined}     | ${"foo"}   | ${expectJustFoo}
      ${false}     | ${true}          | ${null}    | ${expectAllOptionsAreShown}
      ${false}     | ${true}          | ${"foo"}   | ${expectAllOptionsAreShown}
      ${false}     | ${false}         | ${null}    | ${expectAllOptionsAreShown}
      ${false}     | ${false}         | ${"foo"}   | ${expectAllOptionsAreShown}
      ${false}     | ${undefined}     | ${null}    | ${expectAllOptionsAreShown}
      ${false}     | ${undefined}     | ${"foo"}   | ${expectAllOptionsAreShown}
      ${undefined} | ${true}          | ${null}    | ${expectAllOptionsAreShown}
      ${undefined} | ${true}          | ${"foo"}   | ${expectAllOptionsAreShown}
      ${undefined} | ${false}         | ${null}    | ${expectAllOptionsAreShown}
      ${undefined} | ${false}         | ${"foo"}   | ${expectAllOptionsAreShown}
      ${undefined} | ${undefined}     | ${null}    | ${expectAllOptionsAreShown}
      ${undefined} | ${undefined}     | ${["foo"]} | ${expectAllOptionsAreShown}
    `(
      "{disabled = $disabled, hideWhenDisabled = $hideWhenDisabled, value = $value}",
      ({
        disabled,
        hideWhenDisabled,
        value,
        expectFn,
      }: {
        disabled: typeof undefined | boolean;
        hideWhenDisabled: typeof undefined | boolean;
        value: "foo" | "bar" | null;
        expectFn: () => void;
      }) => {
        renderRadioField({ disabled, hideWhenDisabled, value });
        expectFn();
      }
    );
  });
});

