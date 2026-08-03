import type { Page } from "@playwright/test";
import { AppHeader } from "@/__tests__/e2e/components/shared/AppHeader";
import { BasePage } from "../BasePage";
import { AuditTrailPage } from "./AuditTrailPage";
import { DeletedItemsPage } from "./DeletedItemsPage";

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
}
