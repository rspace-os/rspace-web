import type { Locator, Page } from "@playwright/test";

/**
 * The "Import from Fieldmark" dialog opened via Inventory → Create → Fieldmark.
 *
 * Dialog accessible name: "Import from Fieldmark" (from the h2 heading).
 * Every locator is scoped to `this.root` so it never leaks outside the dialog.
 *
 * The IGSN ID field may show a 400 when the backend cannot load candidate
 * fields — this is non-blocking for the core import flow and is not asserted
 * here. The import button remains clickable regardless.
 */
export class FieldmarkDialogComponent {
  readonly root: Locator;
  readonly importButton: Locator;
  readonly closeButton: Locator;

  /**
   * Toasts container: section[data-testid="Toasts"]. Note: RSpace's Playwright
   * config sets testIdAttribute to "data-test-id", so getByTestId() does NOT
   * match this element — use locator() with the literal attribute instead.
   */
  private readonly toastsRegion: Locator;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("dialog", { name: "Import from Fieldmark" });
    this.importButton = this.root.getByRole("button", { name: "Import" });
    this.closeButton = this.root.getByRole("button", { name: "Close" });
    this.toastsRegion = page.locator('[data-testid="Toasts"]');
  }

  /** Radio button for a notebook row, matched by exact notebook name. */
  notebookRadio(name: string): Locator {
    return this.root.getByRole("radio", { name: `Select notebook: ${name}` });
  }

  /**
   * The success toast. Non-infinite — auto-dismisses after ~4 s.
   * Assert before the timeout by calling immediately after import resolves.
   */
  get successToast(): Locator {
    return this.toastsRegion
      .getByRole("group", { name: "success alert" })
      .filter({ hasText: "Successfully imported notebook." });
  }

  /** The "Importing notebook…" notice toast (infinite — persists until import finishes). */
  get importingToast(): Locator {
    return this.toastsRegion.getByRole("group", { name: "notice alert" });
  }

  async waitForOpen(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
    // Grid header is the readiness signal — notebook list has rendered
    await this.root.getByRole("columnheader", { name: "Name" }).waitFor({ state: "visible" });
  }

  async selectNotebook(name: string): Promise<void> {
    await this.notebookRadio(name).click();
  }

  /**
   * Clicks Import and waits for the backend POST to complete.
   * Returns the HTTP response so callers can assert `response.ok()`.
   */
  async clickImport(): Promise<void> {
    const [response] = await Promise.all([
      this.page.waitForResponse((r) => r.url().includes("/api/inventory/v1/import/fieldmark/notebook")),
      this.importButton.click(),
    ]);
    if (!response.ok()) {
      throw new Error(
        `POST /api/inventory/v1/import/fieldmark/notebook failed: ${response.status()} ${response.statusText()}`,
      );
    }
    await this.root.waitFor({ state: "detached" });
  }
}
