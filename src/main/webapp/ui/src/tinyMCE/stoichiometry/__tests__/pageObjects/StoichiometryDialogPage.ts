import { expect } from "vitest";
import { type Locator, page, userEvent } from "vitest/browser";

/**
 * Page object for the stoichiometry dialog, as mounted by the stories in
 * StoichiometryDialog.story.tsx. Encapsulates the locators and user
 * interactions; assertions live in the tests themselves.
 */
export class StoichiometryDialogPage {
  get calculateButton(): Locator {
    return page.getByRole("button", { name: "common:stoichiometry.dialog.calculate" });
  }

  get table(): Locator {
    return page.getByRole("grid");
  }

  get saveButton(): Locator {
    return page.getByRole("button", { name: "common:stoichiometry.dialog.saveChanges" });
  }

  get deleteButton(): Locator {
    return page.getByRole("button", { name: "common:actions.delete", exact: true });
  }

  get deleteConfirmationDialog(): Locator {
    return page.getByRole("dialog", { name: "common:stoichiometry.dialog.deleteTitle" });
  }

  inlineError(message: string): Locator {
    return page.getByRole("alert").filter({ hasText: message });
  }

  async clickCalculate(): Promise<void> {
    await this.calculateButton.click();
  }

  async editFirstMassCell(): Promise<void> {
    await expect.element(this.table).toBeVisible();

    const headerTexts = this.table
      .getByRole("columnheader")
      .elements()
      .map((el) => el.textContent ?? "");
    const indexOfMassColumn = headerTexts.indexOf("common:stoichiometry.table.columns.mass");

    if (indexOfMassColumn < 0) {
      throw new Error("Mass column not found");
    }

    const massCell = this.table.getByRole("row").nth(1).getByRole("gridcell").nth(indexOfMassColumn);

    await massCell.click();
    // Edit using keyboard only; avoids brittle input-selector waits.
    await userEvent.keyboard("{Enter}");
    await userEvent.keyboard("1");
    await userEvent.keyboard("{Enter}");
  }

  async selectLimitingReagent(name: string): Promise<void> {
    await this.table
      .getByRole("row")
      .filter({ has: page.getByRole("gridcell", { name, exact: true }) })
      .getByRole("radio", { name: "common:stoichiometry.table.label.selectLimitingReagent" })
      .click();
  }

  async saveChanges(): Promise<void> {
    await this.saveButton.click();
  }

  async clickDelete(): Promise<void> {
    await this.deleteButton.click();
  }

  async confirmDeletion(): Promise<void> {
    await this.deleteConfirmationDialog.getByRole("button", { name: "common:actions.delete", exact: true }).click();
  }
}
