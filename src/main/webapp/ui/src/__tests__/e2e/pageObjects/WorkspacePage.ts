import type { Locator, Page } from "@playwright/test";
import { BasePage } from "./BasePage";
import { DocumentEditorPage } from "./DocumentEditorPage";

export class WorkspacePage extends BasePage {
  readonly path = "/workspace";
  readonly createButton: Locator;

  constructor(page: Page) {
    super(page);
    this.createButton = page.getByTestId("create-btn");
  }

  async isLoaded(): Promise<boolean> {
    try {
      await this.createButton.waitFor({ state: "visible" });
      return true;
    } catch {
      return false;
    }
  }

  /**
   * Creates a new basic document via the Create menu and waits until the
   * editor is ready. Always returns in edit mode (`#editingStatus` visible).
   */
  async createBasicDocument(): Promise<DocumentEditorPage> {
    await this.createButton.click();
    await this.page.getByTestId("create-btn-basic-document").click();
    await this.page.waitForURL(/\/workspace\/editor\/structuredDocument\//);
    const editor = new DocumentEditorPage(this.page);
    await editor.isLoaded();
    return editor;
  }
}
