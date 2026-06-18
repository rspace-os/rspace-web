import { vi } from "vitest";
import { type Locator, page } from "vitest/browser";

/**
 * Page object for the IGSN table, as mounted by the stories in
 * IgsnTable.story.tsx. Encapsulates the locators and user interactions;
 * assertions live in the tests themselves.
 */
export class IgsnTablePage {
  readonly table: Locator = page.getByRole("grid");

  /**
   * Waits until the table has rendered at least one data row (beyond the
   * header row) or the empty-state message is shown.
   */
  async waitForLoad(): Promise<void> {
    await vi.waitFor(
      () => {
        const rows = document.querySelectorAll('[role="row"]').length;
        const noIgsn = document.body.textContent?.includes("No IGSN IDs");
        if (!((rows > 1 || noIgsn) ?? false)) {
          throw new Error("IGSN table not loaded yet");
        }
      },
      { timeout: 10000, interval: 50 },
    );
  }

  dataRows(): Locator {
    return this.table.getByCSS('[role="row"][data-id]');
  }

  get exportButton(): Locator {
    return page.getByRole("button", { name: /Export/ });
  }

  /**
   * Waits until exactly `expected` data rows are rendered in the grid.
   *
   * `waitForLoad()` only guarantees the first data row has appeared; on slower
   * engines (Firefox) the remaining rows can still be populating when an export
   * is triggered, so `exportDataAsCsv` would emit a partial CSV. Waiting for the
   * full row count makes the export deterministic across browsers.
   */
  async waitForRowCount(expected: number): Promise<void> {
    await vi.waitFor(
      () => {
        const count = document.querySelectorAll('[role="row"][data-id]').length;
        if (count !== expected) {
          throw new Error(`Expected ${expected} data rows, found ${count}`);
        }
      },
      { timeout: 10000, interval: 50 },
    );
  }

  /**
   * Triggers the "Export to CSV" action and returns the generated CSV text.
   *
   * Vitest browser mode has no Playwright-style download interception, so we
   * capture the Blob that MUI's `exportDataAsCsv` hands to
   * `URL.createObjectURL` and read its text, suppressing the anchor click
   * that would otherwise trigger a real browser download.
   */
  async exportToCsv(): Promise<string> {
    await this.exportButton.click();

    const blobs: Blob[] = [];
    // eslint-disable-next-line @typescript-eslint/unbound-method
    const originalCreate = URL.createObjectURL;
    // eslint-disable-next-line @typescript-eslint/unbound-method
    const originalRevoke = URL.revokeObjectURL;
    // eslint-disable-next-line @typescript-eslint/unbound-method
    const originalClick = HTMLAnchorElement.prototype.click;

    URL.createObjectURL = (obj: Blob | MediaSource) => {
      if (obj instanceof Blob) blobs.push(obj);
      return "blob:mock-csv";
    };
    URL.revokeObjectURL = () => {};
    HTMLAnchorElement.prototype.click = function noop() {};

    try {
      await page.getByRole("menuitem", { name: /Export to CSV/ }).click();
    } finally {
      URL.createObjectURL = originalCreate;
      URL.revokeObjectURL = originalRevoke;
      HTMLAnchorElement.prototype.click = originalClick;
    }

    const blob = blobs[blobs.length - 1];
    if (!blob) {
      throw new Error("No CSV blob was produced by the export");
    }
    return blob.text();
  }

  async selectRowByIndex(index: number): Promise<void> {
    const checkboxes = page.getByRole("checkbox", { name: /Select row/ });
    await checkboxes.nth(index).click();
  }
}
