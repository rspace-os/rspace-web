import type { Locator } from "@playwright/test";
import { BasePage } from "@/__tests__/e2e/pageObjects/BasePage";

export class CommunityAdminPage extends BasePage {
  readonly path = "/system/community";

  async openCommunityId(id: number): Promise<void> {
    await this.page.goto(`${this.path}/${id}`);
  }

  get heading(): Locator {
    return this.page.getByRole("heading", { level: 2 });
  }

  async waitUntilLoaded(name: string): Promise<void> {
    await this.heading.filter({ hasText: name }).waitFor({ state: "visible" });
  }

  async getCommunityName(): Promise<string> {
    const text = await this.heading.innerText();
    return text.split(" (created:")[0].trim();
  }

  async rename(newName: string): Promise<void> {
    await this.page.getByRole("link", { name: "Edit", exact: true }).click();
    const nameField = this.page.getByRole("textbox", { name: "Display name", exact: true });
    await nameField.fill(newName);
    await this.page.getByRole("button", { name: "Save", exact: true }).click();
    await this.heading.filter({ hasText: newName }).waitFor({ state: "visible" });
  }

  get addGroupLink(): Locator {
    return this.page.getByRole("link", { name: "Add group", exact: true });
  }

  async addGroup(groupName: string): Promise<void> {
    await this.addGroupLink.click();
    const row = this.page
      .getByRole("row")
      .filter({ has: this.page.getByRole("link", { name: groupName, exact: true }) });
    await row.getByRole("checkbox").check();
    await this.page.getByRole("link", { name: "Go", exact: true }).click();
    await this.page
      .getByRole("heading", { name: "Lab groups in this community:", exact: true })
      .waitFor({ state: "visible" });
    await this.page.getByRole("link", { name: groupName, exact: true }).waitFor({ state: "visible" });
    await this.page.waitForLoadState("load");
  }

  async isGroupPresent(groupName: string): Promise<boolean> {
    return (await this.page.getByRole("link", { name: groupName, exact: true }).count()) > 0;
  }

  get addAdminLink(): Locator {
    return this.page.getByRole("link", { name: "Add admin", exact: true });
  }

  adminRemoveLink(usernameSubstring: string): Locator {
    return this.page
      .getByRole("row")
      .filter({ has: this.page.getByRole("link", { name: usernameSubstring, exact: false }) })
      .getByRole("link", { name: "Remove", exact: true });
  }
}
