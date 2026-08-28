import { type Download, expect, type Locator } from "@playwright/test";
import { OperateAsDialogComponent } from "@/__tests__/e2e/components/system/operate-as/OperateAsDialogComponent";
import { GrantRevokePiRoleDialogComponent } from "@/__tests__/e2e/components/system/users/GrantRevokePiRoleDialogComponent";
import { TagUsersDialogComponent } from "@/__tests__/e2e/components/system/users/TagUsersDialogComponent";
import { BasePage } from "@/__tests__/e2e/pageObjects/BasePage";

export class SystemUsersPage extends BasePage {
  readonly path = "/system";

  override async open(): Promise<void> {
    await super.open();
    await this.dataRows().first().waitFor({ state: "visible" });
  }

  private async waitForJsonList(query: string): Promise<void> {
    await this.page.waitForResponse((res) => {
      const url = new URL(res.url());
      return url.pathname === "/system/ajax/jsonList" && url.searchParams.get("allFields") === (query || null);
    });
  }

  async search(query: string): Promise<void> {
    const box = this.page.getByRole("searchbox", { name: "Search users" });
    await box.fill(query);
    await Promise.all([this.waitForJsonList(query), box.press("Enter")]);
    if (query) {
      await this.userRow(query).waitFor({ state: "visible" });
    }
  }

  async searchByTag(tag: string): Promise<void> {
    const box = this.page.getByRole("searchbox", { name: "Search users" });
    await box.fill(tag);
    await Promise.all([this.waitForJsonList(tag), box.press("Enter")]);
  }

  userRow(text: string): Locator {
    return this.page.getByRole("grid", { name: "users" }).getByRole("row").filter({ hasText: text });
  }

  async lastLoginFor(username: string): Promise<string> {
    return this.userRow(username).getByRole("gridcell").nth(5).innerText();
  }

  async availableSeats(): Promise<number> {
    return this.readSummaryCount(this.page.getByRole("row", { name: "Available Seats" }).getByRole("cell").last());
  }

  async totalUsers(): Promise<number> {
    const row = this.page.getByRole("row").filter({ has: this.page.getByText("Total Users", { exact: true }) });
    return this.readSummaryCount(row.getByRole("cell").last());
  }

  async systemAdmins(): Promise<number> {
    return this.readSummaryCount(this.page.getByRole("row", { name: "System Admins" }).getByRole("cell").last());
  }

  private async exportViaMenuItem(menuItemName: string): Promise<Download> {
    await this.page.getByRole("button", { name: "Export", exact: true }).click();
    const [download] = await Promise.all([
      this.page.waitForEvent("download"),
      this.page.getByRole("menuitem", { name: menuItemName, exact: true }).click(),
    ]);
    return download;
  }

  async exportSelectedRowsToCsv(): Promise<Download> {
    return this.exportViaMenuItem("Export selected rows to CSV");
  }

  async exportThisPageToCsv(): Promise<Download> {
    return this.exportViaMenuItem("Export this page of rows to CSV");
  }

  private async readSummaryCount(cell: Locator): Promise<number> {
    await expect(async () => {
      expect(Number.isNaN(Number(await cell.innerText()))).toBe(false);
    }).toPass({ timeout: 5000 });
    return Number(await cell.innerText());
  }

  async searchExpectingNoResults(query: string): Promise<void> {
    const box = this.page.getByRole("searchbox", { name: "Search users" });
    await box.fill(query);
    await box.press("Enter");
    await expect(this.userRow(query)).toHaveCount(0);
  }

  async selectUser(username: string): Promise<void> {
    await this.search(username);
    await this.userRow(username).getByRole("checkbox", { name: "Select row", exact: true }).check();
  }

  private async openActionsMenuFor(username: string): Promise<void> {
    await this.selectUser(username);
    await this.page.getByRole("button", { name: "Actions", exact: false }).click();
  }

  async grantPiRole(username: string, sysadminPassword: string): Promise<void> {
    await this.openActionsMenuFor(username);
    await this.page.getByRole("menuitem", { name: "Grant PI role", exact: true }).click();
    const dialog = new GrantRevokePiRoleDialogComponent(this.page);
    await dialog.waitUntilVisible();
    await dialog.grant(sysadminPassword);
  }

  async revokePiRole(username: string, sysadminPassword: string): Promise<void> {
    await this.openActionsMenuFor(username);
    await this.page.getByRole("menuitem", { name: "Revoke PI role", exact: true }).click();
    const dialog = new GrantRevokePiRoleDialogComponent(this.page);
    await dialog.waitUntilVisible();
    await dialog.revoke(sysadminPassword);
  }

  async attemptRevokePiRole(username: string, sysadminPassword: string): Promise<GrantRevokePiRoleDialogComponent> {
    await this.openActionsMenuFor(username);
    await this.page.getByRole("menuitem", { name: "Revoke PI role", exact: true }).click();
    const dialog = new GrantRevokePiRoleDialogComponent(this.page);
    await dialog.waitUntilVisible();
    await dialog.attemptRevoke(sysadminPassword);
    return dialog;
  }

