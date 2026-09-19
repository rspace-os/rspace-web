import { basename } from "node:path";
import type { FrameLocator, Locator, Page } from "@playwright/test";
import { ImageQuickToolbar } from "@/__tests__/e2e/components/document/ImageQuickToolbar";

export class TinyMceEditor {
  private readonly frame: FrameLocator;
  private readonly body: Locator;
  readonly container: Locator;
  private readonly menubar: Locator;

  constructor(
    private readonly page: Page,
    readonly editorId: string,
  ) {
    this.frame = page.frameLocator(`iframe#${editorId}_ifr`);
    this.body = this.frame.locator("body#tinymce");
    // TinyMCE exposes stable classes but no semantic editor-container or menubar roles.
    this.container = page.locator("div.tox-tinymce").filter({ has: page.locator(`iframe#${editorId}_ifr`) });
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

  async getText(): Promise<string> {
    return this.body.innerText();
  }

  async getEmbeddedIframeSrc(): Promise<string | null> {
    return this.frame.locator("iframe").first().getAttribute("src");
  }

  async clickToolbarButton(name: string): Promise<void> {
    await this.container.getByRole("button", { name }).click();
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

  async selectImage(): Promise<ImageQuickToolbar> {
    await this.imageElement.click();
    const toolbar = new ImageQuickToolbar(this.page);
    await toolbar.waitForOpen();
    return toolbar;
  }
}
