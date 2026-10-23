import type { Page } from "@playwright/test";

export abstract class BasePage {
  constructor(protected readonly page: Page) {}

  get browserPage(): Page {
    return this.page;
  }

  abstract readonly path: string;

  async open(): Promise<void> {
    await this.page.goto(this.path);
  }
}
