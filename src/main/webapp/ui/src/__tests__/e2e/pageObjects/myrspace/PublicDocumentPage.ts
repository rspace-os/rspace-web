import type { Locator } from "@playwright/test";
import { BasePage } from "../BasePage";

export class PublicDocumentPage extends BasePage {
  readonly path = "/public/publishedView/document";

  async openAt(href: string): Promise<void> {
    await this.page.goto(href);
  }

  title(name: string): Locator {
    return this.page.getByRole("heading", { name, level: 1 });
  }

  summary(summary: string): Locator {
    return this.page.getByRole("heading", { name: summary, level: 3 });
  }

  contact(email: string): Locator {
    return this.page.getByRole("heading", { name: `contact: ${email}`, level: 3 });
  }

  entryCounter(current: number, total: number): Locator {
    return this.page.getByText(`Entry ${current} of ${total}`, { exact: true });
  }

  entryThumbnail(name: string): Locator {
    return this.page.getByTitle(`Name: '${name}'`);
  }

  async robotsMeta(): Promise<string | null> {
    return this.page.evaluate(() => document.querySelector('meta[name="robots"]')?.getAttribute("content") ?? null);
  }

  async descriptionMeta(): Promise<string | null> {
    return this.page.evaluate(
      () => document.querySelector('meta[name="description"]')?.getAttribute("content") ?? null,
    );
  }

  async reload(): Promise<void> {
    await this.page.reload();
  }
}
