import type { Locator, Page } from "@playwright/test";
import { clickAndWaitDetached } from "./DialogHelpers";

/**
 * The Operations Wizard dialog: reached from a subsample's "Process" context menu action, or (for
 * Sample Requests) the Requests page's "Prepare Sample" button via the Choose Sample to Prepare
 * dialog's "Create a new sample derived from the existing sample" option.
 *
 * Its accessible name changes from "Process subsample" to the chosen operation's label (e.g.
 * "Aliquot") once one is picked, so `root` is deliberately unscoped by name - callers only reach
 * for this component when it's the only dialog open.
 */
export class OperationWizardComponent {
  readonly root: Locator;

  constructor(page: Page) {
    this.root = page.getByRole("dialog");
  }

  async waitForOpen(): Promise<void> {
    await this.root.getByRole("heading", { name: "Process subsample" }).waitFor({ state: "visible" });
  }

  /** Picks one of the operations (e.g. "Aliquot") from the picker shown when the wizard opens. */
  async selectOperation(label: string): Promise<void> {
    await this.root.getByRole("button", { name: label }).first().click();
  }

  async setSampleName(name: string): Promise<void> {
    await this.root.getByRole("textbox", { name: "New sample name", exact: true }).fill(name);
  }

  async selectNoTemplate(): Promise<void> {
    await this.root.getByRole("radio", { name: "No template", exact: true }).check();
  }

  async next(): Promise<void> {
    await this.root.getByRole("button", { name: "Next", exact: true }).click();
  }

  /**
   * Performs the operation; waits for the wizard to close, which only happens on success.
   * Waits on the Perform button's own detachment rather than `root` (a generic, unscoped "any
   * dialog" locator) becoming hidden - a caller that opens straight into another dialog once this
   * one closes (e.g. a Transfer Ownership dialog, for the Sample Requests flow) means there is
   * always *some* visible dialog, so `root` itself would never actually go hidden.
   */
  async perform(): Promise<void> {
    const performButton = this.root.getByRole("button", { name: "Perform", exact: true });
    await clickAndWaitDetached(performButton);
  }

  /**
   * The common case this component is built around: an Aliquot with the default amounts, no
   * documentation, and no template, naming the new sample `newSampleName`. Leaves whatever opens
   * once the wizard closes (e.g. a Transfer Ownership dialog, for the Sample Requests flow) for
   * the caller to interact with.
   */
  async performAliquot(newSampleName: string): Promise<void> {
    await this.waitForOpen();
    await this.selectOperation("Aliquot");
    await this.setSampleName(newSampleName);
    await this.next(); // details -> template
    await this.selectNoTemplate();
    await this.next(); // template -> amounts
    await this.next(); // amounts -> documentation
    await this.next(); // documentation -> confirm
    await this.perform();
  }
}
