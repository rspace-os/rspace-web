import { expect, type Locator, type Page } from "@playwright/test";

export interface BatchUserRowFields {
  firstName: Locator;
  lastName: Locator;
  email: Locator;
  username: Locator;
  password: Locator;
  status: Locator;
  remove: Locator;
}

export class BatchUserRegistrationComponent {
  constructor(private readonly page: Page) {}

  async selectCsvInputMode(): Promise<void> {
    await this.page.getByRole("button", { name: "CSV Input", exact: true }).click();
  }

  async selectManualCreationMode(): Promise<void> {
    await this.page.getByRole("button", { name: "Manual creation", exact: true }).click();
  }

  async addUserRow(): Promise<void> {
    const before = await this.userRowCount();
    await this.page.getByRole("link", { name: "Add user...", exact: true }).click();
    await expect(this.usersDataRows()).toHaveCount(before + 1);
  }

  async uploadCsvFile(csvContent: string): Promise<void> {
    await this.uploadViaDialog({ name: "batch.csv", mimeType: "text/csv", buffer: Buffer.from(csvContent) });
  }

  async uploadCsvFileFromPath(filePath: string): Promise<void> {
    await this.uploadViaDialog(filePath);
  }

  private async uploadViaDialog(files: Parameters<Locator["setInputFiles"]>[0]): Promise<void> {
    await this.page.getByRole("button", { name: "Upload CSV file", exact: true }).click();
    await this.page.locator("#csvFileInput").setInputFiles(files);
    await this.page.getByRole("button", { name: "Upload", exact: true }).click();
    await this.page.getByRole("heading", { name: "Users to create", exact: true }).waitFor({ state: "visible" });
  }

  async loadCsv(csvContent: string): Promise<void> {
    await this.page.locator("#csvInputContentArea").fill(csvContent);
    await this.page.getByRole("button", { name: "Load CSV content", exact: true }).click();
    await this.page.getByRole("heading", { name: "Users to create", exact: true }).waitFor({ state: "visible" });
  }

  get usersToCreateTable(): Locator {
    return this.page
      .getByRole("table")
      .filter({ has: this.page.getByRole("columnheader", { name: "First Name", exact: true }) });
  }

  get groupsToCreateTable(): Locator {
    return this.page
      .getByRole("table")
      .filter({ has: this.page.getByRole("columnheader", { name: "Members", exact: true }) });
  }

  userRow(username: string): Locator {
    return this.usersToCreateTable.getByRole("row", { name: username });
  }

  groupRow(groupName: string): Locator {
    return this.groupsToCreateTable.getByRole("row", { name: groupName });
  }

  get createAllButton(): Locator {
    return this.page.getByRole("button", { name: "Create All", exact: true });
  }

  private usersDataRows(): Locator {
    return this.usersToCreateTable.getByRole("rowgroup").nth(1).getByRole("row");
  }

  userRowAt(index: number): BatchUserRowFields {
    const row = this.usersDataRows().nth(index);
    const cell = (n: number) => row.getByRole("cell").nth(n);
    return {
      firstName: cell(0).getByRole("textbox"),
      lastName: cell(1).getByRole("textbox"),
      email: cell(2).getByRole("textbox"),
      username: cell(4).getByRole("textbox"),
      password: cell(5).getByRole("textbox"),
      status: cell(6),
      remove: row.getByRole("link", { name: "Remove", exact: true }),
    };
  }

  async userRowCount(): Promise<number> {
    return this.usersDataRows().count();
  }

  async removeUserRowAt(index: number): Promise<void> {
    const before = await this.userRowCount();
    await this.userRowAt(index).remove.click();
    await expect(this.usersDataRows()).toHaveCount(before - 1);
  }

  async clickCreateAll(): Promise<void> {
    await this.createAllButton.click();
  }

  async validationErrorCount(): Promise<number> {
    const rows = await this.usersDataRows().all();
    const statuses = await Promise.all(rows.map((row) => row.getByRole("cell").nth(6).innerText()));
    return statuses.filter((text) => text.trim().length > 0).length;
  }
}
