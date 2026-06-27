import type { Locator, Page } from "@playwright/test";
import { AttachmentsSection } from "../components/AttachmentsSection";
import { DocumentHeader } from "../components/DocumentHeader";
import { DocumentViewToolbar } from "../components/DocumentViewToolbar";
import { BasePage } from "./BasePage";

/**
 * Structured document in view mode.
 *
 * `path` is the base URL pattern — navigate to a specific document via the
 * workspace or via `WorkspacePage.createBasicDocument()`, not by calling
 * `open()` directly (no fixed document ID here).
 */
export class DocumentPage extends BasePage {
  readonly path = "/workspace/editor/structuredDocument";

  readonly header: DocumentHeader;
  readonly toolbar: DocumentViewToolbar;
  readonly attachments: AttachmentsSection;

  constructor(page: Page) {
    super(page);
    this.header = new DocumentHeader(page);
    this.toolbar = new DocumentViewToolbar(page);
    this.attachments = new AttachmentsSection(page);
  }

  async isLoaded(): Promise<void> {
    await this.page.waitForURL(/\/workspace\/editor\/structuredDocument\//);
    await this.page
      .locator("#viewGreenStatus, #viewAmberStatus, #viewAmberStatusReadPermission, #viewRedStatus")
      .waitFor({ state: "visible" });
  }

  /**
   * View-mode field content. Derives the field id from `td.field-name[id]`
   * and targets `#div_rtf_{fieldId}` directly
   */
  async getFieldViewContent(fieldName: string): Promise<Locator> {
    const fieldTd = this.page.locator("td.field-name").filter({ hasText: fieldName });
    const tdId = await fieldTd.getAttribute("id");
    if (!tdId) {
      throw new Error(
        `getFieldViewContent('${fieldName}'): no td.field-name element with an id attribute found. ` +
          "Verify the field name is spelled exactly as rendered and the document is in view mode.",
      );
    }
    const fieldId = tdId.replace("field-name-", "");
    if (!fieldId) {
      throw new Error(
        `getFieldViewContent('${fieldName}'): id attribute '${tdId}' yielded an empty field id after ` +
          "stripping the 'field-name-' prefix.",
      );
    }
    return this.page.locator(`#div_rtf_${fieldId}`);
  }
}
