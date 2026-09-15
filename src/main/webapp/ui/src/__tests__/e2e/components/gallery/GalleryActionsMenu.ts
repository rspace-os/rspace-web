import type { Locator, Page } from "@playwright/test";

export type GalleryAction =
  | "View"
  | "Open"
  | "Edit"
  | "Duplicate"
  | "Move"
  | "Rename"
  | "Upload New Version"
  | "View Version History"
  | "Download"
  | "Share"
  | "Export"
  | "Move to iRODS"
  | "Move to S3"
  | "Delete";

export class GalleryActionsMenu {
  readonly button: Locator;

  constructor(private readonly page: Page) {
    this.button = page.getByRole("button", { name: "Actions" });
  }

  async open(): Promise<void> {
    await this.button.click();
  }

  async close(): Promise<void> {
    await this.page.keyboard.press("Escape");
  }

  menuItem(action: GalleryAction): Locator {
    return this.page.getByRole("menuitem", { name: action, exact: true });
  }

  async clickAction(action: GalleryAction): Promise<void> {
    await this.menuItem(action).click();
  }

  async isActionEnabled(action: GalleryAction): Promise<boolean> {
    return (await this.menuItem(action).count()) > 0;
  }
}
