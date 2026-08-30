import type { Locator } from "@playwright/test";
import { BasePage } from "../BasePage";

export class PublishedDocumentsPage extends BasePage {
  readonly path = "/record/share/published/manage";

  async waitUntilLoaded(): Promise<void> {
    await this.page.getByRole("heading", { name: "My Group's Published Documents" }).waitFor({ state: "visible" });
  }

  row(name: string): Locator {
    return this.page.getByRole("row").filter({ has: this.page.getByRole("link", { name, exact: true }) });
  }

  publicLink(name: string): Locator {
    return this.row(name).getByRole("link", { name: "public link" });
  }

  private async waitForRow(name: string, timeoutMs = 30_000): Promise<void> {
    const deadline = Date.now() + timeoutMs;
    while ((await this.row(name).count()) === 0) {
      if (Date.now() > deadline) {
        throw new Error(`Published record '${name}' did not appear within ${timeoutMs}ms.`);
      }
      await this.page.reload();
      await this.waitUntilLoaded();
    }
  }

  async publicHref(name: string): Promise<string> {
    await this.waitForRow(name);
    const href = await this.publicLink(name).getAttribute("href");
    if (!href) throw new Error(`Published record '${name}' has no public href.`);
    return href;
  }

  async unpublish(name: string): Promise<void> {
    await this.row(name).getByRole("link", { name: "Unpublish" }).click();
    await this.row(name).waitFor({ state: "hidden" });
  }
}
