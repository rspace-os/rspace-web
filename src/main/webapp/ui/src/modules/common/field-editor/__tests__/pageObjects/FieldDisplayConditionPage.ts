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

  get noAvailableFields(): Locator {
    return page.getByRole("option", { name: "No available fields" });
  }

  async chooseConditional(): Promise<void> {
    await page.getByRole("radio", { name: "Only when another field has certain values" }).click();
  }

  async openMenu(): Promise<void> {
    await this.field.click();
  }

  async press(key: string): Promise<void> {
    await userEvent.keyboard(key);
  }
}
