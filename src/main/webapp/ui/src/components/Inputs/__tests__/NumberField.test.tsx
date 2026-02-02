 
import {
  describe,
  expect,
  beforeEach,
  test,
  vi,
} from "vitest";
import React from "react";
import {
  render,
} from "@testing-library/react";
import NumberField from "../NumberField";
import TextField from "@mui/material/TextField";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../theme";

vi.mock("@mui/material/TextField", () => ({
  default: vi.fn(() => <div></div>),
}));




const expectLabel = (text: string) => (container: Element) =>
  expect(container).toHaveTextContent(text);

const expectTextField = (value: string | number | null) => () =>
  expect(TextField).toHaveBeenCalledWith(
    expect.objectContaining({ value }),
    expect.anything()
  );

describe("NumberField", () => {
  describe("Renders correctly", () => {
    test.each`
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
    `(
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

