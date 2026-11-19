import { type Locator, page, userEvent } from "vitest/browser";

export class LiveDisplayRulesPage {
  get weight(): Locator {
    return page.getByRole("spinbutton", { name: "Weight" });
  }

  get permit(): Locator {
    return page.getByRole("textbox", { name: "Heavy goods permit" });
  }

  get lightNote(): Locator {
    return page.getByRole("textbox", { name: "Light goods note" });
  }

  get status(): Locator {
    return page.getByRole("status");
  }

  /** Replaces the weight, leaving focus in it. */
  async typeWeight(value: string): Promise<void> {
    await userEvent.clear(this.weight);
    await userEvent.type(this.weight, value);
  }

  async pressTab(): Promise<void> {
    await userEvent.keyboard("{Tab}");
  }

  async pressEnterInWeight(): Promise<void> {
    await this.weight.click();
    await this.pressEnter();
  }

  async pressEnter(): Promise<void> {
    await userEvent.keyboard("{Enter}");
  }
}
