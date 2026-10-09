import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, test } from "vitest";
import i18n from "@/modules/common/i18n";
import type { Secret } from "@/util/secret";
import SecretField, { secretAfterSave } from "../SecretField";

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

function SavedHarness() {
  const [value, setValue] = useState<Secret>(null);
  const [storedSecretExists, setStoredSecretExists] = useState(true);
  return (
    <>
      <SecretField label="Secret" value={value} onChange={setValue} storedSecretExists={storedSecretExists} />
      <button
        type="button"
        aria-label="Save secret"
        onClick={() => {
          setStoredSecretExists(value !== "");
          setValue(secretAfterSave(value));
        }}
      />
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
    expect(input).toHaveFocus();
    expect(screen.getByRole("status")).toHaveTextContent('""');
    expect(input).not.toHaveAttribute("placeholder", "common:inputs.secretField.unchanged");
    expect(screen.queryByRole("button", { name: "common:inputs.secretField.clear" })).not.toBeInTheDocument();

    // once cleared, emptying the field keeps it cleared
    await user.type(input, "x");
    await user.clear(input);
    expect(screen.getByRole("status")).toHaveTextContent('""');
  });

  test("undoes a clear, restoring the stored secret", async () => {
    const user = userEvent.setup();
    render(<Harness initial={null} />);

    await user.click(screen.getByRole("button", { name: "common:inputs.secretField.clear" }));
    await user.click(screen.getByRole("button", { name: "common:inputs.secretField.undoClear" }));
    expect(screen.getByRole("status")).toHaveTextContent("null");
    expect(screen.getByLabelText("Secret")).toHaveAttribute("placeholder", "common:inputs.secretField.unchanged");
  });

  test("does not offer to undo a clear after it has been saved", async () => {
    const user = userEvent.setup();
    render(<SavedHarness />);

    await user.click(screen.getByRole("button", { name: "common:inputs.secretField.clear" }));
    await user.click(screen.getByRole("button", { name: "Save secret" }));

    expect(screen.getByRole("status")).toHaveTextContent('""');
    expect(screen.queryByRole("button", { name: "common:inputs.secretField.undoClear" })).not.toBeInTheDocument();

    await user.type(screen.getByLabelText("Secret"), "new-secret");
    await user.clear(screen.getByLabelText("Secret"));
    expect(screen.getByRole("status")).toHaveTextContent('""');
  });

  test("resets clear after replacing a stored secret and saving", async () => {
    const user = userEvent.setup();
    render(<SavedHarness />);

    await user.click(screen.getByRole("button", { name: "common:inputs.secretField.clear" }));
    await user.type(screen.getByLabelText("Secret"), "replacement");
    await user.click(screen.getByRole("button", { name: "Save secret" }));

    expect(screen.getByRole("status")).toHaveTextContent("null");
    expect(screen.getByRole("button", { name: "common:inputs.secretField.clear" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "common:inputs.secretField.undoClear" })).not.toBeInTheDocument();
  });

  test("names its buttons after the field", async () => {
    // cimode renders only the key, which would hide the interpolated label
    await i18n.changeLanguage("en-US");
    try {
      const { unmount } = render(<SecretField label="API key" value={null} onChange={() => {}} />);
      expect(screen.getByRole("button", { name: "Clear API key" })).toBeInTheDocument();
      expect(screen.getByRole("button", { name: "Show API key" })).toBeInTheDocument();
      unmount();
    } finally {
      await i18n.changeLanguage("cimode");
    }
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
