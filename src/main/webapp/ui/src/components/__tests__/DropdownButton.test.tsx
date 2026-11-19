import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, test, vi } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import materialTheme from "../../theme";
import DropdownButton from "../DropdownButton";

const renderButton = (props: { disabled?: boolean; disabledReason?: string }) => {
  const onClick = vi.fn();
  const { container } = render(
    <ThemeProvider theme={materialTheme}>
      <DropdownButton name="Bench" onClick={onClick} {...props}>
        {null}
      </DropdownButton>
    </ThemeProvider>,
  );
  return { onClick, container, button: screen.getByRole("button", { name: "Bench" }) };
};

describe("DropdownButton", () => {
  test("a button disabled with a reason stays focusable and is described by the reason", async () => {
    const user = userEvent.setup();
    const { button, container } = renderButton({ disabled: true, disabledReason: "Remove the structure first." });

    await user.tab();

    expect(button).toHaveFocus();
    expect(button).toHaveAttribute("aria-disabled", "true");
    expect(button).toHaveAccessibleDescription("Remove the structure first.");
    await expectAccessible(container);
  });

  test("a button disabled with a reason ignores clicks and keys", async () => {
    const user = userEvent.setup();
    const { button, onClick } = renderButton({ disabled: true, disabledReason: "Remove the structure first." });

    await user.click(button);
    button.focus();
    await user.keyboard("{Enter}");
    await user.keyboard(" ");

    expect(onClick).not.toHaveBeenCalled();
  });

  test("a button disabled without a reason is natively disabled", () => {
    const { button } = renderButton({ disabled: true });

    expect(button).toBeDisabled();
  });

  test("an enabled button ignores its reason", async () => {
    const user = userEvent.setup();
    const { button, onClick } = renderButton({ disabled: false, disabledReason: "Remove the structure first." });

    await user.click(button);

    expect(onClick).toHaveBeenCalled();
    expect(button).not.toHaveAttribute("aria-disabled");
    expect(button).not.toHaveAccessibleDescription();
  });
});
