import type { Locator, Page } from "@playwright/test";
import { AttachmentsSection } from "@/__tests__/e2e/components/document/AttachmentsSection";
import { DocumentHeader } from "@/__tests__/e2e/components/document/DocumentHeader";
import { DocumentViewToolbar } from "@/__tests__/e2e/components/document/DocumentViewToolbar";
import { BasePage } from "../BasePage";

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
    // All four status divs exist in the DOM simultaneously — only one is
    // ever `display`ed at a time. Without `:visible`,
    // waitFor({state: "visible"}) strict-mode-violates on the other three.
    await this.page
      .locator(
        "#viewGreenStatus:visible, #viewAmberStatus:visible, #viewAmberStatusReadPermission:visible, #viewRedStatus:visible",
      )
      .waitFor({ state: "visible" });
  }

  /**
   * View-mode field content. Derives the field id from `td.field-name[id]`
   * and targets `#div_rtf_{fieldId}` directly.
   *
   * Only correct for TinyMCE text fields (basic/experiment/ontology forms).
   * For a form with other field types (date, choice, radio, number, string,
   * time), use `getStructuredFieldValue` instead.
   */
  async getFieldViewContent(fieldName: string, index = 0): Promise<Locator> {
    const fieldTd = this.page.locator("td.field-name").filter({ hasText: fieldName }).nth(index);
    const tdId = await fieldTd.getAttribute("id");
    if (!tdId) {
      throw new Error(
        `getFieldViewContent('${fieldName}', ${index}): no td.field-name element with an id attribute found. ` +
          "Verify the field name is spelled exactly as rendered and the document is in view mode.",
      );
    }
    const fieldId = tdId.replace("field-name-", "");
    if (!fieldId) {
      throw new Error(
        `getFieldViewContent('${fieldName}', ${index}): id attribute '${tdId}' yielded an empty field id after ` +
          "stripping the 'field-name-' prefix.",
      );
    }
    return this.page.locator(`#div_rtf_${fieldId}`);
  }

  /**
   * View-mode field content for any structured form field type. Returns the
   * locator for the rendered value regardless of field type
   * against a 7-field form (date/choice/number/radio/string/text/time):
   * - TinyMCE text → `#div_rtf_{fieldId}`
   * - Choice        → `#choiceText_{fieldId}`
   * - Radio         → `#radioText_{fieldId}`
   * - Date / Number / String / Time → `#plainText_{fieldId}` (same prefix for all four)
   *
   */
  async getStructuredFieldValue(fieldName: string, index = 0): Promise<Locator> {
    const fieldTd = this.page.locator("td.field-name").filter({ hasText: fieldName }).nth(index);
    const tdId = await fieldTd.getAttribute("id");
    if (!tdId) {
      throw new Error(
        `getStructuredFieldValue('${fieldName}', ${index}): no td.field-name element with an id attribute found. ` +
          "Verify the field name is spelled exactly as rendered and the document is in view mode.",
      );
    }
    const fieldId = tdId.replace("field-name-", "");
    if (!fieldId) {
      throw new Error(
        `getStructuredFieldValue('${fieldName}', ${index}): id attribute '${tdId}' yielded an empty field id after ` +
          "stripping the 'field-name-' prefix.",
      );
    }

    const divRtf = this.page.locator(`#div_rtf_${fieldId}`);
    if ((await divRtf.count()) > 0) return divRtf;

    const choiceText = this.page.locator(`#choiceText_${fieldId}`);
    if ((await choiceText.count()) > 0) return choiceText;

    const radioText = this.page.locator(`#radioText_${fieldId}`);
    if ((await radioText.count()) > 0) return radioText;

    return this.page.locator(`#plainText_${fieldId}`);
  }
}
