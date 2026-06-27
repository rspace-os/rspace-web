import type { Locator, Page } from "@playwright/test";
import { DocumentToolbar } from "../components/DocumentToolbar";
import { PubchemDialogComponent } from "../components/pubchem/PubchemDialogComponent";
import { TinyMceEditor } from "../components/TinyMceEditor";
import { DocumentPage } from "./DocumentPage";

/**
 * Structured document in edit mode. Extends DocumentPage and overrides
 * `isLoaded()` to wait for `#editingStatus`.
 *
 * `editToolbar` provides save/cancel actions.
 * `getField(name)` activates a TinyMCE field and returns an editor handle.
 */
export class DocumentEditorPage extends DocumentPage {
  readonly editToolbar: DocumentToolbar;
  readonly pubchemDialog: PubchemDialogComponent;

  constructor(page: Page) {
    super(page);
    this.editToolbar = new DocumentToolbar(page);
    this.pubchemDialog = new PubchemDialogComponent(page);
  }

  override async isLoaded(): Promise<void> {
    await this.page.waitForURL(/\/workspace\/editor\/structuredDocument\//);
    await this.page.locator("#editingStatus").waitFor({ state: "visible" });
    // TinyMCE plugins (including Chemistry) finish registering after
    // #editingStatus appears. Wait for at least one editor container before
    // continuing so the assertion window covers plugin toolbar rendering.
    await this.page.locator(".tox-tinymce").first().waitFor({ state: "visible" });
  }

  /**
   * Returns a handle for the named field's TinyMCE instance.
   *
   * The editorId is derived from the `td.field-name` element's id attribute
   * ("field-name-{fieldId}") rather than by dblclicking — TinyMCE is already
   * initialised when a document opens in edit mode.
   *
   * For a basic document the single field is named "New List of Materials".
   */
  async getField(fieldName: string): Promise<TinyMceEditor> {
    const fieldTd = this.page.locator("td.field-name").filter({ hasText: fieldName });
    const tdId = await fieldTd.getAttribute("id");
    if (!tdId) {
      throw new Error(
        `getField('${fieldName}'): no td.field-name element with an id attribute found. ` +
          "Verify the field name is spelled exactly as rendered and the document is in edit mode.",
      );
    }
    const fieldId = tdId.replace("field-name-", "");
    if (!fieldId) {
      throw new Error(
        `getField('${fieldName}'): id attribute '${tdId}' yielded an empty field id after ` +
          "stripping the 'field-name-' prefix.",
      );
    }
    const editorId = `rtf_${fieldId}`;
    await this.page.locator(`iframe#${editorId}_ifr`).waitFor({ state: "visible" });
    return new TinyMceEditor(this.page, editorId).waitForReady();
  }

  async saveAndView(): Promise<DocumentPage> {
    await this.editToolbar.saveAndView();
    const viewPage = new DocumentPage(this.page);
    await viewPage.isLoaded();
    return viewPage;
  }

  get pubchemToolbarButton(): Locator {
    return this.page.getByRole("button", { name: "Insert PubChem Compound" });
  }

  async openPubchemDialog(): Promise<PubchemDialogComponent> {
    await this.pubchemToolbarButton.click();
    await this.pubchemDialog.waitForOpen();
    return this.pubchemDialog;
  }
}
