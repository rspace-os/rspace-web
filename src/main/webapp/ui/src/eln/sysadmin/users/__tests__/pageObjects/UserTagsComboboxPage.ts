import { type Locator, page } from "vitest/browser";

/**
 * Page object for the sysadmin user-tags TagsCombobox (opened via the harness's
 * trigger button). Locator getters only; actions are sync/async; no assertions.
 */
export class UserTagsComboboxPage {
  get openButton(): Locator {
    return page.getByRole("button", { name: /open user tags/i });
  }

  get filterInput(): Locator {
    // useAutocomplete gives the input role="combobox" (not textbox).
    return page.getByRole("combobox", { name: /filter suggested tags/i });
  }

  option(name: string | RegExp): Locator {
    return page.getByRole("option", { name });
  }

  async open(): Promise<void> {
    await this.openButton.click();
  }

  /**
   * Set the value via React's native input setter + an `input` event so MUI's
   * useAutocomplete onInputChange fires. The combobox only fetches once the
   * filter is at least 2 characters. (userEvent.type/fill are flaky here: the
   * list re-renders, so the input never satisfies Playwright's stability check.)
   */
  filter(text: string): void {
    const input = this.filterInput.element() as HTMLInputElement;
    input.focus();
    const setValue = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value")?.set;
    setValue?.call(input, text);
    input.dispatchEvent(new Event("input", { bubbles: true }));
  }

  async selectOption(name: string | RegExp): Promise<void> {
    await this.option(name).click();
  }
}
