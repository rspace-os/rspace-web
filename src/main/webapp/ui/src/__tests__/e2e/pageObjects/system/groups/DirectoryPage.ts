import type { Locator } from "@playwright/test";
import { BasePage } from "@/__tests__/e2e/pageObjects/BasePage";

export class DirectoryPage extends BasePage {
  readonly path = "/directory";

  get heading(): Locator {
    return this.page.getByRole("heading", { level: 2 });
  }

  async findGroupIdForUser(username: string, groupName: string): Promise<number> {
    await this.page.goto(this.path);
    const searchBox = this.page.getByRole("textbox", { name: "Search" });
    await searchBox.fill(username);
    await searchBox.press("Enter");
    const link = this.page
      .getByRole("row")
      .filter({ hasText: username })
      .getByRole("link", { name: groupName, exact: true });
    await link.waitFor({ state: "visible" });
    const href = await link.getAttribute("href");
    return Number((href ?? "").split("/groups/view/")[1]);
  }
}
