import { type Locator, page, userEvent } from "vitest/browser";

/** Page object for ContentGrid, as mounted by ContentGrid.story.tsx. */
export class ContentGridPage {
  get grid(): Locator {
    return page.getByRole("grid");
  }

  /** The nth (0-based, row-major) location cell. */
  cell(n: number): Locator {
    return page.getByRole("gridcell").nth(n);
  }

  /** The nth cell's painted focus ring, "none" when it has none. */
  focusIndicator(n: number): string {
    return getComputedStyle(this.cell(n).element()).boxShadow;
  }

  /** Firefox gives the scrollable table container its own tab stop before the first location. */
  async tabIntoGrid(): Promise<void> {
    await userEvent.tab();
    if (!this.cell(0).element().contains(document.activeElement)) await userEvent.tab();
  }

  async press(keys: string): Promise<void> {
    await userEvent.keyboard(keys);
  }
}
