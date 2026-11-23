import { expect, type Locator, type Page } from "@playwright/test";

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
    // Keyboard selection avoids a WebKit pointer click landing during layout/scroll changes.
    // Space is idempotent for a radio: an already selected option stays selected.
    await this.radio(mode).focus();
    await this.radio(mode).press("Space");
    await expect(this.radio(mode)).toBeChecked();
  }

  async addGroup(groupName: string): Promise<void> {
    await this.addGroupButton.click();
    await this.groupCombobox.fill(groupName);
    await this.page.getByRole("option", { name: groupName }).first().click();
  }

  groupCheckbox(groupName: string): Locator {
    return this.root.getByRole("row").filter({ hasText: groupName }).getByRole("checkbox");
  }

  explanation(): Locator {
    return this.root.getByRole("alert");
  }
}
