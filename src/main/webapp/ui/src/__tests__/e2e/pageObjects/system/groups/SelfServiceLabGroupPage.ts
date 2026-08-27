import { BasePage } from "@/__tests__/e2e/pageObjects/BasePage";

/** PI self-service group creation. */
export class SelfServiceLabGroupPage extends BasePage {
  readonly path = "/selfServiceLabGroup/group/new";

  async createGroup(groupName: string): Promise<number> {
    await this.page.getByRole("button", { name: "Create Group", exact: true }).click();
    const dialog = this.page.getByRole("dialog");
    await dialog.getByRole("textbox", { name: "Group Name" }).fill(groupName);
    await dialog.getByRole("button", { name: "Next" }).click();
    await dialog.getByRole("button", { name: "Next" }).click();
    await dialog.getByRole("button", { name: "Create labGroup" }).click();
    await this.page.waitForURL((url) => url.pathname.includes("/groups/view/"));
    return Number(this.page.url().split("/groups/view/")[1]);
  }
}
