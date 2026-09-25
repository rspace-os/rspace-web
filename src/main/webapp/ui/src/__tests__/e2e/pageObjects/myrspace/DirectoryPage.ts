import { BasePage } from "../BasePage";
import { UserProfilePage } from "./UserProfilePage";

export class DirectoryPage extends BasePage {
  readonly path = "/directory";

  async search(query: string): Promise<void> {
    await this.page.getByRole("textbox", { name: "Search" }).fill(query);
    await this.page.getByRole("button", { name: "Search" }).click();
    await this.page.getByRole("button", { name: "Clear search" }).waitFor({ state: "visible" });
  }

  async openUserProfile(username: string): Promise<UserProfilePage> {
    await this.search(username);
    await this.page.getByRole("link", { name: username, exact: true }).click();
    const profile = new UserProfilePage(this.page);
    await profile.waitUntilLoaded();
    return profile;
  }
}
