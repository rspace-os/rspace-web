import { type Locator, page } from "vitest/browser";

/**
 * Page object for the ontologies TagsCombobox, opened via AddTag's "Add Tag"
 * chip. Locator getters only; actions are async; no assertions.
 */
export class TagsComboboxPage {
  get addTagChip(): Locator {
    return page.getByRole("button", { name: /add tag/i });
  }

  get filterInput(): Locator {
    // useAutocomplete gives the input role="combobox" (not textbox).
    return page.getByRole("combobox", { name: /filter suggested tags/i });
  }

  /** The react-window scroll container (a <ul> with role=listbox from useAutocomplete). */
  get listbox(): Locator {
    return page.getByRole("listbox");
  }

  option(name: string | RegExp): Locator {
    return page.getByRole("option", { name });
  }

  async open(): Promise<void> {
    await this.addTagChip.click();
  }

  filter(text: string): void {
    // Set the value via React's native input setter + an `input` event so MUI's
    // useAutocomplete onInputChange fires. userEvent.type/fill hang here: the
    // debounced list re-renders continuously, so the input never satisfies
    // Playwright's actionability/stability check. Synchronous DOM avoids that.
    const input = this.filterInput.element() as HTMLInputElement;
    input.focus();
    const setValue = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value")?.set;
    setValue?.call(input, text);
    input.dispatchEvent(new Event("input", { bubbles: true }));
  }

  /**
   * Scroll the virtualised list to the bottom and notify react-window. react-window
   * recomputes its rendered range (and fires onRowsRendered) on the container's
   * scroll event, so set scrollTop then dispatch a scroll event.
   */
  async scrollListToBottom(): Promise<void> {
    const el = this.listbox.element() as HTMLElement;
    el.scrollTop = el.scrollHeight;
    el.dispatchEvent(new Event("scroll", { bubbles: true }));
  }

  async selectOption(name: string | RegExp): Promise<void> {
    await this.option(name).click();
  }
}
