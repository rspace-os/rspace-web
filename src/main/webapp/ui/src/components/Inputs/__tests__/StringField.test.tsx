import { ThemeProvider } from "@mui/material/styles";
import TextField from "@mui/material/TextField";
import { render } from "@testing-library/react";
import type React from "react";
import { describe, expect, test, vi } from "vitest";
import materialTheme from "../../../theme";
import StringField from "../StringField";

vi.mock("@mui/material/TextField", () => ({
  default: vi.fn(() => <div></div>),
}));
const expectLabel = (text: string) => (container: Node) => expect(container).toHaveTextContent(text);
const expectTextField = (value: string) => () =>
  expect(TextField).toHaveBeenCalledWith(expect.objectContaining({ value }), undefined);
describe("StringField", () => {
  describe("Renders correctly", () => {
    test.each`
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
    `(
      '{disabled = $disabled, value = "$value", noValueLabel = $noValueLabel}',
      ({
        disabled,
        value,
        noValueLabel,
        expectFn,
      }: {
        disabled: typeof undefined | boolean;
        value: string;
        noValueLabel: typeof undefined | string;
        expectFn: (container: Element) => void;
      }) => {
        const { container } = render(
          <ThemeProvider theme={materialTheme}>
            <StringField disabled={disabled} value={value} noValueLabel={noValueLabel} />
          </ThemeProvider>,
        );
        expectFn(container);
      },
    );
  });

  test("passes slotProps through to the underlying TextField", () => {
    render(
      <ThemeProvider theme={materialTheme}>
        <StringField
          value="foo"
          slotProps={{
            input: {
              endAdornment: <span>suffix</span>,
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
      };
    };

    expect(textFieldProps.slotProps?.input?.endAdornment).toBeTruthy();
  });
});
