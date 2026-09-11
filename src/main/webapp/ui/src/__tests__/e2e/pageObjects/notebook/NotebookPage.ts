import { expect, type Locator, type Page } from "@playwright/test";
import { DocumentHeader } from "@/__tests__/e2e/components/document/DocumentHeader";
import { signedStatusLocator } from "@/__tests__/e2e/components/document/SignedStatus";
import { SigningDialogComponent } from "@/__tests__/e2e/components/document/SigningDialogComponent";
import { WitnessDialogComponent } from "@/__tests__/e2e/components/document/WitnessDialogComponent";
import { NotebookEntryStrip } from "@/__tests__/e2e/components/notebook/NotebookEntryStrip";
import { NotebookViewToolbar } from "@/__tests__/e2e/components/notebook/NotebookViewToolbar";
import type { RecordInfoDialog } from "@/__tests__/e2e/components/shared/RecordInfoDialog";
import { ShareDialog } from "@/__tests__/e2e/components/shared/ShareDialog";
import { WorkspaceTemplatePickerDialog } from "@/__tests__/e2e/components/workspace/WorkspaceTemplatePickerDialog";
import { BasePage } from "../BasePage";
import { DocumentEditorPage } from "../document/DocumentEditorPage";
import { DocumentPage } from "../document/DocumentPage";

export class NotebookPage extends BasePage {
  readonly path = "/notebookEditor";

  readonly header: DocumentHeader;
  readonly toolbar: NotebookViewToolbar;
  readonly entryStrip: NotebookEntryStrip;
  readonly signingDialog: SigningDialogComponent;
  readonly witnessDialog: WitnessDialogComponent;
  readonly ribbon: Locator;
  readonly entryContent: Locator;
  readonly emptyState: Locator;
  private readonly signedStatuses: Locator;
  private readonly witnessedStatus: Locator;

  constructor(page: Page) {
    super(page);
    this.header = new DocumentHeader(page);
    this.toolbar = new NotebookViewToolbar(page);
    this.entryStrip = new NotebookEntryStrip(page);
    this.signingDialog = new SigningDialogComponent(page);
    this.witnessDialog = new WitnessDialogComponent(page);
    // Legacy jQuery-rendered ribbon container; no stable accessible role or label.
    this.ribbon = page.locator("#journalEntriesRibbon");
    // The journal renders field headings and bare text in this legacy content panel.
    this.entryContent = page.locator("#journalPage");
    this.emptyState = page.getByText("There are no entries to display.").first();
    this.signedStatuses = signedStatusLocator(page);
    this.witnessedStatus = page.locator("#witnessedStatus");
  }

  async isLoaded(): Promise<void> {
    await this.page.waitForURL("**/notebookEditor/**");
    await Promise.race([
      this.entryStrip.entryCounter.waitFor({ state: "visible" }),
      this.emptyState.waitFor({ state: "visible" }),
    ]);
    await this.page.waitForLoadState("networkidle").catch(() => undefined);
  }

