import { type Locator, page } from "vitest/browser";

export class SidebarPage {
  get createButton(): Locator {
    return page.getByRole("button", { name: "Create" });
  }

  get menu(): Locator {
    return page.getByRole("menu");
  }

  get dmptool(): Locator {
    return page.getByRole("menuitem", { name: /dmptool/i });
  }

  async openCreateMenu(): Promise<void> {
    await this.createButton.click();
  }
}
