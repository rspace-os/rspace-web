import { basename } from "node:path";
import type { FrameLocator, Locator, Page } from "@playwright/test";
import { ImageQuickToolbar } from "@/__tests__/e2e/components/document/ImageQuickToolbar";

export class TinyMceEditor {
  private readonly frame: FrameLocator;
  private readonly body: Locator;
  readonly container: Locator;
  private readonly menubar: Locator;
  readonly editorId: string;

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

  /** Types each line followed by Enter, producing one <p> per line (unlike fill(), which doesn't). */
  async typeLines(lines: string[]): Promise<void> {
    await this.waitForReady();
    await this.body.click();
    for (const line of lines) {
      await this.body.pressSequentially(line);
      await this.body.press("Enter");
    }
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
    const [response] = await Promise.all([
      this.page.waitForResponse((res) => res.url().includes("/ajax/saveStructuredDocument")),
      this.page.locator(`#stopEdit_${this.fieldId}`).click(),
    ]);
    if (!response.ok()) {
      throw new Error(`Save and View failed: ${response.status()} ${response.statusText()}`);
    }
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

  get chemElement(): Locator {
    return this.frame.locator('img[src*="sourceType=CHEM"]');
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
}
