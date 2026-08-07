import type { Locator, Page } from "@playwright/test";
import { resolveFieldId } from "@/__tests__/e2e/components/document/DocumentFieldHelpers";
import { DocumentToolbar } from "@/__tests__/e2e/components/document/DocumentToolbar";
import { TinyMceEditor } from "@/__tests__/e2e/components/document/TinyMceEditor";
import { GalleryPickerComponent } from "@/__tests__/e2e/components/shared/GalleryPickerComponent";
import { CaliraDialogComponent } from "@/modules/calira/__tests__/pageObjects/CaliraDialogComponent";
import { ExternalWorkflowDialogComponent } from "@/modules/galaxy/__tests__/pageObjects/ExternalWorkflowDialogComponent";
import { GalaxyDialogComponent } from "@/modules/galaxy/__tests__/pageObjects/GalaxyDialogComponent";
import { GitHubDialogComponent } from "@/modules/github/__tests__/pageObjects/GitHubDialogComponent";
import { NextcloudDialogComponent } from "@/modules/nextcloud/__tests__/pageObjects/NextcloudDialogComponent";
import { OmeroDialogComponent } from "@/modules/omero/__tests__/pageObjects/OmeroDialogComponent";
import { OwnCloudDialogComponent } from "@/modules/owncloud/__tests__/pageObjects/OwnCloudDialogComponent";
import { ProtocolsIODialogComponent } from "@/modules/protocolsio/__tests__/pageObjects/ProtocolsIODialogComponent";
import { PubchemDialogComponent } from "@/modules/pubchem/__tests__/pageObjects/PubchemDialogComponent";
import { PyratDialogComponent } from "@/modules/pyrat/__tests__/pageObjects/PyratDialogComponent";
import { DocumentPage } from "./DocumentPage";

export class DocumentEditorPage extends DocumentPage {
  readonly editToolbar: DocumentToolbar;
  readonly pubchemDialog: PubchemDialogComponent;
  readonly galleryPicker: GalleryPickerComponent;
  readonly galaxyDialog: GalaxyDialogComponent;
  readonly externalWorkflowDialog: ExternalWorkflowDialogComponent;
  readonly pyratDialog: PyratDialogComponent;
  readonly omeroDialog: OmeroDialogComponent;
  readonly caliraDialog: CaliraDialogComponent;
  readonly owncloudDialog: OwnCloudDialogComponent;
  readonly nextcloudDialog: NextcloudDialogComponent;
  readonly protocolsioDialog: ProtocolsIODialogComponent;
  readonly githubDialog: GitHubDialogComponent;

  constructor(page: Page) {
    super(page);
    this.editToolbar = new DocumentToolbar(page);
    this.pubchemDialog = new PubchemDialogComponent(page);
    this.galleryPicker = new GalleryPickerComponent(page);
    this.galaxyDialog = new GalaxyDialogComponent(page);
    this.externalWorkflowDialog = new ExternalWorkflowDialogComponent(page);
    this.pyratDialog = new PyratDialogComponent(page);
    this.omeroDialog = new OmeroDialogComponent(page);
    this.caliraDialog = new CaliraDialogComponent(page);
    this.owncloudDialog = new OwnCloudDialogComponent(page);
    this.nextcloudDialog = new NextcloudDialogComponent(page);
    this.protocolsioDialog = new ProtocolsIODialogComponent(page);
    this.githubDialog = new GitHubDialogComponent(page);
  }

  override async isLoaded(): Promise<void> {
    await this.waitUntilReady();
  }

  private async waitUntilReady(): Promise<void> {
    await this.page.waitForURL("**/workspace/editor/structuredDocument/**");
    await this.page.locator("#editingStatus").waitFor({ state: "visible" });
    // TinyMCE has no semantic editor-container role; its stable class signals plugin readiness.
    await this.page.locator(".tox-tinymce").first().waitFor({ state: "visible" });
  }

