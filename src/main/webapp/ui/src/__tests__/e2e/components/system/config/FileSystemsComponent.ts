import type { Locator, Page } from "@playwright/test";
import { AppriseAlertComponent } from "@/__tests__/e2e/components/system/AppriseAlertComponent";
import { responseTo } from "@/__tests__/e2e/responses";
import { type FileSystemForm, FileSystemFormComponent } from "./FileSystemFormComponent";

export class FileSystemsComponent {
  readonly form: FileSystemFormComponent;
  private readonly root: Locator;
  private readonly table: Locator;
  private readonly emptyMessage: Locator;
  private readonly addNewButton: Locator;
  private readonly alert: AppriseAlertComponent;

  constructor(private readonly page: Page) {
    // The panel is injected into the legacy #mainArea with no accessible container of its own.
    this.root = page.locator("#mainArea");
    this.table = this.root
      .getByRole("table")
      .filter({ has: page.getByRole("columnheader", { name: "Authentication Type" }) });
    this.emptyMessage = this.root.getByText("No File System configured yet.");
    this.addNewButton = this.root.getByRole("button", { name: "Add new File System" });
    this.form = new FileSystemFormComponent(page);
    this.alert = new AppriseAlertComponent(page);
  }

  async waitUntilLoaded(): Promise<void> {
    await this.emptyMessage.or(this.table).filter({ visible: true }).waitFor();
  }

  row(name: string): Locator {
    return this.table.getByRole("row").filter({ has: this.page.getByRole("cell", { name, exact: true }) });
  }

  /** Name, URL, Enabled, Client Type and Authentication Type, in table order. */
  rowCells(name: string): Locator {
    return this.row(name).getByRole("cell");
  }

  async openNewForm(): Promise<FileSystemFormComponent> {
    await this.addNewButton.click();
    await this.form.addHeading.waitFor();
    return this.form;
  }

  async add(settings: FileSystemForm): Promise<void> {
    const form = await this.openNewForm();
    await form.fill(settings);
    await this.saveForm();
    await this.row(settings.name).waitFor();
  }

  async openDetails(name: string): Promise<FileSystemFormComponent> {
    await this.row(name).getByRole("button", { name: "Details" }).click();
    await this.form.detailsHeading.waitFor();
    return this.form;
  }

  async saveForm(): Promise<void> {
    await this.mutate("/system/netfilesystem/save", () => this.form.submit());
    await this.waitForReload(this.form.submitButton);
  }

  async delete(name: string): Promise<void> {
    const row = this.row(name);
    await row.getByRole("button", { name: "Delete" }).click();
    await this.alert.waitUntilVisible();
    await this.mutate("/system/netfilesystem/delete", () => this.alert.confirm(), "true");
    await this.waitForReload(row);
  }

  private async mutate(path: string, trigger: () => Promise<void>, expectedBody?: string): Promise<void> {
    const response = await responseTo(this.page, "POST", ({ pathname }) => pathname === path, trigger);
    if (expectedBody !== undefined) {
      const body = await response.text();
      if (body.trim() !== expectedBody) {
        throw new Error(`POST ${path} was rejected: ${body.slice(0, 300)}`);
      }
    }
  }

  private async waitForReload(previous: Locator): Promise<void> {
    await previous.waitFor({ state: "hidden" });
    await this.waitUntilLoaded();
  }
}
