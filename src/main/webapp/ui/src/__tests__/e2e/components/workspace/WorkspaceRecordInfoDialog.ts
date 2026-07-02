import type { Locator, Page } from "@playwright/test";

/**
 * The "Info" dialog opened via a row's Record Info link. Server-rendered JSP
 * table with no `data-test-id`s — fields are keyed by their visible label.
 *
 * Field availability depends on record type: `Version`/`Signature Status` on
 * documents and notebooks only, `Created from`/`Form ID` on documents created
 * from a form, `Caption`/`Original Image` on gallery-linked documents.
 */
export class WorkspaceRecordInfoDialog {
  readonly root: Locator;

  constructor(page: Page) {
    this.root = page.getByRole("dialog", { name: "Info" });
  }

  async waitUntilVisible(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  /** Clicks OK — the dialog's only reliably-named dismiss action. */
  async close(): Promise<void> {
    await this.root.getByRole("button", { name: "OK" }).click();
    await this.root.waitFor({ state: "detached" });
  }

  /**
   * Reads a field's value from its table row, e.g. `field("Unique Id")`.
   * Strips the "Label: " prefix that the row's accessible name includes.
   */
  async field(name: string): Promise<string> {
    const row = this.root.getByRole("row", { name: new RegExp(`^${name}:`) });
    const text = await row.innerText();
    return text.replace(new RegExp(`^${name}:\\s*`), "").trim();
  }

  async isShared(): Promise<boolean> {
    const text = await this.root.innerText();
    return !text.includes("This document is not shared.");
  }

  async isPublished(): Promise<boolean> {
    const text = await this.root.innerText();
    return !text.includes("This document is not published.");
  }
}
