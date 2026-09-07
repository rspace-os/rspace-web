import type { Page } from "@playwright/test";
import { AppHeader } from "@/__tests__/e2e/components/shared/AppHeader";
import { BasePage } from "../BasePage";
import { AuditTrailPage } from "./AuditTrailPage";
import { CreateFormPage } from "./CreateFormPage";
import { DeletedItemsPage } from "./DeletedItemsPage";
import { ManageFormsPage } from "./ManageFormsPage";

export class MyRSpacePage extends BasePage {
  readonly path = "/admin";
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

  async navigateToCreateFormPage(): Promise<CreateFormPage> {
    await this.page.getByRole("link", { name: "Create Form", exact: true }).click();
    await this.page.waitForURL("**/workspace/editor/form/**");
    const formPage = new CreateFormPage(this.page);
    await formPage.isLoaded();
    return formPage;
  }

  async navigateToManageFormsPage(): Promise<ManageFormsPage> {
    await this.page.getByRole("link", { name: "Manage Forms", exact: true }).click();
    await this.page.waitForURL("**/workspace/editor/form/list**");
    const manageForms = new ManageFormsPage(this.page);
    await manageForms.isLoaded();
    return manageForms;
  }
}
