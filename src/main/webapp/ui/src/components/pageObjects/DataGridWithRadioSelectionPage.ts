import { vi } from "vitest";
import { type Locator, page, userEvent } from "vitest/browser";

/**
 * Page object for DataGridWithRadioSelection, as mounted by the stories in
 * DataGridWithRadioSelection.story.tsx. Encapsulates locators and user
 * interactions; assertions live in the specs themselves.
 *
 */
export class DataGridWithRadioSelectionPage {
  readonly grid: Locator = page.getByRole("grid");

  /** The first column header — should be "Select" for the radio column. */
  get firstColumnHeader(): Locator {
    return page.getByRole("columnheader").first();
  }

  /**
   * Returns the data rows (i.e. not the column-header row). Data rows contain
   * `gridcell`s; the header row contains `columnheader`s, so filtering by the
   * presence of a gridcell selects the data rows without a CSS attribute query.
   */
  dataRows(): Locator {
    return this.grid.getByRole("row").filter({ has: page.getByRole("gridcell") });
  }

  /** Returns the nth data row (0-indexed). */
  dataRow(i: number): Locator {
    return this.dataRows().nth(i);
  }

  /** Returns the radio button inside the nth data row (0-indexed). */
  radioForRow(i: number): Locator {
    return this.dataRow(i).getByRole("radio");
  }

  /** Returns the first gridcell in the nth data row (0-indexed). */
  firstCellForRow(i: number): Locator {
    return this.dataRow(i).getByRole("gridcell").first();
  }

  /** Returns the selection indicator element. */
  get selectionIndicator(): Locator {
    return page.getByTestId("selection-indicator");
  }

  /** Returns the column header with the given name. */
  columnHeader(name: string): Locator {
    return page.getByRole("columnheader", { name });
  }

  /** Clicks the radio button for the nth row (0-indexed). */
  async clickRadioForRow(i: number): Promise<void> {
    await this.radioForRow(i).click();
  }

  /**
   * Tabs through the page until the radio in the nth data row gains focus, or
   * the maxTabs safety limit is reached.
   */
  async tabToRadioForRow(i: number, maxTabs = 20): Promise<void> {
    let found = false;
    let tabCount = 0;
    while (!found && tabCount < maxTabs) {
      await userEvent.keyboard("{Tab}");
      found = document.activeElement === this.radioForRow(i).element();
      tabCount++;
    }
  }

  /** Presses the space key (to activate a focused radio). */
  async pressSpace(): Promise<void> {
    await userEvent.keyboard(" ");
  }

  /** Clicks a column header to trigger sort. */
  async clickSort(columnName: string): Promise<void> {
    await this.columnHeader(columnName).click();
  }

  /**
   * Changes the page size via the MUI TablePagination rows-per-page Select.
   *
   * MUI v9's `TablePagination` renders a `div[role="combobox"]` that Playwright
   * marks as "not visible" when the pagination footer is scrolled below the
   * Vitest iframe's viewport.  We bypass Playwright's visibility gate by
   * dispatching a synthetic `mousedown` + `mouseup` + `click` event sequence
   * directly on the DOM element, then wait for the MUI Select's listbox to
   * mount and click the matching option.
   */
  async changePageSize(size: number): Promise<void> {
    // MUI X's pagination select renders as a `role="combobox"` labelled by the
    // TablePagination "Rows per page:" label (DataGrid's `labelRowsPerPage`), so
    // we address it semantically by that accessible name. The display element is
    // laid out at 0x0 in the DataGrid footer, which the role engine treats as
    // hidden, so `includeHidden` is required for it to match.
    const selectEl = page.getByRole("combobox", { name: /rows per page/i, includeHidden: true }).element();

    // MUI Select opens on `mousedown` on the control div.
    const fire = (type: string) => selectEl.dispatchEvent(new MouseEvent(type, { bubbles: true, cancelable: true }));
    fire("mousedown");
    fire("mouseup");
    fire("click");

    // Wait for the listbox to be present in the DOM.
    await vi.waitFor(
      () => {
        if (!document.querySelector('[role="listbox"]')) {
          throw new Error("MUI Select listbox not mounted yet");
        }
      },
      { timeout: 5000, interval: 50 },
    );

    // Click the option via native DOM to avoid viewport visibility checks.
    const options = Array.from(document.querySelectorAll<HTMLElement>('[role="option"]'));
    const target = options.find((o) => o.textContent?.trim() === `${size}`);
    if (!target) {
      throw new Error(`Page size option "${size}" not found in MUI Select listbox`);
    }
    target.click();
  }

  /** Clicks the "Go to next page" button. */
  async goToNextPage(): Promise<void> {
    await page.getByRole("button", { name: /Go to next page/i }).click();
  }

  /** Clicks the "Go to previous page" button. */
  async goToPreviousPage(): Promise<void> {
    await page.getByRole("button", { name: /Go to previous page/i }).click();
  }
}
