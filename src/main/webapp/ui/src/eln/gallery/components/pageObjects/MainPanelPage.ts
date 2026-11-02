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
   * Clicks a tree item's inner content `div`, matching the Playwright spec
   * pattern `treeitem.locator("> div").click()`. It uses a real pointer click,
   * not `HTMLElement.click()`. MUI X registers newly expanded children in an
   * effect, and a synchronous click before that effect runs crashes TreeItem
   * under StrictMode.
   */
  private async clickTreeItemContent(name: string): Promise<void> {
    await expect.element(this.treeItem(name)).toBeVisible();
    const inner = this.treeItem(name).element().querySelector<HTMLElement>(":scope > div");
    if (!inner) throw new Error(`No child div found for treeitem "${name}"`);
    await page.elementLocator(inner).click();
  }

  async expandTreeItem(name: string): Promise<void> {
    await this.clickTreeItemContent(name);
    await expect.element(this.treeItem(name)).toHaveAttribute("aria-expanded", "true");
  }

  /** MUI X 9 reports multi-select selection with aria-checked, not aria-selected. */
  async selectTreeItem(name: string): Promise<void> {
    await this.clickTreeItemContent(name);
    await expect.element(this.treeItem(name)).toHaveAttribute("aria-checked", "true");
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
