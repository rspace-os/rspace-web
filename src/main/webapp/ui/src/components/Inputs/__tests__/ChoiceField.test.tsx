import Checkbox from "@mui/material/Checkbox";
import { ThemeProvider } from "@mui/material/styles";
import { render } from "@testing-library/react";
import { afterEach, describe, expect, test, vi } from "vitest";
import materialTheme from "../../../theme";
import ChoiceField from "../ChoiceField";

vi.mock("@mui/material/Checkbox", () => ({
  default: vi.fn(() => <div></div>),
}));
const renderChoiceField = (props: { disabled?: boolean; hideWhenDisabled?: boolean; value: Array<"foo" | "bar"> }) =>
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
    </ThemeProvider>,
  );
const expectAllOptionsAreShown = () => {
  expect(Checkbox).toHaveBeenCalledTimes(2);
  expect(Checkbox).toHaveBeenCalledWith(
    expect.objectContaining({
      value: "foo",
    }),
    undefined,
  );
  expect(Checkbox).toHaveBeenCalledWith(
    expect.objectContaining({
      value: "bar",
    }),
    undefined,
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
    undefined,
  );
};
describe("ChoiceField", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });
  describe("Renders correctly", () => {
    test.each`
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
        value: Array<"foo" | "bar">;
        expectFn: () => void;
      }) => {
        renderChoiceField({ disabled, hideWhenDisabled, value });
        expectFn();
      },
    );
  });
});
