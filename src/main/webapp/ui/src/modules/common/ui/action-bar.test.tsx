import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, test } from "vitest";
import { ActionBar } from "./action-bar";

test("keeps a late cancel first and caps the remaining actions before overflow", async () => {
  const user = userEvent.setup();
  render(
    <ActionBar
      actions={[
        ...["First", "Second", "Third", "Fourth", "Dropped"].map((label) => ({ label })),
        { label: "Cancel", style: "cancel" },
      ]}
    />,
  );
  expect(screen.getAllByRole("button").map((button) => button.textContent)).toEqual([
    "Cancel",
    "First",
    "Second",
    "Third",
    "Fourth",
    "",
  ]);
  await user.click(screen.getByRole("button", { name: "common:actions.moreActions" }));
  expect(screen.getAllByRole("menuitem").map((item) => item.textContent)).toEqual(["Second", "Third", "Fourth"]);
  expect(screen.queryByRole("menuitem", { name: "Cancel" })).not.toBeInTheDocument();
});
