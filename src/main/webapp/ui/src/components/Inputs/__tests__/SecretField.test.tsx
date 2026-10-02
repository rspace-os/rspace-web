import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, test } from "vitest";
import SecretField, { type Secret } from "../SecretField";

function Harness({ initial, clearable }: { initial: Secret; clearable?: boolean }) {
  const [value, setValue] = useState<Secret>(initial);
  return (
    <>
      <SecretField label="Secret" value={value} onChange={setValue} clearable={clearable} />
      {/* JSON so that null (stored) and "" (none) can be told apart */}
      <output>{JSON.stringify(value)}</output>
    </>
  );
}

describe("SecretField", () => {
  test("masks a secret being entered, with a toggle to reveal it", async () => {
    const user = userEvent.setup();
    render(<Harness initial="" />);
    const input = screen.getByLabelText("Secret");
    expect(input).toHaveAttribute("type", "password");

    await user.type(input, "abc");
    await user.click(screen.getByRole("button", { name: "common:inputs.secretField.show" }));
    expect(input).toHaveAttribute("type", "text");
    expect(input).toHaveValue("abc");

    await user.click(screen.getByRole("button", { name: "common:inputs.secretField.hide" }));
    expect(input).toHaveAttribute("type", "password");
  });

  test("renders a stored secret as an empty, unchanged field that cannot be revealed", () => {
    render(<Harness initial={null} />);
    const input = screen.getByLabelText("Secret");
    expect(input).toHaveValue("");
    expect(input).toHaveAttribute("placeholder", "common:inputs.secretField.unchanged");
    expect(screen.getByRole("button", { name: "common:inputs.secretField.show" })).toBeDisabled();
  });

  test("returns a stored secret to unchanged when the field is emptied", async () => {
    const user = userEvent.setup();
    render(<Harness initial={null} />);
    const input = screen.getByLabelText("Secret");

    await user.type(input, "new");
    expect(screen.getByRole("status")).toHaveTextContent("new");

    await user.clear(input);
    expect(screen.getByRole("status")).toHaveTextContent("null");
    expect(input).toHaveAttribute("placeholder", "common:inputs.secretField.unchanged");
  });

  test("clears a stored secret only through the clear button", async () => {
    const user = userEvent.setup();
    render(<Harness initial={null} />);
    const input = screen.getByLabelText("Secret");

    await user.click(screen.getByRole("button", { name: "common:inputs.secretField.clear" }));
    expect(screen.getByRole("status")).toHaveTextContent('""');
    expect(input).not.toHaveAttribute("placeholder", "common:inputs.secretField.unchanged");
    expect(screen.queryByRole("button", { name: "common:inputs.secretField.clear" })).not.toBeInTheDocument();

    // once cleared, emptying the field keeps it cleared
    await user.type(input, "x");
    await user.clear(input);
    expect(screen.getByRole("status")).toHaveTextContent('""');
  });

  test("offers no clear button for a secret that was never stored", () => {
    render(<Harness initial="" />);
    expect(screen.queryByRole("button", { name: "common:inputs.secretField.clear" })).not.toBeInTheDocument();
  });

  test("offers no clear button when the secret is not clearable", () => {
    render(<Harness initial={null} clearable={false} />);
    expect(screen.queryByRole("button", { name: "common:inputs.secretField.clear" })).not.toBeInTheDocument();
  });

  test("shows a read-only secret that can be revealed but not edited or cleared", async () => {
    const user = userEvent.setup();
    render(<SecretField label="Secret" value="generated-secret" readOnly />);
    const input = screen.getByLabelText("Secret");
    expect(input).toHaveAttribute("readonly");
    expect(screen.queryByRole("button", { name: "common:inputs.secretField.clear" })).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "common:inputs.secretField.show" }));
    expect(input).toHaveAttribute("type", "text");
    expect(input).toHaveValue("generated-secret");
  });

  test("has no axe violations with a stored secret", async () => {
    const { container } = render(<Harness initial={null} />);

    // @ts-expect-error toBeAccessible is from @sa11y/vitest
    await expect(container).toBeAccessible();
  });
});
