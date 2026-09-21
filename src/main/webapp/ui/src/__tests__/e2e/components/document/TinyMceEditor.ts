import { basename } from "node:path";
import { expect, type FrameLocator, type Locator, type Page } from "@playwright/test";
import { ChemistryFieldContent } from "@/__tests__/e2e/components/document/ChemistryFieldContent";
import { ImageQuickToolbar } from "@/__tests__/e2e/components/document/ImageQuickToolbar";
import { StoichiometryReactionDialogComponent } from "@/__tests__/e2e/components/document/StoichiometryReactionDialogComponent";

export class TinyMceEditor {
  private readonly frame: FrameLocator;
  private readonly body: Locator;
  readonly container: Locator;
  private readonly menubar: Locator;
  readonly editorId: string;
  readonly chemistry: ChemistryFieldContent;

  constructor(
    private readonly page: Page,
    readonly fieldId: string,
  ) {
    this.editorId = `rtf_${fieldId}`;
    this.frame = page.frameLocator(`iframe#${this.editorId}_ifr`);
    this.body = this.frame.locator("body#tinymce");
    // TinyMCE exposes stable classes but no semantic editor-container or menubar roles.
    this.container = page.locator("div.tox-tinymce").filter({ has: page.locator(`iframe#${this.editorId}_ifr`) });
    this.menubar = this.container.locator(".tox-menubar");
    this.chemistry = new ChemistryFieldContent(page, this.frame, this.container);
  }

  async waitForReady(): Promise<this> {
    await this.container.waitFor({ state: "visible" });
    await this.menubar.waitFor({ state: "visible" });
    return this;
  }

  async fill(text: string): Promise<void> {
    await this.waitForReady();
    await this.body.fill(text);
  }

  /** Returns only after this edit has been accepted by the field autosave endpoint. */
  async fillAndWaitForAutosave(text: string): Promise<void> {
    const [response] = await Promise.all([
      this.page.waitForResponse((res) => {
        if (!res.url().endsWith("/ajax/autosaveField")) return false;
        const data = new URLSearchParams(res.request().postData() ?? "");
        return data.get("fieldId") === this.fieldId && (data.get("dataValue") ?? "").includes(text);
      }),
      (async () => {
        await this.waitForReady();
        await this.body.press("ControlOrMeta+A");
        await this.body.pressSequentially(text);
        await this.page.locator(`#field-name-${this.fieldId}`).click();
      })(),
    ]);
    expect(response.ok()).toBe(true);
    expect(await response.json(), "The edited text was accepted by autosave").toMatchObject({ data: true });
  }

  async menuItems(name: string): Promise<Locator> {
    await this.openMenu(name);
    const menu = this.page.getByRole("menu");
    return menu.getByRole("menuitem").or(menu.getByRole("menuitemcheckbox"));
  }

  async closeMenu(): Promise<void> {
    await this.page.keyboard.press("Escape");
    await this.page.getByRole("menu").waitFor({ state: "hidden" });
  }

  async openMolarityCalculator(): Promise<Page> {
    await this.openMenu("Online Tools");
    await this.page.getByRole("menuitem", { name: "Sigma-Aldrich.com", exact: true }).hover();
    const [popup] = await Promise.all([
      this.page.waitForEvent("popup"),
      this.page.getByRole("menuitem", { name: "Normality & Molarity calculator", exact: true }).click(),
    ]);
    return popup;
  }

  /** Types each line followed by Enter, producing one <p> per line (unlike fill(), which doesn't). */
  async typeLines(lines: string[]): Promise<void> {
    await this.waitForReady();
    await this.body.click();
    for (const line of lines) {
      await this.body.pressSequentially(line);
      await this.body.press("Enter");
    }
  }

  async typeAtEnd(text: string): Promise<void> {
    await this.waitForReady();
    await this.body.locator("p").last().click();
    await this.body.press("Control+End");
    await this.body.pressSequentially(text);
  }

  async getText(): Promise<string> {
    return this.body.innerText();
  }

  async getEmbeddedIframeSrc(): Promise<string | null> {
    return this.frame.locator("iframe").first().getAttribute("src");
  }

  async clickToolbarButton(name: string): Promise<void> {
    await this.container.getByRole("button", { name }).click();
  }

  /**
   * Saves this field's content. Only applies when editing a single field inline on an
   * already-saved (view-mode) document — DocumentPage.editField() — where the Save
   * button lives in this field's own toolbar, not the document-level Save dropdown.
   */
  async save(): Promise<void> {
    const [response] = await Promise.all([
      this.page.waitForResponse((res) => res.url().includes("/ajax/saveStructuredDocument")),
      this.clickToolbarButton("Save"),
    ]);
    if (!response.ok()) {
      throw new Error(`Saving field failed: ${response.status()} ${response.statusText()}`);
    }
  }

