import type { Locator, Page } from "@playwright/test";

/** PubChem-specific locators/actions for the live editor dialog. */
export class PubchemDialogPage {
  constructor(private readonly page: Page) {}

  get toolbarButton(): Locator {
    return this.page.locator('button[aria-label="Insert PubChem Compound"]');
  }

  /** Dialog root; absent (count 0) when the integration is disabled. */
  get root(): Locator {
    return this.page.locator("#tinymce-pubchem");
  }

  async open(): Promise<void> {
    await this.toolbarButton.click();
  }

  async search(term: string): Promise<void> {
    await this.page.locator('input[placeholder="Enter a compound name or CAS number"]').fill(term);
    // The MUI Dialog overlay intercepts the pointer hit-test on Search; native click bypasses it.
    await this.page
      .locator("#tinymce-pubchem button", { hasText: /^Search$/ })
      .evaluate((b) => (b as HTMLElement).click());
  }

  result(name: string): Locator {
    return this.page.getByText(name).first();
  }
}
