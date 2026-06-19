import { expect } from "vitest";
import { type Locator, page, userEvent } from "vitest/browser";

/**
 * Page object for the roving tab index stories, as mounted by
 * useOneDimensionalRovingTabIndex.story.tsx. Encapsulates the locators and
 * user interactions; assertions live in the tests themselves.
 */
export class RovingTabIndexPage {
  get beforeButton(): Locator {
    return page.getByRole("button", { name: "Before the list" });
  }

  get firstListItemButton(): Locator {
    return page.getByRole("listitem").first().getByRole("button");
  }

  get secondListItemButton(): Locator {
    return page.getByRole("listitem").nth(1).getByRole("button");
  }

  get lastButton(): Locator {
    return page.getByRole("button").last();
  }

  async focusBeforeButton(): Promise<void> {
    const el = this.beforeButton;
    await expect.element(el).toBeVisible();
    el.element().focus();
  }

  async focusFirstListItem(): Promise<void> {
    const firstListItem = page.getByRole("button", { name: "One Thing" });
    await expect.element(firstListItem).toBeVisible();
    firstListItem.element().focus();
  }

  async pressTab(): Promise<void> {
    await userEvent.keyboard("{Tab}");
  }

  async pressTabMultipleTimes(count: number): Promise<void> {
    for (let i = 0; i < count; i++) {
      await this.pressTab();
    }
  }

  async pressArrowDown(): Promise<void> {
    await userEvent.keyboard("{ArrowDown}");
  }

  async pressArrowUp(): Promise<void> {
    await userEvent.keyboard("{ArrowUp}");
  }

  async pressArrowRight(): Promise<void> {
    await userEvent.keyboard("{ArrowRight}");
  }

  async pressArrowLeft(): Promise<void> {
    await userEvent.keyboard("{ArrowLeft}");
  }

  async shiftTab(): Promise<void> {
    await userEvent.keyboard("{Shift>}{Tab}{/Shift}");
  }
}
