import { BasePage } from "@/__tests__/e2e/pageObjects/BasePage";

/** Self-service Project Group creation. */
export class ProjectGroupPage extends BasePage {
  readonly path = "/projectGroup/newGroupForm";

  async createGroup(groupName: string): Promise<number> {
    await this.page.getByRole("button", { name: "Create Group", exact: true }).click();
    const dialog = this.page.getByRole("dialog");
    await dialog.getByRole("textbox", { name: "Group Name" }).fill(groupName);
    await dialog.getByRole("button", { name: "Next" }).click();
    await dialog.getByRole("button", { name: "Next" }).click();
    await dialog.getByRole("button", { name: "Create Project Group" }).click();
    await this.page.waitForURL((url) => url.pathname.includes("/groups/view/"));
    return Number(this.page.url().split("/groups/view/")[1]);
  }
}
