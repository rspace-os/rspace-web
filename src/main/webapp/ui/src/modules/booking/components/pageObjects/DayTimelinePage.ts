import { type Locator, page, userEvent } from "vitest/browser";

export class DayTimelinePage {
  get scroller(): Locator {
    return page.getByRole("region", { name: /24-hour calendar for Test instruments/ });
  }

  get outsideButton(): Locator {
    return page.getByRole("button", { name: "Outside timeline" });
  }

  trigger(title: string): Locator {
    return page.getByRole("button", { name: new RegExp(`Show details for ${title}`) });
  }

  hideButton(period: string, title: string): Locator {
    return this.popup(period).getByRole("button", { name: new RegExp(`Hide details for ${title}`) });
  }

  popup(period: string): Locator {
    return page.getByRole("dialog", { name: period });
  }

  async open(title: string): Promise<void> {
    await this.trigger(title).click();
  }

  async close(period: string, title: string): Promise<void> {
    await this.hideButton(period, title).click();
  }

  async pressEscape(): Promise<void> {
    await userEvent.keyboard("{Escape}");
  }
}
