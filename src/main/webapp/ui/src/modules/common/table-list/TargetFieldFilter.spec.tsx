import { cleanup, render } from "@testing-library/react";
import { page, userEvent } from "@vitest/browser/context";
import { afterEach, describe, expect, test } from "vitest";
import { TargetFieldFilterStory } from "./TargetFieldFilter.story";

afterEach(() => {
  cleanup();
});

async function openFilterRow() {
  const { container } = render(<TargetFieldFilterStory />);
  const screen = page.elementLocator(container);
  const locators = {
    field: screen.getByRole("combobox", { name: "Field for filter 1" }),
    operator: screen.getByRole("combobox", { name: "Operator for filter 1" }),
    value: screen.getByRole("combobox", { name: "Value for filter 1" }),
    apply: screen.getByRole("button", { name: "Apply filters" }),
    lastQuery: screen.getByTestId("last-query"),
  };
  await expect.element(locators.lastQuery).toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "Filters, none applied" }));
  await userEvent.click(screen.getByRole("button", { name: "Add filter" }));
  return locators;
}

describe("filtering by a field of a relationship target", () => {
  test("a user can build target.name and the table sends it as RSQL", async () => {
    const { field, operator, value, apply, lastQuery } = await openFilterRow();

    // Offered because the API publishes target.name and the adapter derives it. The collection
    // config declares the relationship only, and never names this selector.
    await expect.element(field).toHaveTextContent("Bookable item name");
    await userEvent.selectOptions(field, "target.name");

    // The server permits positive matching only on a target field, so no negative operator here.
    await expect.element(operator).not.toHaveTextContent("does not equal");
    await userEvent.selectOptions(operator, "contains");

    await userEvent.fill(value, "confocal");
    await userEvent.click(apply);

    await expect.element(lastQuery).toHaveTextContent("target.name=contains=confocal");
  });
});
