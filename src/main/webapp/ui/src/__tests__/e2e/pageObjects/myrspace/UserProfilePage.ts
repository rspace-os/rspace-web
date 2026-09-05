import type { Locator } from "@playwright/test";
import { ChangePasswordDialogComponent } from "@/__tests__/e2e/components/myrspace/ChangePasswordDialogComponent";
import { BasePage } from "@/__tests__/e2e/pageObjects/BasePage";

export class UserProfilePage extends BasePage {
  readonly path = "/userform";

  private get changePasswordLink(): Locator {
    return this.page.getByRole("link", { name: "Change Password", exact: true });
  }

  async waitUntilLoaded(): Promise<void> {
    await this.changePasswordLink.waitFor({ state: "visible" });
  }

  async openChangePassword(): Promise<ChangePasswordDialogComponent> {
    await this.changePasswordLink.click();
    const dialog = new ChangePasswordDialogComponent(this.page);
    await dialog.waitUntilVisible();
    return dialog;
  }
}
