import { type Locator, page, userEvent } from "vitest/browser";

export class DayTimelinePage {
  get scroller(): Locator {
    return page.getByRole("region", {
      name: "24-hour calendar for Test instruments on Monday, August 17, 2026 in Europe/Berlin",
      exact: true,
    });
  }

  get outsideButton(): Locator {
    return page.getByRole("button", { name: "Outside timeline" });
  }

  trigger(title: string): Locator {
    return page.getByRole("button", { name: `Show details for ${title}`, exact: false });
  }

  hideButton(period: string, title: string): Locator {
    return this.popup(period).getByRole("button", { name: `Hide details for ${title}, ${period}`, exact: true });
  }

  popup(period: string): Locator {
    return page.getByRole("dialog", { name: period });
  }

  async open(title: string): Promise<void> {
    await this.trigger(title).click();
  }

  async openWithKeyboard(title: string): Promise<void> {
    this.trigger(title).element().focus();
    await userEvent.keyboard("{Enter}");
  }

  async close(period: string, title: string): Promise<void> {
    await this.hideButton(period, title).click();
  }

  async pressEscape(): Promise<void> {
    await userEvent.keyboard("{Escape}");
  }
}
