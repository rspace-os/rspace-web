import { type Locator, page, userEvent } from "vitest/browser";

/**
 * Page object for the PubChem ImportDialog, as mounted by ImportDialogStory.
 * Encapsulates locators and user interactions; assertions live in the tests.
 */
export class PubchemImportDialogPage {
  get searchInput(): Locator {
    return page.getByRole("textbox");
  }

  get searchButton(): Locator {
    return page.getByRole("button", { name: "Search" });
  }

  get firstCompoundCard(): Locator {
    return page.getByRole("region").first();
  }

  get firstCompoundCheckbox(): Locator {
    return page.getByRole("checkbox", { name: /select/i }).first();
  }

  get firstViewOnPubchemLink(): Locator {
    return page.getByRole("link", { name: /View on PubChem/i }).first();
  }

  compoundCard(name: string): Locator {
    return page.getByRole("region", { name });
  }

  compoundCardButton(name: string): Locator {
    return this.compoundCard(name).getByRole("button");
  }

  compoundCardCheckbox(name: string): Locator {
    return this.compoundCard(name).getByRole("checkbox");
  }

  /**
   * Clicks the first compound checkbox using the native DOM click() to bypass
   * Playwright's pointer-event hit-test, which is blocked by the MUI
   * DialogContent overflow container.
   */
  clickFirstCompoundCheckbox(): void {
    const el = this.firstCompoundCheckbox.element();
    (el as HTMLElement).click();
  }

  async performSearch(term: string): Promise<void> {
    // Use fill (Playwright-native) to set the value reliably in the MUI input
    // with a Select adornment, then click Search to trigger the form submit.
    await userEvent.fill(this.searchInput, term);
    await this.searchButton.click();
  }

  /**
   * Puts keyboard focus on the Aspirin compound card's action button.
   *
   * We focus the element directly rather than pressing Tab until it matches
   * `document.activeElement`: tab traversal is engine-fragile (on Firefox,
   * focus never lands exactly on the MUI CardActionArea button node, so a
   * tab-until-match loop spins until the test times out). The behaviour under
   * test here is that pressing Enter on the focused card toggles selection —
   * the dialog's tab order is already exercised by the checkbox test, which
   * tabs to a native input.
   */
  focusAspirinCardButton(): void {
    (this.compoundCardButton("Aspirin").element() as HTMLElement).focus();
  }

  /**
   * Tabs to the Aspirin compound card's checkbox and returns when the
   * checkbox has focus.
   */
  async tabToAspirinCardCheckbox(): Promise<void> {
    const checkbox = this.compoundCardCheckbox("Aspirin");
    while (checkbox.element() !== document.activeElement) {
      await userEvent.keyboard("{Tab}");
    }
  }
}
