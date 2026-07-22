import { BasePage } from "../BasePage";
import { AuditTrailPage } from "./AuditTrailPage";

export class MyRSpacePage extends BasePage {
  readonly path = "/admin";

  async openAuditTrail(): Promise<AuditTrailPage> {
    await this.page.getByRole("link", { name: "Auditing" }).click();
    await this.page.waitForURL("**/audit/auditing**");
    return new AuditTrailPage(this.page);
  }
}
