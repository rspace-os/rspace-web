import type { Locator, Page } from "@playwright/test";
import { AttachmentsSection } from "@/__tests__/e2e/components/document/AttachmentsSection";
import { resolveFieldId } from "@/__tests__/e2e/components/document/DocumentFieldHelpers";
import { DocumentHeader } from "@/__tests__/e2e/components/document/DocumentHeader";
import { DocumentViewToolbar } from "@/__tests__/e2e/components/document/DocumentViewToolbar";
import { SignDocumentDialogComponent } from "@/__tests__/e2e/components/document/SignDocumentDialogComponent";
import { SigningDialogComponent } from "@/__tests__/e2e/components/document/SigningDialogComponent";
import { WitnessDocumentDialogComponent } from "@/__tests__/e2e/components/document/WitnessDocumentDialogComponent";
import type { RecordInfoDialog } from "@/__tests__/e2e/components/shared/RecordInfoDialog";
import { BasePage } from "../BasePage";

export class DocumentPage extends BasePage {
  readonly path = "/workspace/editor/structuredDocument";

  readonly header: DocumentHeader;
  readonly toolbar: DocumentViewToolbar;
  readonly attachments: AttachmentsSection;
  readonly signingDialog: SigningDialogComponent;

  constructor(page: Page) {
    super(page);
    this.header = new DocumentHeader(page);
    this.toolbar = new DocumentViewToolbar(page);
    this.attachments = new AttachmentsSection(page);
    this.signingDialog = new SigningDialogComponent(page);
  }

  getId(): number {
    return Number(this.page.url().split("/structuredDocument/")[1].split("?")[0]);
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

  async reload(): Promise<void> {
    await this.page.goto(this.page.url().split("?")[0]);
    await this.isLoaded();
  }

  async saveAsTemplate(templateName: string): Promise<void> {
    await this.toolbar.saveAsTemplateButton.click();
    const dialog = this.page.getByRole("dialog", { name: "Save Template" });
    await dialog.getByRole("textbox", { name: "Template Name" }).fill(templateName);
    await dialog.getByRole("button", { name: "OK" }).click();
    await dialog.waitFor({ state: "hidden" });
  }

  async openRecordInfo(): Promise<RecordInfoDialog> {
    return this.header.openRecordInfo();
  }

  async signWithoutWitness(password: string): Promise<void> {
    await this.toolbar.signButton.click();
    await this.signingDialog.waitForOpen();
    await this.signingDialog.signWithoutWitness(password);
  }

  async sign(password: string, witnessLabels: string[] = []): Promise<void> {
    await this.toolbar.signButton.click();
    const dialog = new SignDocumentDialogComponent(this.page);
    await dialog.waitUntilVisible();
    for (const label of witnessLabels) {
      await dialog.selectWitness(label);
    }
    await dialog.signWithPassword(password);
  }

  async witness(password: string): Promise<void> {
    await this.toolbar.witnessButton.click();
    const dialog = new WitnessDocumentDialogComponent(this.page);
    await dialog.waitUntilVisible();
    await dialog.witnessWithPassword(password);
  }

  statusText(text: string): Locator {
    return this.page.getByText(text, { exact: true });
  }

  // Legacy JSP status banner
  get readOnlyStatus(): Locator {
    return this.page.locator("#viewAmberStatusReadPermission");
  }
}
