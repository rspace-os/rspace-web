import type { Locator, Page } from "@playwright/test";
import {
  ChangeEmailDialogComponent,
  ChangePasswordDialogComponent,
  ConfirmPasswordDialogComponent,
  EditProfileDialogComponent,
  UploadImageDialogComponent,
} from "@/__tests__/e2e/components/myrspace/UserProfileDialogs";
import { AppHeader } from "@/__tests__/e2e/components/shared/AppHeader";
import { BasePage } from "../BasePage";

export class UserProfilePage extends BasePage {
  readonly path = "/userform";
  readonly header: AppHeader;

  constructor(page: Page) {
    super(page);
    this.header = new AppHeader(page);
  }

  async waitUntilLoaded(): Promise<void> {
    await this.page.getByText("Username:", { exact: false }).first().waitFor({ state: "visible" });
  }

  username(username: string): Locator {
    return this.page.getByText(`Username: ${username}`, { exact: false });
  }

  private nameText(name: string): Locator {
    return this.page.getByText(name, { exact: true }).first();
  }

  firstName(name: string): Locator {
    return this.nameText(name);
  }

  lastName(name: string): Locator {
    return this.nameText(name);
  }

  email(email: string): Locator {
    return this.page.getByText(email, { exact: true }).first();
  }

  get profileImage(): Locator {
    return this.page.getByRole("img").nth(1);
  }

  get changePasswordLink(): Locator {
    return this.page.getByRole("link", { name: "Change Password" });
  }

  get apiDisabledMessage(): Locator {
    return this.page.getByText("Access to all API has been disabled by your administrator", { exact: true });
  }

  async isProfileEditable(): Promise<boolean> {
    return (await this.page.getByRole("link", { name: "Edit", exact: true }).count()) > 0;
  }

  groupLink(groupName: string): Locator {
    return this.page.getByRole("link", { name: groupName, exact: true });
  }

  async isApiKeyManagementVisible(): Promise<boolean> {
    const revoke = this.page.getByRole("link", { name: "Revoke key" });
    const regenerate = this.page.getByRole("link", { name: "Regenerate key" });
    const generate = this.page.getByRole("link", { name: "Generate key" });
    return (await revoke.or(regenerate).or(generate).count()) > 0;
  }

  async openChangePassword(): Promise<ChangePasswordDialogComponent> {
    await this.page.getByRole("link", { name: "Change Password" }).click();
    const dialog = new ChangePasswordDialogComponent(this.page);
    await dialog.waitUntilVisible();
    return dialog;
  }

  async openEditProfile(): Promise<EditProfileDialogComponent> {
    await this.page.getByRole("link", { name: "Edit", exact: true }).first().click();
    const dialog = new EditProfileDialogComponent(this.page);
    await dialog.waitUntilVisible();
    return dialog;
  }

  async openChangeEmail(): Promise<ChangeEmailDialogComponent> {
    await this.page.getByRole("link", { name: "Edit", exact: true }).last().click();
    const dialog = new ChangeEmailDialogComponent(this.page);
    await dialog.waitUntilVisible();
    return dialog;
  }

  async openUploadImage(): Promise<UploadImageDialogComponent> {
    await this.page.getByRole("link", { name: "Change Image" }).click();
    const dialog = new UploadImageDialogComponent(this.page);
    await dialog.waitUntilVisible();
    return dialog;
  }

  async revokeApiKey(): Promise<void> {
    await this.page.getByRole("link", { name: "Revoke key" }).click();
    await this.page.getByRole("link", { name: "Revoke key" }).waitFor({ state: "hidden" });
  }

  async regenerateApiKey(password: string): Promise<string> {
    const regenerate = this.page.getByRole("link", { name: "Regenerate key" });
    const generate = this.page.getByRole("link", { name: "Generate key" });
    await regenerate.or(generate).click();
    const dialog = new ConfirmPasswordDialogComponent(this.page);
    await dialog.waitUntilVisible();
    await dialog.submit(password);
    const keyBlock = this.page.getByText("Key:", { exact: false }).first();
    await keyBlock.waitFor({ state: "visible" });
    const text = await keyBlock.innerText();
    return text.split("Key:")[1].split("\n")[0].trim();
  }
}