  async openByGlobalId(notebook: { id: number; globalId: string }): Promise<void> {
    await this.page.goto(`/globalId/${notebook.globalId}`);
    await expect(this.page).toHaveURL((url) => url.pathname === `${this.path}/${notebook.id}`);
    await this.isLoaded();
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

  async createFromForm(formName: string): Promise<DocumentPage> {
    await this.toolbar.createMenu.createFromCustomForm(formName);
    const doc = new DocumentPage(this.page);
    await doc.isLoaded();
    return doc;
  }

  /** Creates a new entry from a saved document template — lands in edit mode. */
  async createFromTemplate(templateName: string, newEntryName: string): Promise<DocumentEditorPage> {
    await this.toolbar.createMenu.create("From Template");
    const picker = new WorkspaceTemplatePickerDialog(this.page);
    await picker.waitUntilVisible();
    await picker.createFromTemplate(templateName, newEntryName);
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

  async canEdit(): Promise<boolean> {
    return this.toolbar.editButton.isVisible();
  }

  async canSign(): Promise<boolean> {
    return this.toolbar.signButton.isVisible();
  }

  async isSigned(): Promise<boolean> {
    return (await this.signedStatuses.count()) > 0;
  }

  async sign(password: string): Promise<void> {
    await this.toolbar.signButton.click();
    await this.signingDialog.waitForOpen();
    await this.signingDialog.signWithoutWitness(password);
  }

  async signWithWitness(password: string, witnessUsername: string): Promise<void> {
    await this.toolbar.signButton.click();
    await this.signingDialog.waitForOpen();
    await this.signingDialog.signWithWitness(password, witnessUsername);
  }

  /** Whether the current user has a pending witness request on this entry. */
  async canWitness(): Promise<boolean> {
    return this.toolbar.witnessButton.isVisible();
  }

  /** Whether the current entry has been fully witnessed (all pending witnesses confirmed). */
  async isWitnessed(): Promise<boolean> {
    return this.witnessedStatus.isVisible();
  }

  async confirmWitness(password: string): Promise<void> {
    await this.toolbar.witnessButton.click();
    await this.witnessDialog.waitForOpen();
    await this.witnessDialog.confirm(password);
  }

  async share(): Promise<ShareDialog> {
    await this.toolbar.shareButton.click();
    const dialog = new ShareDialog(this.page);
    await dialog.waitUntilVisible();
    return dialog;
  }

  /**
   * Toggles the entry ribbon open, matching whatever its current visible label is.
   * A prior mutation (e.g. deleteEntry()) can still be settling the toggle/ribbon
   * client-side even after its own network activity has finished.
   */
  async showAllEntries(): Promise<void> {
    await expect(async () => {
      const toggle = this.page.getByText("Show All Entries", { exact: true });
      if (await toggle.isVisible().catch(() => false)) {
        await toggle.click();
      }
      await this.ribbon.waitFor({ state: "visible", timeout: 3_000 });
    }).toPass({ timeout: 15_000 });
  }

  async hideAllEntries(): Promise<void> {
    await expect(async () => {
      const toggle = this.page.getByText("Hide All Entries", { exact: true });
      if (await toggle.isVisible().catch(() => false)) {
        await toggle.click();
      }
      await this.ribbon.waitFor({ state: "hidden", timeout: 3_000 });
    }).toPass({ timeout: 15_000 });
  }

  /** Deletes the currently-open entry and confirms the dialog. */
  async deleteEntry(): Promise<void> {
    await this.toolbar.actions.deleteButton.click();
    const dialog = this.page.getByRole("dialog", { name: "Confirm Deletion" });
    const confirmButton = dialog.getByRole("button", { name: "Confirm" });
    await confirmButton.waitFor({ state: "visible" });
    const [response] = await Promise.all([
      this.page.waitForResponse((res) => new URL(res.url()).pathname.includes("/notebookEditor/ajax/delete/")),
      confirmButton.click(),
    ]);
    if (!response.ok()) {
      throw new Error(`Delete entry failed: ${response.status()} ${response.statusText()}`);
    }
    await dialog.waitFor({ state: "hidden" });
    await this.isLoaded();
  }

  async closeNotebook(): Promise<void> {
    await this.toolbar.actions.closeLink.click();
  }

  async previousEntry(): Promise<void> {
    await this.entryStrip.previous();
  }

  async nextEntry(): Promise<void> {
    await this.entryStrip.next();
  }

  /** Switches the active entry to the named one via its ribbon thumbnail. */
  async selectEntry(name: string): Promise<void> {
    await this.entryStrip.clickEntry(name);
  }

  entryThumbnail(name: string): Locator {
    return this.entryStrip.entryThumbnail(name);
  }

  async isEntryVisibleInRibbon(name: string): Promise<boolean> {
    return this.entryThumbnail(name).isVisible();
  }

  async getAllEntriesText(): Promise<string[]> {
    const handles = await this.ribbon.locator("[title^='Name:']").all();
    const names: string[] = [];
    const prefix = "Name: '";
    for (const handle of handles) {
      const title = await handle.getAttribute("title");
      if (title?.startsWith(prefix) && title.endsWith("'")) {
        names.push(title.slice(prefix.length, -1));
      }
    }
    return names;
  }
}
