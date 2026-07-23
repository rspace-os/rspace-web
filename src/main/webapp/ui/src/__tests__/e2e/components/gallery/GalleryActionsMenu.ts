import type { Locator, Page } from "@playwright/test";

export type GalleryAction =
  | "View"
  | "Edit"
  | "Duplicate"
  | "Move"
  | "Rename"
  | "Upload New Version"
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
    if (action === "Share") {
      return this.page.getByRole("menuitem", { name: "Share" });
    }
    return this.page.getByRole("menuitem", { name: action, exact: true });
  }

  async clickAction(action: GalleryAction): Promise<void> {
    await this.menuItem(action).click();
  }

  async isActionEnabled(action: GalleryAction): Promise<boolean> {
    const ariaDisabled = await this.menuItem(action).getAttribute("aria-disabled");
    return ariaDisabled !== "true";
  }
}
