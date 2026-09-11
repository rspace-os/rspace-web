import { type Dialog, expect, type Locator, type Page } from "@playwright/test";
import { AttachmentsSection } from "@/__tests__/e2e/components/document/AttachmentsSection";
import { resolveFieldId } from "@/__tests__/e2e/components/document/DocumentFieldHelpers";
import { DocumentHeader } from "@/__tests__/e2e/components/document/DocumentHeader";
import { DocumentViewToolbar } from "@/__tests__/e2e/components/document/DocumentViewToolbar";
import { MaterialsDialogComponent } from "@/__tests__/e2e/components/document/MaterialsDialogComponent";
import { SignDocumentDialogComponent } from "@/__tests__/e2e/components/document/SignDocumentDialogComponent";
import { signedStatusLocator } from "@/__tests__/e2e/components/document/SignedStatus";
import { SigningDialogComponent } from "@/__tests__/e2e/components/document/SigningDialogComponent";
import { StoichiometryTableComponent } from "@/__tests__/e2e/components/document/StoichiometryTableComponent";
import { TinyMceEditor } from "@/__tests__/e2e/components/document/TinyMceEditor";
import { WitnessDocumentDialogComponent } from "@/__tests__/e2e/components/document/WitnessDocumentDialogComponent";
import type { RecordInfoDialog } from "@/__tests__/e2e/components/shared/RecordInfoDialog";
import { BasePage } from "../BasePage";

export class DocumentPage extends BasePage {
  readonly path = "/workspace/editor/structuredDocument";

  readonly header: DocumentHeader;
  readonly toolbar: DocumentViewToolbar;
  readonly attachments: AttachmentsSection;
  readonly signingDialog: SigningDialogComponent;
  readonly editingStatus: Locator;
  readonly lastModifiedDates: Locator;
  private readonly signedStatuses: Locator;

  constructor(page: Page) {
    super(page);
    this.header = new DocumentHeader(page);
    this.toolbar = new DocumentViewToolbar(page);
    this.attachments = new AttachmentsSection(page);
    this.signingDialog = new SigningDialogComponent(page);
    // Legacy JSP status banner; the same indicator covers document and entry editing.
    this.editingStatus = page.locator("#editingStatus");
    this.lastModifiedDates = page.getByText("Last modified:", { exact: false });
    this.signedStatuses = signedStatusLocator(page);
  }

  getId(): number {
    return Number(this.page.url().split("/structuredDocument/")[1].split("?")[0]);
  }

  async isLoaded(): Promise<void> {
    await this.page.waitForURL("**/workspace/editor/structuredDocument/**");
    await this.page.locator("#status .state:not(#editingStatus):visible").waitFor({ state: "visible" });
  }

  /** True when shared with this user at READ only (status.tag's #viewAmberStatusReadPermission). */
  async isReadOnly(): Promise<boolean> {
    return this.page.locator("#viewAmberStatusReadPermission").isVisible();
  }

  async isSigned(): Promise<boolean> {
    return (await this.signedStatuses.count()) > 0;
  }

  /** Whether the current user has a pending witness request on this document. */
  async canWitness(): Promise<boolean> {
    return this.toolbar.witnessButton.isVisible();
  }

  /** Whether the document has been fully witnessed (all pending witnesses confirmed). */
  async isWitnessed(): Promise<boolean> {
    return this.page.locator("#witnessedStatus").isVisible();
  }

  async canSign(): Promise<boolean> {
    return this.toolbar.signButton.isVisible();
  }

  async getFieldViewContent(fieldName: string, index = 0): Promise<Locator> {
    const fieldId = await resolveFieldId(this.page, fieldName, index, "getFieldViewContent");
    const content = this.page.locator(`#div_rtf_${fieldId}`);
    await content.waitFor({ state: "visible" });
    return content;
  }

