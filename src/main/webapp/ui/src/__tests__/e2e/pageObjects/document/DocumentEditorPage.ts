import type { Locator, Page } from "@playwright/test";
import { CreateSnippetDialogComponent } from "@/__tests__/e2e/components/document/CreateSnippetDialogComponent";
import { resolveFieldId } from "@/__tests__/e2e/components/document/DocumentFieldHelpers";
import { DocumentToolbar } from "@/__tests__/e2e/components/document/DocumentToolbar";
import { InternalLinkDialogComponent } from "@/__tests__/e2e/components/document/InternalLinkDialogComponent";
import { TinyMceEditor } from "@/__tests__/e2e/components/document/TinyMceEditor";
import { VideoEmbedDialogComponent } from "@/__tests__/e2e/components/document/VideoEmbedDialogComponent";
import { GalleryPickerComponent } from "@/__tests__/e2e/components/shared/GalleryPickerComponent";
import { CaliraDialogComponent } from "@/modules/calira/__tests__/pageObjects/CaliraDialogComponent";
import { EgnyteDialogComponent } from "@/modules/egnyte/__tests__/pageObjects/EgnyteDialogComponent";
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

type ToolbarDialog = { waitForOpen(): Promise<void> };

export class DocumentEditorPage extends DocumentPage {
  readonly editToolbar: DocumentToolbar;
  readonly pubchemDialog: PubchemDialogComponent;
  readonly galleryPicker: GalleryPickerComponent;
  readonly galaxyDialog: GalaxyDialogComponent;
  readonly externalWorkflowDialog: ExternalWorkflowDialogComponent;
  readonly pyratDialog: PyratDialogComponent;
  readonly omeroDialog: OmeroDialogComponent;
  readonly createSnippetDialog: CreateSnippetDialogComponent;
  readonly internalLinkDialog: InternalLinkDialogComponent;
  readonly caliraDialog: CaliraDialogComponent;
  readonly egnyteDialog: EgnyteDialogComponent;
  readonly owncloudDialog: OwnCloudDialogComponent;
  readonly nextcloudDialog: NextcloudDialogComponent;
  readonly protocolsioDialog: ProtocolsIODialogComponent;
  readonly githubDialog: GitHubDialogComponent;
  readonly videoEmbedDialog: VideoEmbedDialogComponent;

  constructor(page: Page) {
    super(page);
    this.editToolbar = new DocumentToolbar(page);
    this.videoEmbedDialog = new VideoEmbedDialogComponent(page);
    this.pubchemDialog = new PubchemDialogComponent(page);
    this.galleryPicker = new GalleryPickerComponent(page);
    this.galaxyDialog = new GalaxyDialogComponent(page);
    this.externalWorkflowDialog = new ExternalWorkflowDialogComponent(page);
    this.pyratDialog = new PyratDialogComponent(page);
    this.omeroDialog = new OmeroDialogComponent(page);
    this.createSnippetDialog = new CreateSnippetDialogComponent(page);
    this.internalLinkDialog = new InternalLinkDialogComponent(page);
    this.caliraDialog = new CaliraDialogComponent(page);
    this.egnyteDialog = new EgnyteDialogComponent(page);
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

  /** Clicks a toolbar button by its accessible name, then waits for the given dialog to open. */
  private async openToolbarDialog<T extends ToolbarDialog>(buttonName: string, dialog: T): Promise<T> {
    await this.page.getByRole("button", { name: buttonName }).click();
    await dialog.waitForOpen();
    return dialog;
  }

  async openPubchemDialog(): Promise<PubchemDialogComponent> {
    return this.openToolbarDialog("Insert from PubChem", this.pubchemDialog);
  }

  get insertFromGalleryButton(): Locator {
    return this.page.getByRole("button", { name: "Insert from Gallery" });
  }

  async openGalleryPicker(): Promise<GalleryPickerComponent> {
    await this.insertFromGalleryButton.click();
    await this.galleryPicker.waitForOpen();
    return this.galleryPicker;
  }

  async openGalaxyDialog(): Promise<GalaxyDialogComponent> {
    return this.openToolbarDialog("Use a Galaxy Workflow", this.galaxyDialog);
  }

  get galaxyWorkflowIcon(): Locator {
    return this.page.getByRole("button", { name: "Show computational workflows associated with this field" });
  }

  async openExternalWorkflowsDialog(): Promise<ExternalWorkflowDialogComponent> {
    await this.galaxyWorkflowIcon.click();
    await this.externalWorkflowDialog.waitForOpen();
    return this.externalWorkflowDialog;
  }

  async openPyratDialog(): Promise<PyratDialogComponent> {
    return this.openToolbarDialog("Link to PyRAT", this.pyratDialog);
  }

  async openOmeroDialog(): Promise<OmeroDialogComponent> {
    return this.openToolbarDialog("Insert from Omero", this.omeroDialog);
  }

  async openCaliraDialog(): Promise<CaliraDialogComponent> {
    return this.openToolbarDialog("Insert from Calira", this.caliraDialog);
  }

  async openOwnCloudDialog(): Promise<OwnCloudDialogComponent> {
    return this.openToolbarDialog("Insert from ownCloud", this.owncloudDialog);
  }

  async openNextcloudDialog(): Promise<NextcloudDialogComponent> {
    return this.openToolbarDialog("Insert from Nextcloud", this.nextcloudDialog);
  }

  async openProtocolsIoDialog(): Promise<ProtocolsIODialogComponent> {
    return this.openToolbarDialog("Import from Protocols.io", this.protocolsioDialog);
  }

  async openGitHubDialog(): Promise<GitHubDialogComponent> {
    return this.openToolbarDialog("Insert from GitHub", this.githubDialog);
  }

  async openEgnyteDialog(): Promise<EgnyteDialogComponent> {
    return this.openToolbarDialog("Insert from Egnyte", this.egnyteDialog);
  }

  get boxToolbarButton(): Locator {
    return this.page.getByRole("button", { name: "Insert from Box" });
  }

  async openBoxPicker(): Promise<Page> {
    const [popup] = await Promise.all([this.page.waitForEvent("popup"), this.boxToolbarButton.click()]);
    await popup.waitForLoadState();
    return popup;
  }

  get dropboxToolbarButton(): Locator {
    return this.page.getByRole("button", { name: "Insert from Dropbox" });
  }

  async openDropboxPicker(): Promise<Page> {
    const [popup] = await Promise.all([this.page.waitForEvent("popup"), this.dropboxToolbarButton.click()]);
    await popup.waitForLoadState();
    return popup;
  }

  get onedriveToolbarButton(): Locator {
    return this.page.getByRole("button", { name: "Insert from OneDrive" });
  }

  async openOneDrivePicker(): Promise<Page> {
    const [popup] = await Promise.all([this.page.waitForEvent("popup"), this.onedriveToolbarButton.click()]);
    await popup.waitForLoadState();
    return popup;
  }

  get createSnippetButton(): Locator {
    return this.page.getByRole("button", { name: "Create a snippet" });
  }

  async openCreateSnippetDialog(): Promise<CreateSnippetDialogComponent> {
    await this.createSnippetButton.click();
    await this.createSnippetDialog.waitForOpen();
    return this.createSnippetDialog;
  }

  get insertInternalLinkButton(): Locator {
    return this.page.getByRole("button", { name: "Insert internal link" });
  }

  async openInsertInternalLinkDialog(): Promise<InternalLinkDialogComponent> {
    await this.insertInternalLinkButton.click();
    await this.internalLinkDialog.waitForOpen();
    return this.internalLinkDialog;
  }
}
