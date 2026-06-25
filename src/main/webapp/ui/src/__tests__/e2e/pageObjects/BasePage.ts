import type { Page } from "@playwright/test";

/**
 * Deliberately thin: navigation + readiness only. User-level actions and
 * locators belong on the concrete page or on a composed component — never
 * bolted onto this base. Keeping this class minimal prevents page objects
 * from growing into god classes.
 */
export abstract class BasePage {
  constructor(protected readonly page: Page) {}

  abstract readonly path: string;

  async open(): Promise<void> {
    await this.page.goto(this.path);
  }
}
