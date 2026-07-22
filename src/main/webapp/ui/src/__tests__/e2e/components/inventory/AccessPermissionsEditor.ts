import type { Locator, Page } from "@playwright/test";

export type SharingMode = "Owner's groups" | "Explicit access list" | "Only the Owner";

export class AccessPermissionsEditor {
  private readonly addGroupButton: Locator;
  private readonly groupCombobox: Locator;

  constructor(
    private readonly page: Page,
    private readonly root: Locator,
  ) {
    this.addGroupButton = this.root.getByRole("button", { name: "Add group" });
    this.groupCombobox = page.getByRole("combobox");
  }

  radio(mode: SharingMode): Locator {
    return this.root.getByRole("radio", { name: mode });
  }

  async setSharingMode(mode: SharingMode): Promise<void> {
    await this.radio(mode).click();
  }

  async addGroup(groupName: string): Promise<void> {
    await this.addGroupButton.click();
    await this.groupCombobox.fill(groupName);
    await this.page.getByRole("option", { name: groupName }).first().click();
  }

  groupCheckbox(groupName: string): Locator {
    return this.root.getByRole("row").filter({ hasText: groupName }).getByRole("checkbox");
  }
}