  async clickOperateAs(): Promise<OperateAsDialogComponent> {
    await this.page.getByRole("link", { name: "Operate As", exact: true }).click();
    const dialog = new OperateAsDialogComponent(this.page);
    await dialog.waitUntilVisible();
    return dialog;
  }

  async deleteUser(username: string): Promise<void> {
    await this.openActionsMenuFor(username);
    await this.page.getByRole("menuitem", { name: "Delete", exact: true }).click();
    const dialog = this.page.getByRole("dialog", { name: "Deletion Confirmation" });
    await dialog.getByRole("textbox", { name: "Username", exact: true }).fill(username);
    const confirmButton = dialog
      .getByRole("button", { name: "Delete", exact: true })
      .or(dialog.getByRole("button", { name: "Transfer" }));
    await confirmButton.click();
    await dialog.waitFor({ state: "hidden" });
  }

  async openActionsMenu(username: string): Promise<Locator> {
    await this.openActionsMenuFor(username);
    return this.page.getByRole("menu");
  }

  private dataRows(): Locator {
    return this.page
      .getByRole("grid", { name: "users" })
      .getByRole("row")
      .filter({ has: this.page.getByRole("gridcell") });
  }

  async rowCount(): Promise<number> {
    return this.dataRows().count();
  }

  async columnValues(field: string): Promise<string[]> {
    return this.page
      .getByRole("grid", { name: "users" })
      .locator(`[role="gridcell"][data-field="${field}"]`)
      .allInnerTexts();
  }

  private async sortColumn(columnHeader: string, menuItemName: "Sort by ASC" | "Sort by DESC"): Promise<void> {
    const firstRow = this.dataRows().first();
    const before = await firstRow.innerText();
    await this.page.getByRole("button", { name: `${columnHeader} column menu`, exact: true }).click();
    await this.page
      .getByRole("menu", { name: `${columnHeader} column menu` })
      .getByRole("menuitem", { name: menuItemName, exact: true })
      .click();
    await expect(firstRow).not.toHaveText(before);
  }

  async sortColumnAscending(columnHeader: string): Promise<void> {
    await this.sortColumn(columnHeader, "Sort by ASC");
  }

  async sortColumnDescending(columnHeader: string): Promise<void> {
    await this.sortColumn(columnHeader, "Sort by DESC");
  }

  private get paginationSummary(): Locator {
    return this.page.locator(".MuiTablePagination-displayedRows");
  }

  async goToNextPage(): Promise<void> {
    const before = await this.paginationSummary.innerText();
    await this.page.getByRole("button", { name: "Go to next page" }).click();
    await expect(this.paginationSummary).not.toHaveText(before);
  }

  async goToPreviousPage(): Promise<void> {
    const before = await this.paginationSummary.innerText();
    await this.page.getByRole("button", { name: "Go to previous page" }).click();
    await expect(this.paginationSummary).not.toHaveText(before);
  }

  async toggleColumn(name: string, visible: boolean): Promise<void> {
    await this.page.getByRole("button", { name: "Columns", exact: true }).click();
    const checkbox = this.page.getByRole("checkbox", { name, exact: true });
    if ((await checkbox.isChecked()) !== visible) {
      await checkbox.click();
    }
    await this.page.keyboard.press("Escape");
  }

  async filterByTag(tag: string): Promise<void> {
    const before = await this.paginationSummary.innerText();
    await this.page.getByRole("button", { name: "Filter users", exact: true }).click();
    const tagsSwitch = this.page.getByRole("switch", { name: "Tags", exact: true });
    if (!(await tagsSwitch.isChecked())) {
      await tagsSwitch.click();
    }
    await this.page.getByRole("button", { name: "Add Tag", exact: true }).click();
    await this.page.getByRole("combobox", { name: "Filter suggested tags" }).fill(tag);
    await this.page.getByRole("option", { name: tag, exact: true }).click();
    await expect(this.paginationSummary).not.toHaveText(before);
    await this.page.keyboard.press("Escape");
  }

  tagsShowListButton(username: string): Locator {
    return this.userRow(username).getByRole("button", { name: "tag(s). Show list of tags.", exact: false });
  }

  async showTagsList(username: string): Promise<Locator> {
    await this.tagsShowListButton(username).click();
    const dialog = this.page.getByRole("dialog", { name: "Tags", exact: true });
    await dialog.waitFor({ state: "visible" });
    return dialog;
  }

  async closeTagsList(): Promise<void> {
    await this.page.keyboard.press("Escape");
  }

  async openTagsDialog(username: string): Promise<TagUsersDialogComponent> {
    await this.openActionsMenuFor(username);
    await this.page.getByRole("menuitem", { name: "Add/Remove Tags", exact: true }).click();
    const dialog = new TagUsersDialogComponent(this.page);
    await dialog.waitUntilVisible();
    return dialog;
  }

  async unlockUser(username: string): Promise<void> {
    await this.openActionsMenuFor(username);
    await this.page.getByRole("menuitem", { name: "Unlock", exact: true }).click();
  }
}
