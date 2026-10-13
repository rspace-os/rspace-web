import { expect, type Locator, type Page } from "@playwright/test";

const ANNOUNCEMENT = /This location is not empty\.|Selection limit reached/;
// The cells carry the implicit gridcell role of a td, and the row header is a td too.
const LOCATION_CELL = 'td:not([role="rowheader"])';
const TIPS = /Use the arrow keys to move|Press Enter to drop items|Expand selection by holding Shift/;

/**
 * A grid container's contents grid, wherever it is rendered: the container page, the Move dialog,
 * the Create dialog's location picker or the operation wizard's Location step.
 *
 * Cells are addressed as the headers show them, row letter then column number ("B2"), which holds
 * for containers created with the default ABC row and 123 column labels. An empty cell is named by
 * its row-major number; an occupied or previewed cell has no name of its own.
 */
export class ContentGridComponent {
  readonly root: Locator;

  constructor(
    private readonly page: Page,
    private readonly scope: Locator,
  ) {
    this.root = scope.getByRole("grid", { name: "Container contents grid" });
  }

  async waitForLoaded(): Promise<void> {
    await this.root.getByRole("rowheader").first().waitFor({ state: "visible" });
  }

  cell(label: string): Locator {
    const match = /^([A-Z]+)(\d+)$/.exec(label);
    if (!match) throw new Error(`"${label}" is not a grid label such as "B2".`);
    const [, row, column] = match;
    return this.root
      .getByRole("row")
      .filter({ has: this.page.getByRole("rowheader", { name: row, exact: true }) })
      .getByRole("gridcell")
      .nth(Number(column) - 1);
  }

  async click(...labels: string[]): Promise<void> {
    for (const label of labels) {
      await this.cell(label).click();
    }
  }

  /** Labels of the selected cells, in row-major order. */
  async selectedLabels(): Promise<string[]> {
    return this.root.evaluate((grid, cellSelector) => {
      const columns = [...grid.querySelectorAll("thead th")].slice(1).map((th) => th.textContent?.trim() ?? "");
      const labels: string[] = [];
      for (const row of grid.querySelectorAll("tbody tr")) {
        const rowLabel = row.querySelector('[role="rowheader"]')?.textContent?.trim() ?? "";
        row.querySelectorAll(cellSelector).forEach((cell, i) => {
          if (cell.getAttribute("aria-selected") === "true") labels.push(`${rowLabel}${columns[i]}`);
        });
      }
      return labels;
    }, LOCATION_CELL);
  }

  async expectSelected(labels: string[]): Promise<void> {
    await expect.poll(() => this.selectedLabels()).toEqual(labels);
  }

  /** The label of the cell holding DOM focus, or null when focus is outside the grid. */
  async focusedLabel(): Promise<string | null> {
    return this.root.evaluate((grid, cellSelector) => {
      const cell = document.activeElement?.closest(cellSelector);
      if (!cell || !grid.contains(cell)) return null;
      const row = cell.closest("tr");
      const rowLabel = row?.querySelector('[role="rowheader"]')?.textContent?.trim() ?? "";
      const index = [...(row?.querySelectorAll(cellSelector) ?? [])].indexOf(cell);
      const columns = [...grid.querySelectorAll("thead th")].slice(1).map((th) => th.textContent?.trim() ?? "");
      return `${rowLabel}${columns[index]}`;
    }, LOCATION_CELL);
  }

  /**
   * Tabs from `before`, the control that precedes the grid, until a cell holds focus. Firefox makes
   * the grid's scroll container a tab stop of its own, so it can take more than one Tab.
   */
  async tabIn(before: Locator): Promise<void> {
    await before.focus();
    for (let i = 0; i < 6; i++) {
      await this.page.keyboard.press("Tab");
      if ((await this.focusedLabel()) !== null) return;
    }
    throw new Error("Tabbing from the preceding control did not reach a grid cell.");
  }

  /** Presses each key in turn, e.g. press("Space", "ArrowRight", "Shift+ArrowDown"). */
  async press(...keys: string[]): Promise<void> {
    for (const key of keys) {
      await this.page.keyboard.press(key);
    }
  }

  /** The grid's live region, holding why the last Space or range step was refused. */
  announcement(): Locator {
    return this.scope.getByRole("status").filter({ hasText: ANNOUNCEMENT });
  }

  /** The keyboard tips Snackbar shown while the grid has focus. */
  tips(): Locator {
    return this.page.getByRole("alert").filter({ hasText: TIPS });
  }
}
