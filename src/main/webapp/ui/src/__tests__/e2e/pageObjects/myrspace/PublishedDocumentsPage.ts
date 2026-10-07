import { expect, type Locator } from "@playwright/test";
import { BasePage } from "../BasePage";
import { rowWithLink } from "../rowHelpers";

export class PublishedDocumentsPage extends BasePage {
  readonly path = "/record/share/published/manage";

  async waitUntilLoaded(): Promise<void> {
    await this.page.getByRole("heading", { name: "My Group's Published Documents" }).waitFor({ state: "visible" });
  }

  get resultsTable(): Locator {
    return this.page.getByRole("table");
  }

  row(name: string): Locator {
    return rowWithLink(this.resultsTable, name);
  }

  publicLink(name: string): Locator {
    return this.row(name).getByRole("link", { name: "public link" });
  }

  private async waitForRow(name: string, timeoutMs = 30_000): Promise<void> {
    await expect
      .poll(
        async () => {
          await this.page.reload();
          await this.waitUntilLoaded();
          return this.row(name).count();
        },
        { timeout: timeoutMs },
      )
      .toBeGreaterThan(0);
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
