import type { Page } from "@playwright/test";

export abstract class BasePage {
  constructor(protected readonly page: Page) {}

  abstract readonly path: string;

  async open(): Promise<void> {
    await this.page.goto(this.path);
  }
}