  async getField(fieldName: string, index = 0): Promise<TinyMceEditor> {
    const fieldId = await resolveFieldId(this.page, fieldName, index, "getField");
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
    return this.page.getByRole("button", { name: "Insert from PubChem" });
  }

  async openPubchemDialog(): Promise<PubchemDialogComponent> {
    await this.pubchemToolbarButton.click();
    await this.pubchemDialog.waitForOpen();
    return this.pubchemDialog;
  }

  get insertFromGalleryButton(): Locator {
    return this.page.getByRole("button", { name: "Insert from Gallery" });
  }

  async openGalleryPicker(): Promise<GalleryPickerComponent> {
    await this.insertFromGalleryButton.click();
    await this.galleryPicker.waitForOpen();
    return this.galleryPicker;
  }

  get galaxyToolbarButton(): Locator {
    return this.page.getByRole("button", { name: "Use a Galaxy Workflow" });
  }

  async openGalaxyDialog(): Promise<GalaxyDialogComponent> {
    await this.galaxyToolbarButton.click();
    await this.galaxyDialog.waitForOpen();
    return this.galaxyDialog;
  }

  get galaxyWorkflowIcon(): Locator {
    return this.page.getByRole("button", { name: "Show computational workflows associated with this field" });
  }

  async openExternalWorkflowsDialog(): Promise<ExternalWorkflowDialogComponent> {
    await this.galaxyWorkflowIcon.click();
    await this.externalWorkflowDialog.waitForOpen();
    return this.externalWorkflowDialog;
  }

  get pyratToolbarButton(): Locator {
    return this.page.getByRole("button", { name: "Link to PyRAT" });
  }

  async openPyratDialog(): Promise<PyratDialogComponent> {
    await this.pyratToolbarButton.click();
    await this.pyratDialog.waitForOpen();
    return this.pyratDialog;
  }

  get omeroToolbarButton(): Locator {
    return this.page.getByRole("button", { name: "Insert from Omero" });
  }

  async openOmeroDialog(): Promise<OmeroDialogComponent> {
    await this.omeroToolbarButton.click();
    await this.omeroDialog.waitForOpen();
    return this.omeroDialog;
  }

  get caliraToolbarButton(): Locator {
    return this.page.getByRole("button", { name: "Insert from Calira" });
  }

  async openCaliraDialog(): Promise<CaliraDialogComponent> {
    await this.caliraToolbarButton.click();
    await this.caliraDialog.waitForOpen();
    return this.caliraDialog;
  }

  get owncloudToolbarButton(): Locator {
    return this.page.getByRole("button", { name: "Insert from ownCloud" });
  }

  async openOwnCloudDialog(): Promise<OwnCloudDialogComponent> {
    await this.owncloudToolbarButton.click();
    await this.owncloudDialog.waitForOpen();
    return this.owncloudDialog;
  }

  get nextcloudToolbarButton(): Locator {
    return this.page.getByRole("button", { name: "Insert from Nextcloud" });
  }

  async openNextcloudDialog(): Promise<NextcloudDialogComponent> {
    await this.nextcloudToolbarButton.click();
    await this.nextcloudDialog.waitForOpen();
    return this.nextcloudDialog;
  }

  get protocolsioToolbarButton(): Locator {
    return this.page.getByRole("button", { name: "Import from Protocols.io" });
  }

  async openProtocolsIoDialog(): Promise<ProtocolsIODialogComponent> {
    await this.protocolsioToolbarButton.click();
    await this.protocolsioDialog.waitForOpen();
    return this.protocolsioDialog;
  }

  get githubToolbarButton(): Locator {
    return this.page.getByRole("button", { name: "Insert from GitHub" });
  }

  async openGitHubDialog(): Promise<GitHubDialogComponent> {
    await this.githubToolbarButton.click();
    await this.githubDialog.waitForOpen();
    return this.githubDialog;
  }
}
