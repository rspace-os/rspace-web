import type { Locator, Page } from "@playwright/test";

export type GroupFormPermission = "NONE" | "READ" | "WRITE";
export type WorldFormPermission = Exclude<GroupFormPermission, "WRITE">;

export class FormAccessDialogComponent {
  readonly root: Locator;

  constructor(page: Page) {
    this.root = page.getByRole("dialog", { name: "Configure access to Forms" });
  }

  async waitUntilVisible(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  private permissionRow(name: "Group" | "World"): Locator {
    return this.root.getByRole("row").filter({
      has: this.root.getByRole("cell", { name, exact: true }),
    });
  }

  async setGroup(permission: GroupFormPermission): Promise<void> {
    await this.permissionRow("Group").getByRole("radio", { name: permission, exact: true }).check();
  }

  async setWorld(permission: WorldFormPermission): Promise<void> {
    await this.permissionRow("World").getByRole("radio", { name: permission, exact: true }).check();
  }

  async confirm(): Promise<void> {
    await this.root.getByRole("button", { name: "OK" }).click();
    await this.root.waitFor({ state: "hidden" });
  }
}
