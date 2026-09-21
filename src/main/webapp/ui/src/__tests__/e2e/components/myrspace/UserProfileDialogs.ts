import type { Locator, Page } from "@playwright/test";

abstract class ProfileDialog {
  readonly root: Locator;
  protected readonly page: Page;

  protected constructor(page: Page, name: string) {
    this.page = page;
    this.root = page.getByRole("dialog", { name });
  }

  async waitUntilVisible(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  async cancel(): Promise<void> {
    await this.root.getByRole("button", { name: "Cancel" }).click();
    await this.root.waitFor({ state: "hidden" });
  }
}

export class ChangePasswordDialogComponent extends ProfileDialog {
  constructor(page: Page) {
    super(page, "Change Password");
  }

  // legacy jQuery message area.
  get message(): Locator {
    return this.root.locator("#msgAreaPassword");
  }

  async submit(current: string, password: string, confirmation: string): Promise<void> {
    await this.root.getByRole("textbox", { name: "Enter current password" }).fill(current);
    await this.root.getByRole("textbox", { name: "Enter new password" }).fill(password);
    await this.root.getByRole("textbox", { name: "Confirm new password" }).fill(confirmation);
    await this.root.getByRole("button", { name: "Save" }).click();
  }
}

export class ConfirmPasswordDialogComponent extends ProfileDialog {
  constructor(page: Page) {
    super(page, "Confirm Password");
  }

  async submit(password: string): Promise<void> {
    await this.root.getByRole("textbox", { name: "Please confirm your password" }).fill(password);
    await this.root.getByRole("button", { name: "OK" }).click();
    await this.root.waitFor({ state: "hidden" });
  }
}

export class ChangeEmailDialogComponent extends ProfileDialog {
  constructor(page: Page) {
    super(page, "Change Email");
  }

  async submit(email: string, password: string): Promise<void> {
    await this.root.getByRole("textbox", { name: "New e-mail", exact: true }).fill(email);
    await this.root.getByRole("textbox", { name: "Confirm new e-mail" }).fill(email);
    await this.root.getByRole("textbox", { name: "Enter password" }).fill(password);
    await this.root.getByRole("button", { name: "Save" }).click();
  }
}

export class EditProfileDialogComponent extends ProfileDialog {
  constructor(page: Page) {
    super(page, "Edit Profile");
  }

  async submit(firstName: string, lastName: string): Promise<void> {
    await this.root.getByRole("textbox", { name: "First Name" }).fill(firstName);
    await this.root.getByRole("textbox", { name: "Last Name" }).fill(lastName);
    await this.root.getByRole("button", { name: "Save" }).click();
    await this.root.waitFor({ state: "hidden" });
  }
}

export class UploadImageDialogComponent extends ProfileDialog {
  constructor(page: Page) {
    super(page, "Upload Image");
  }

  get message(): Locator {
    return this.root.locator("#msgAreaImage");
  }

  async upload(file: string | { name: string; mimeType: string; buffer: Buffer }): Promise<void> {
    await this.root.getByRole("button", { name: "Choose a file" }).setInputFiles(file);
    const response = await Promise.all([
      this.page.waitForResponse((candidate) => new URL(candidate.url()).pathname === "/userform/profileImage/upload"),
      this.root.getByRole("button", { name: "Upload" }).click(),
    ]).then(([candidate]) => candidate);
    if (!response.ok()) {
      throw new Error(`Profile image upload failed with HTTP ${response.status()}.`);
    }
    await this.page.waitForLoadState("domcontentloaded");
  }

  async uploadExpectingValidationError(
    file: string | { name: string; mimeType: string; buffer: Buffer },
  ): Promise<void> {
    await this.root.getByRole("button", { name: "Choose a file" }).setInputFiles(file);
    await this.root.getByRole("button", { name: "Upload" }).click();
  }
}