  async equationInField(fieldName: string, index = 0): Promise<Locator> {
    const content = await this.getFieldViewContent(fieldName, index);
    // TinyMCE equations expose their persisted LaTeX on this application-owned element.
    return content.locator(".rsEquation");
  }

  /** A saved reaction table renders read-only, directly in the field, once the document is reopened. */
  async getStoichiometryTable(fieldName: string, index = 0): Promise<StoichiometryTableComponent> {
    const content = await this.getFieldViewContent(fieldName, index);
    return new StoichiometryTableComponent(content);
  }

  async openListOfMaterials(name: string, fieldIndex = 0): Promise<MaterialsDialogComponent> {
    await this.page
      .getByRole("button", { name: "Show list of materials associated with this field", exact: true })
      .nth(fieldIndex)
      .click();
    await this.page.getByRole("menuitem", { name: `1: ${name}`, exact: true }).click();
    const dialog = new MaterialsDialogComponent(this.page);
    await dialog.waitForOpen();
    return dialog;
  }

  /** Reads an "Ontologies" field's tag list in view mode: one tag per rendered <p>. */
  async getOntologyTags(index = 0): Promise<string[]> {
    const field = await this.getFieldViewContent("Ontologies", index);
    const lines = await field.locator("p").allInnerTexts();
    return lines.map((line) => line.trim()).filter((line) => line.length > 0);
  }

  /** Reads the external ontology metadata and term identities written by the CSV importer. */
  async getImportedOntology(index = 0): Promise<{
    name: string;
    version: string;
    terms: Array<{ label: string; uri: string }>;
  }> {
    const field = await this.getFieldViewContent("Ontologies", index);
    // OntologyDocManager stores this serialization as the field's displayed text.
    const [name, version, ...terms] = (await field.innerText()).trim().split("__RSP_EXTONT_TAG_DELIM__");
    const namePrefix = "__RSP_EXTONT_NAME__";
    const versionPrefix = "__RSP_EXTONT_VERSION__";
    if (!name?.startsWith(namePrefix) || !version?.startsWith(versionPrefix)) {
      throw new Error("Imported ontology is missing its name or version metadata.");
    }
    return {
      name: name.slice(namePrefix.length),
      version: version.slice(versionPrefix.length),
      terms: terms.map((term) => {
        const parts = term.split("__RSP_EXTONT_URL_DELIM__");
        if (parts.length !== 2 || !parts[0] || !parts[1]) {
          throw new Error(`Imported ontology term has no unique label/URI pair: ${term}`);
        }
        return { label: parts[0], uri: parts[1] };
      }),
    };
  }

  async editField(fieldName: string, index = 0): Promise<TinyMceEditor> {
    const fieldId = await resolveFieldId(this.page, fieldName, index, "editField");
    const editButton = this.page.locator(`#edit_${fieldId}`);
    const editorId = `rtf_${fieldId}`;
    const editorIframe = this.page.locator(`iframe#${editorId}_ifr`);
    await this.page.waitForLoadState("networkidle").catch(() => undefined);
    await expect(async () => {
      if (!(await editorIframe.isVisible().catch(() => false)) && (await editButton.isVisible().catch(() => false))) {
        await editButton.click();
      }
      await editorIframe.waitFor({ state: "visible", timeout: 10_000 });
    }).toPass({ timeout: 45_000 });
    return new TinyMceEditor(this.page, fieldId).waitForReady();
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

  /** Leaves editing without an explicit Save; completed autosaves remain available for recovery. */
  async leaveWithoutSaving(): Promise<void> {
    const handleDialog = (dialog: Dialog): Promise<void> =>
      dialog.type() === "beforeunload" ? dialog.accept() : dialog.dismiss();
    this.page.on("dialog", handleDialog);
    try {
      await this.page.goto("/workspace");
      await this.page.waitForURL((url) => url.pathname === "/workspace");
    } finally {
      this.page.off("dialog", handleDialog);
    }
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

  async rename(newName: string): Promise<void> {
    await this.header.rename(newName);
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
