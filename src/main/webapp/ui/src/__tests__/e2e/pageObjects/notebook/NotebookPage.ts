import type { Locator, Page } from "@playwright/test";
import { DocumentHeader } from "@/__tests__/e2e/components/document/DocumentHeader";
import { NotebookEntryStrip } from "@/__tests__/e2e/components/notebook/NotebookEntryStrip";
import { NotebookViewToolbar } from "@/__tests__/e2e/components/notebook/NotebookViewToolbar";
import type { RecordInfoDialog } from "@/__tests__/e2e/components/shared/RecordInfoDialog";
import { BasePage } from "../BasePage";
import { DocumentEditorPage } from "../document/DocumentEditorPage";

export class NotebookPage extends BasePage {
  readonly path = "/notebookEditor";

  readonly header: DocumentHeader;
  readonly toolbar: NotebookViewToolbar;
  readonly entryStrip: NotebookEntryStrip;

  constructor(page: Page) {
    super(page);
    this.header = new DocumentHeader(page);
    this.toolbar = new NotebookViewToolbar(page);
    this.entryStrip = new NotebookEntryStrip(page);
  }

  async isLoaded(): Promise<void> {
    await this.page.waitForURL("**/notebookEditor/**");
    await Promise.race([
      this.entryStrip.entryCounter.waitFor({ state: "visible" }),
      this.page.getByText("There are no entries to display.").first().waitFor({ state: "visible" }),
    ]);
  }

  async enterEditMode(): Promise<DocumentEditorPage> {
    await this.toolbar.editButton.click();
    const editor = new DocumentEditorPage(this.page);
    await editor.isLoaded();
    return editor;
  }

  async addEntry(): Promise<DocumentEditorPage> {
    await this.toolbar.createMenu.create("New entry");
    const editor = new DocumentEditorPage(this.page);
    await editor.isLoaded();
    return editor;
  }

  async openRecordInfo(): Promise<RecordInfoDialog> {
    return this.header.openRecordInfo();
  }

  sharingAlert(textSubstring: string): Locator {
    return this.page.getByRole("alert").filter({ hasText: textSubstring });
  }

  async dismissSharingAlert(): Promise<void> {
    await this.page.locator('[data-test-id="toast-close"]').click();
  }
}
