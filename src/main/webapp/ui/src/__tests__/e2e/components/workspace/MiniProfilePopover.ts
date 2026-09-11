import type { Locator, Page } from "@playwright/test";
import { UserProfilePage } from "@/__tests__/e2e/pageObjects/myrspace/UserProfilePage";

export class MiniProfilePopover {
  readonly root: Locator;

  constructor(private readonly page: Page) {
    this.root = page.locator(".MuiPopover-root").filter({ has: page.getByRole("link", { name: "Open profile" }) });
  }

  async waitUntilVisible(): Promise<void> {
    await this.root.getByRole("link", { name: "Open profile" }).waitFor({ state: "visible" });
  }

  emailLink(email: string): Locator {
    return this.root.getByRole("link", { name: email, exact: true });
  }

  get accountStatus(): Locator {
    return this.root
      .getByRole("row")
      .filter({ has: this.page.getByRole("rowheader", { name: "Account Status" }) })
      .getByRole("cell");
  }

  groupLink(groupName: string): Locator {
    return this.root.getByRole("link", { name: groupName, exact: true });
  }

  get sendMessageLink(): Locator {
    return this.root.getByRole("link", { name: "Send a message" });
  }

  async openProfile(): Promise<UserProfilePage> {
    await this.root.getByRole("link", { name: "Open profile" }).click();
    const profile = new UserProfilePage(this.page);
    await profile.waitUntilLoaded();
    return profile;
  }
}
