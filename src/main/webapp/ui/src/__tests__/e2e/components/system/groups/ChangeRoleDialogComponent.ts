import type { Locator, Page } from "@playwright/test";

export class ChangeRoleDialogComponent {
  readonly root: Locator;

  constructor(page: Page) {
    this.root = page.getByRole("dialog", { name: "Change User's Role" });
  }

  async waitUntilVisible(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  async makeUser(): Promise<void> {
    await this.root.getByRole("radio", { name: "User", exact: true }).check();
    await this.root.getByRole("button", { name: "OK", exact: true }).click();
    await this.root.waitFor({ state: "hidden" });
  }

  async makeLabAdmin(canViewAllDocuments: boolean): Promise<void> {
    await this.root.getByRole("radio", { name: "Lab Admin", exact: true }).check();
    const permission = canViewAllDocuments
      ? "Lab Admin can view all group's documents."
      : "Lab Admin cannot view all group's documents.";
    await this.root.getByRole("radio", { name: permission }).check();
    await this.root.getByRole("button", { name: "OK", exact: true }).click();
    await this.root.waitFor({ state: "hidden" });
  }
}
