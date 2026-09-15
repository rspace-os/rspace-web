import type { Locator } from "@playwright/test";
import { AppriseAlertComponent } from "@/__tests__/e2e/components/system/AppriseAlertComponent";
import { BasePage } from "@/__tests__/e2e/pageObjects/BasePage";
import { CommunityAdminPage } from "./CommunityAdminPage";
import { CommunityCreationPage } from "./CommunityCreationPage";

export class CommunitiesPage extends BasePage {
  readonly path = "/community/admin/list";

  static readonly DEFAULT_COMMUNITY_NAME = "All Groups";

  get newCommunityLink(): Locator {
    return this.page.getByRole("link", { name: "New Community", exact: true });
  }

  get removeCommunityLink(): Locator {
    return this.page.getByRole("link", { name: "Remove community", exact: true });
  }

  communityRow(name: string): Locator {
    return this.page.getByRole("row").filter({ has: this.page.getByRole("link", { name, exact: true }) });
  }

  async selectCommunity(name: string): Promise<void> {
    await this.communityRow(name).getByRole("checkbox").check();
  }

  async newCommunity(): Promise<CommunityCreationPage> {
    await this.newCommunityLink.click();
    const page = new CommunityCreationPage(this.page);
    await page.heading.waitFor({ state: "visible" });
    return page;
  }

  async openCommunity(name: string): Promise<CommunityAdminPage> {
    await this.communityRow(name).getByRole("link", { name, exact: true }).click();
    const page = new CommunityAdminPage(this.page);
    await page.waitUntilLoaded(name);
    return page;
  }

  async attemptRemoveDefaultCommunity(): Promise<AppriseAlertComponent> {
    await this.removeCommunityLink.click();
    const alert = new AppriseAlertComponent(this.page);
    await alert.waitUntilVisible();
    return alert;
  }

  async removeSelected(): Promise<void> {
    await this.removeCommunityLink.click();
    const dialog = this.page.getByRole("dialog", { name: "Confirm Deletion" });
    await dialog.waitFor({ state: "visible" });
    await dialog.getByRole("button", { name: "Confirm", exact: true }).click();
    await dialog.waitFor({ state: "hidden" });
  }

  async deleteCommunity(name: string): Promise<void> {
    await this.open();
    await this.selectCommunity(name);
    await this.removeSelected();
  }
}
