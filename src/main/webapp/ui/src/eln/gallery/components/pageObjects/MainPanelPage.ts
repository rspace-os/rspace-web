import { expect } from "vitest";
import { type Locator, page } from "vitest/browser";

/**
 * Page object for the gallery MainPanel component as mounted by the stories in
 * MainPanel.story.tsx. Encapsulates locators and user interactions; assertions
 * live in the tests themselves.
 */
export class MainPanelPage {
  // ── Breadcrumbs ───────────────────────────────────────────────────────────

  get breadcrumbs(): Locator {
    return page.getByRole("navigation", { name: "Breadcrumbs" });
  }

  breadcrumbItems(): Locator {
    return this.breadcrumbs.getByRole("listitem");
  }

  breadcrumbButton(name: string): Locator {
    return this.breadcrumbs.getByRole("button", { name });
  }

  // ── Grid cells ────────────────────────────────────────────────────────────

  gridCell(name: string): Locator {
    return page.getByRole("gridcell", { name });
  }

  // ── Files-listing controls area ───────────────────────────────────────────

  get listingControlsRegion(): Locator {
    return page.getByRole("region", { name: "files listing controls" });
  }

  get selectionStatus(): Locator {
    return this.listingControlsRegion.getByRole("status");
  }

  // ── Views menu ────────────────────────────────────────────────────────────

  async switchToTreeView(): Promise<void> {
    await page.getByRole("button", { name: /views/i }).click();
    await page.getByRole("menuitem", { name: /tree view/i }).click();
  }

  // ── Tree view items ───────────────────────────────────────────────────────

  treeItem(name: string): Locator {
    return page.getByRole("treeitem", { name });
  }

  /**
   * Expands a tree item by clicking its inner content `div`, matching the
   * Playwright spec pattern `treeitem.locator("> div").click()`.
   *
   * Uses `.element()` to get the DOM node, then queries its first `div` child.
   * This is equivalent to Playwright's `locator("> div")` selector.
   */
  async expandTreeItem(name: string): Promise<void> {
    // Ensure the tree item has rendered before reaching into its DOM.
    await expect.element(this.treeItem(name)).toBeVisible();
    const el = this.treeItem(name).element();
    const inner = el.querySelector<HTMLElement>(":scope > div");
    if (!inner) throw new Error(`No child div found for treeitem "${name}"`);
    inner.click();
  }

  /**
   * Selects a tree item by clicking its content row (same as expandTreeItem).
   */
  async selectTreeItem(name: string): Promise<void> {
    await this.expandTreeItem(name);
  }

  // ── Copy-to-clipboard button ──────────────────────────────────────────────

  get copyToClipboardButton(): Locator {
    return page.getByRole("button", { name: "Copy to clipboard" });
  }

  // ── Alert ─────────────────────────────────────────────────────────────────

  clipboardSuccessAlert(): Locator {
    return page.getByRole("alert", {
      name: "Link copied to clipboard successfully.",
    });
  }

  // ── Grid file interactions ────────────────────────────────────────────────

  async clickFile(
    name: string,
    { modifiers = [] }: { modifiers?: Array<"Shift" | "ControlOrMeta"> } = {},
  ): Promise<void> {
    await this.gridCell(name).click(modifiers.length ? { modifiers } : undefined);
  }

  async dblClickFile(name: string): Promise<void> {
    await this.gridCell(name).dblClick();
  }
}
