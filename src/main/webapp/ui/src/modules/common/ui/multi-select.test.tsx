import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, test, vi } from "vitest";
import { MultiSelect } from "./multi-select";

const labels = {
  ariaLabel: "Tags",
  placeholder: "Choose tags",
  emptyMessage: "No tags",
  removeLabel: (label: string) => `Remove ${label}`,
};

test("keeps selected values absent from options available", async () => {
  const user = userEvent.setup();
  render(<MultiSelect {...labels} options={["Apple"]} value={["Pear"]} onValueChange={vi.fn()} />);
  expect(screen.getByRole("button", { name: "Remove Pear" })).toBeInTheDocument();
  await user.click(screen.getByRole("combobox", { name: "Tags" }));
  expect(await screen.findByRole("option", { name: "Pear" })).toHaveAttribute("aria-selected", "true");
});

test.each([true, false])("offers a trimmed custom value first only when allowed: %s", async (allowCustomValues) => {
  const user = userEvent.setup();
  render(
    <MultiSelect
      {...labels}
      options={["Apricot"]}
      value={[]}
      onValueChange={vi.fn()}
      allowCustomValues={allowCustomValues}
    />,
  );
  await user.type(screen.getByRole("combobox", { name: "Tags" }), "  Ap  ");
  if (allowCustomValues) {
    expect((await screen.findAllByRole("option"))[0]).toHaveAccessibleName("Ap");
  } else {
    expect(screen.queryByRole("option", { name: "Ap" })).not.toBeInTheDocument();
  }
});

test("does not duplicate a custom value matching an option", async () => {
  const user = userEvent.setup();
  render(<MultiSelect {...labels} options={["Apple"]} value={[]} onValueChange={vi.fn()} allowCustomValues />);
  await user.type(screen.getByRole("combobox", { name: "Tags" }), "Apple");
  expect(await screen.findAllByRole("option", { name: "Apple" })).toHaveLength(1);
});
