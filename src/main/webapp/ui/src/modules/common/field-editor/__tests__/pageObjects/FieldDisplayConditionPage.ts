import { type Locator, page, userEvent } from "vitest/browser";

export class FieldDisplayConditionPage {
  get field(): Locator {
    return page.getByRole("combobox", { name: "Field" });
  }

  get listbox(): Locator {
    return page.getByRole("listbox");
  }

  get chosen(): Locator {
    return page.getByRole("option", { name: "Is this a GMO?" });
  }

  get notes(): Locator {
    return page.getByRole("option", { name: 'Notes Unavailable - Notes has unsupported type "Formatted text"' });
  }

  async openMenu(): Promise<void> {
    await this.field.click();
  }

  async press(key: string): Promise<void> {
    await userEvent.keyboard(key);
  }
}
