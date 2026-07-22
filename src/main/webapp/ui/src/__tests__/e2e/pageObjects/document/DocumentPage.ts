import type { Locator, Page } from "@playwright/test";
import { AttachmentsSection } from "@/__tests__/e2e/components/document/AttachmentsSection";
import { resolveFieldId } from "@/__tests__/e2e/components/document/DocumentFieldHelpers";
import { DocumentHeader } from "@/__tests__/e2e/components/document/DocumentHeader";
import { DocumentViewToolbar } from "@/__tests__/e2e/components/document/DocumentViewToolbar";
import { BasePage } from "../BasePage";

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
    await this.page.waitForURL("**/workspace/editor/structuredDocument/**");
    await this.page
      .locator(
        "#viewGreenStatus:visible, #viewAmberStatus:visible, #viewAmberStatusReadPermission:visible, #viewRedStatus:visible",
      )
      .waitFor({ state: "visible" });
  }

  async getFieldViewContent(fieldName: string, index = 0): Promise<Locator> {
    const fieldId = await resolveFieldId(this.page, fieldName, index, "getFieldViewContent");
    return this.page.locator(`#div_rtf_${fieldId}`);
  }

  async getStructuredFieldValue(fieldName: string, index = 0): Promise<Locator> {
    const fieldId = await resolveFieldId(this.page, fieldName, index, "getStructuredFieldValue");

    const divRtf = this.page.locator(`#div_rtf_${fieldId}`);
    if ((await divRtf.count()) > 0) return divRtf;

    const choiceText = this.page.locator(`#choiceText_${fieldId}`);
    if ((await choiceText.count()) > 0) return choiceText;

    const radioText = this.page.locator(`#radioText_${fieldId}`);
    if ((await radioText.count()) > 0) return radioText;

    return this.page.locator(`#plainText_${fieldId}`);
  }

  async close(): Promise<void> {
    await this.toolbar.actions.closeLink.click();
  }

  async saveAsTemplate(templateName: string): Promise<void> {
    await this.toolbar.saveAsTemplateButton.click();
    const dialog = this.page.getByRole("dialog", { name: "Save Template" });
    await dialog.getByRole("textbox", { name: "Template Name" }).fill(templateName);
    await dialog.getByRole("button", { name: "OK" }).click();
    await dialog.waitFor({ state: "hidden" });
  }
}
