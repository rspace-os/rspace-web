import { type Locator, page } from "vitest/browser";

export class CalendarPage {
  get heading(): Locator {
    return page.getByRole("heading", { name: "Calendar" });
  }

  get table(): Locator {
    return page.getByRole("table", { name: /Bookable items/ });
  }

  availability(itemName: string): Locator {
    return page.getByRole("img", { name: `${itemName} availability` });
  }

  item(itemName: string): Locator {
    return page.getByRole("button", { name: new RegExp(itemName) });
  }

  detail(itemName: string): Locator {
    return page.getByRole("region", { name: `Bookings for ${itemName}` });
  }

  get busy(): Locator {
    return page.getByRole("article", { name: /^Busy,/ });
  }

  get nextDay(): Locator {
    return page.getByRole("button", { name: "Next day" });
  }

  get previousDay(): Locator {
    return page.getByRole("button", { name: "Previous day" });
  }

  get today(): Locator {
    return page.getByRole("button", { name: "Today" });
  }

  get nextPage(): Locator {
    return page.getByRole("button", { name: "Next page" });
  }

  async openItem(itemName: string): Promise<void> {
    await this.item(itemName).click();
  }
}
