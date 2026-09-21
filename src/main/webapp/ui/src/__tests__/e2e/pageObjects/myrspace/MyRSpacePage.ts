import type { Page } from "@playwright/test";
import { AppHeader } from "@/__tests__/e2e/components/shared/AppHeader";
import { BasePage } from "../BasePage";
import { AuditTrailPage } from "./AuditTrailPage";
import { CreateFormPage } from "./CreateFormPage";
import { DeletedItemsPage } from "./DeletedItemsPage";
import { DirectoryPage } from "./DirectoryPage";
import { ExportImportPage } from "./ExportImportPage";
import { ManageFormsPage } from "./ManageFormsPage";
import { PublishedDocumentsPage } from "./PublishedDocumentsPage";
import { SharedDocumentsPage } from "./SharedDocumentsPage";
import { UserProfilePage } from "./UserProfilePage";

export class MyRSpacePage extends BasePage {
  readonly path = "/groups/viewPIGroup";
  readonly header: AppHeader;

  constructor(page: Page) {
    super(page);
    this.header = new AppHeader(page);
  }

  async openAuditTrail(): Promise<AuditTrailPage> {
    await this.page.getByRole("link", { name: "Auditing" }).click();
    await this.page.waitForURL("**/audit/auditing**");
    return new AuditTrailPage(this.page);
  }

  async openDeletedItems(): Promise<DeletedItemsPage> {
    await this.page.getByRole("link", { name: "Deleted Items" }).click();
    await this.page.waitForURL("**/workspace/trash/list**");
    return new DeletedItemsPage(this.page);
  }

  async openProfile(): Promise<UserProfilePage> {
    await this.page.getByRole("link", { name: "My Profile" }).click();
    const page = new UserProfilePage(this.page);
    await page.waitUntilLoaded();
    return page;
  }

  async openDirectory(): Promise<DirectoryPage> {
    await this.page.getByRole("link", { name: "Directory" }).click();
    await this.page.waitForURL((url) => url.pathname === "/directory");
    return new DirectoryPage(this.page);
  }

  async openCreateForm(): Promise<CreateFormPage> {
    await this.page.getByRole("link", { name: "Create Form" }).click();
    const form = new CreateFormPage(this.page);
    await form.waitUntilLoaded();
    return form;
  }

  async openManageForms(): Promise<ManageFormsPage> {
    await this.page.getByRole("link", { name: "Manage Forms" }).click();
    const forms = new ManageFormsPage(this.page);
    await forms.waitUntilLoaded();
    return forms;
  }

  async openSharedDocuments(): Promise<SharedDocumentsPage> {
    await this.page.getByRole("link", { name: "Shared Documents" }).click();
    const shared = new SharedDocumentsPage(this.page);
    await shared.waitUntilLoaded();
    return shared;
  }

  async openPublishedDocuments(): Promise<PublishedDocumentsPage> {
    await this.page.getByRole("link", { name: "My Group's Published Documents" }).click();
    const published = new PublishedDocumentsPage(this.page);
    await published.waitUntilLoaded();
    return published;
  }

  async openExportImport(): Promise<ExportImportPage> {
    await this.page.getByRole("link", { name: "Export - Import" }).click();
    const page = new ExportImportPage(this.page);
    await page.waitUntilLoaded();
    return page;
  }
}
