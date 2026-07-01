import { ThemeProvider } from "@mui/material/styles";
import TextField from "@mui/material/TextField";
import { render } from "@testing-library/react";
import type React from "react";
import { describe, expect, test, vi } from "vitest";
import materialTheme from "../../../theme";
import NumberField from "../NumberField";

vi.mock("@mui/material/TextField", () => ({
  default: vi.fn(() => <div></div>),
}));
const expectLabel = (text: string) => (container: Element) => expect(container).toHaveTextContent(text);
const expectTextField = (value: string | number | null) => () =>
  expect(TextField).toHaveBeenCalledWith(expect.objectContaining({ value }), undefined);
describe("NumberField", () => {
  describe("Renders correctly", () => {
    test.each`
      disabled     | value | noValueLabel | expectFn
      ${true}      | ${0}  | ${undefined} | ${expectTextField(0)}
      ${true}      | ${0}  | ${"foo"}     | ${expectTextField(0)}
      ${true}      | ${""} | ${undefined} | ${expectLabel("None")}
      ${true}      | ${""} | ${"foo"}     | ${expectLabel("foo")}
      ${false}     | ${0}  | ${undefined} | ${expectTextField(0)}
      ${false}     | ${0}  | ${"foo"}     | ${expectTextField(0)}
      ${false}     | ${""} | ${undefined} | ${expectTextField("")}
      ${false}     | ${""} | ${"foo"}     | ${expectTextField("")}
      ${undefined} | ${0}  | ${undefined} | ${expectTextField(0)}
      ${undefined} | ${0}  | ${"foo"}     | ${expectTextField(0)}
      ${undefined} | ${""} | ${undefined} | ${expectTextField("")}
      ${undefined} | ${""} | ${"foo"}     | ${expectTextField("")}
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
            <NumberField disabled={disabled} value={value} noValueLabel={noValueLabel} />
          </ThemeProvider>,
        );
        expectFn(container);
      },
    );
  });

  test("passes slotProps through to the underlying TextField", () => {
    render(
      <ThemeProvider theme={materialTheme}>
        <NumberField
          value={1}
          slotProps={{
            input: {
              endAdornment: <span>{"kg"}</span>,
            },
            htmlInput: {
              min: 0,
            },
          }}
        />
      </ThemeProvider>,
    );

    const textFieldProps = vi.mocked(TextField).mock.lastCall?.[0] as {
      slotProps?: {
        input?: {
          endAdornment?: React.ReactNode;
        };
        htmlInput?: {
          min?: number;
          inputMode?: string;
          lang?: string;
        };
      };
    };

    expect(textFieldProps.slotProps?.input?.endAdornment).toBeTruthy();
    expect(textFieldProps.slotProps?.htmlInput).toEqual(
      expect.objectContaining({
        min: 0,
        inputMode: "text",
        lang: "en",
      }),
    );
  });
});
