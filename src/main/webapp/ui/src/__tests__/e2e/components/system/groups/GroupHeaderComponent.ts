import type { Locator, Page } from "@playwright/test";

export class GroupHeaderComponent {
  private readonly page: Page;

  constructor(page: Page) {
    this.page = page;
  }

  get changeGroupLink(): Locator {
    return this.page.getByRole("link", { name: "Change Group", exact: true });
  }

  groupOption(groupName: string): Locator {
    return this.page.locator("#group_options").getByRole("link").filter({ hasText: groupName });
  }

  async changeGroup(groupName: string): Promise<void> {
    await this.changeGroupLink.click();
    await this.groupOption(groupName).click();
  }
}