  async saveAndFinishEditing(): Promise<void> {
    const [response, refreshedFields] = await Promise.all([
      this.page.waitForResponse((res) => res.url().includes("/ajax/saveStructuredDocument")),
      this.page.waitForResponse((res) => res.url().includes("/ajax/getUpdatedFields")),
      this.page.locator(`#stopEdit_${this.fieldId}`).click(),
    ]);
    if (!response.ok()) {
      throw new Error(`Save and View failed: ${response.status()} ${response.statusText()}`);
    }
    if (!refreshedFields.ok()) {
      throw new Error(`Reloading saved fields failed: ${refreshedFields.status()} ${refreshedFields.statusText()}`);
    }
    await refreshedFields.finished();
    await this.container.waitFor({ state: "hidden" });
  }

  async openMenu(name: string): Promise<void> {
    await this.menubar.getByRole("menuitem", { name }).click();
  }

  async selectAll(): Promise<void> {
    await this.body.press("Control+a");
  }

  async insertImageAttachment(image: string | { name: string; mimeType: string; buffer: Buffer }): Promise<void> {
    const fileName = typeof image === "string" ? basename(image) : image.name;
    const uploadButton = this.container
      .getByRole("button", { name: "Insert file from computer" })
      .or(this.container.getByRole("button", { name: "Upload a file from your mobile device" }));
    const [chooser] = await Promise.all([this.page.waitForEvent("filechooser"), uploadButton.click()]);
    const [response] = await Promise.all([
      this.page.waitForResponse(
        (res) => res.request().method() === "POST" && new URL(res.url()).pathname === "/gallery/ajax/uploadFile/",
      ),
      chooser.setFiles(image),
    ]);
    if (!response.ok()) {
      throw new Error(`POST /gallery/ajax/uploadFile failed: ${response.status()} ${response.statusText()}`);
    }

    await this.body.getByRole("img", { name: `image ${fileName}` }).waitFor({ state: "visible" });
  }

  get imageElement(): Locator {
    return this.frame.locator('img[src*="sourceType=IMAGE"]');
  }

  async countImages(): Promise<number> {
    return this.imageElement.count();
  }

  async getImageSourceIds(): Promise<string[]> {
    const srcs = await this.imageElement.evaluateAll((imgs) => imgs.map((img) => img.getAttribute("src") ?? ""));
    return srcs.map((src) => new URL(src, "http://localhost").searchParams.get("sourceId") ?? "");
  }

  async selectImage(): Promise<ImageQuickToolbar> {
    await this.imageElement.click();
    const toolbar = new ImageQuickToolbar(this.page);
    await toolbar.waitForOpen();
    return toolbar;
  }

  /** The inserted-but-not-yet-configured reaction table node, before any compound has been added. */
  get stoichiometryTablePlaceholder(): Locator {
    return this.frame.getByRole("button", { name: "Reaction Table" });
  }

  async hasBlankStoichiometryTable(): Promise<boolean> {
    return this.stoichiometryTablePlaceholder.isVisible();
  }

  async insertStoichiometryTable(): Promise<StoichiometryReactionDialogComponent> {
    await this.clickToolbarButton("Insert reaction table");
    const dialog = new StoichiometryReactionDialogComponent(this.page);
    await dialog.waitForOpen();
    return dialog;
  }

  async viewStoichiometryTable(): Promise<StoichiometryReactionDialogComponent> {
    // Saved nodes render this text instead of the fresh placeholder's button role.
    await this.frame.getByText("Stoichiometry Table (no preview)", { exact: true }).click();
    const dialog = new StoichiometryReactionDialogComponent(this.page);
    await this.page.getByRole("button", { name: "View stoichiometry", exact: true }).click();
    await dialog.waitForOpen();
    return dialog;
  }

  get attachmentIcon(): Locator {
    return this.frame.locator("img.attachmentIcon");
  }

  async hasAttachment(): Promise<boolean> {
    return this.attachmentIcon.isVisible();
  }

  attachmentName(fileName: string): Locator {
    return this.frame.getByRole("link", { name: fileName, exact: true });
  }

  attachment(fileName: string): Locator {
    // Gallery attachments use a legacy non-editable wrapper without an accessible role.
    return this.body.locator(".attachmentDiv").filter({ has: this.attachmentName(fileName) });
  }

  /** The equation's own LaTeX source, read back from its `data-equation` attribute. */
  get equationSource(): Locator {
    return this.frame.locator(".rsEquation");
  }

  async getEquationSource(): Promise<string | null> {
    return this.equationSource.getAttribute("data-equation");
  }

  async insertEquation(latex: string): Promise<void> {
    await this.clickToolbarButton("Insert equation");
    await this.submitEquation(latex);
  }

  async editEquation(latex: string): Promise<void> {
    await this.equationSource.dblclick();
    await this.submitEquation(latex);
  }

  private async submitEquation(latex: string): Promise<void> {
    const dialog = this.page.getByRole("dialog", { name: "Equation Editor" });
    await dialog.waitFor({ state: "visible" });
    const dialogFrame = dialog.locator("iframe").contentFrame();
    await dialogFrame.getByRole("textbox", { name: "any simple or complex LaTeX" }).fill(latex);
    await dialogFrame.getByRole("button", { name: "Parse Equation" }).click();
    // Parsing loads MathJax asynchronously; Insert ignores a preview that is not ready.
    await dialogFrame.getByRole("img").waitFor({ state: "visible" });
    await dialog.getByRole("button", { name: "Insert", exact: true }).click();
    await dialog.waitFor({ state: "hidden" });
  }
}
