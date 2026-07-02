import type { FrameLocator, Locator, Page } from "@playwright/test";

/**
 * Handle for a single TinyMCE editor instance.
 *
 * Constructed from an `editorId` (e.g. "rtf_26268", derived from the
 * `td.field-name` id attribute). That id uniquely identifies the iframe
 * (`iframe#rtf_26268_ifr`) and its containing `div.tox-tinymce` wrapper.
 *
 * Do NOT store instances across actions — always obtain a fresh one via
 * `DocumentEditorPage.getField()` to avoid stale references.
 */
export class TinyMceEditor {
  private readonly frame: FrameLocator;
  private readonly body: Locator;
  readonly container: Locator;
  private readonly menubar: Locator;

  constructor(
    page: Page,
    readonly editorId: string,
  ) {
    this.frame = page.frameLocator(`iframe#${editorId}_ifr`);
    this.body = this.frame.locator("body#tinymce");
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

  async clickToolbarButton(name: string): Promise<void> {
    await this.container.getByRole("button", { name }).click();
  }

  async openMenu(name: string): Promise<void> {
    await this.menubar.getByRole("menuitem", { name }).click();
  }

  async selectAll(): Promise<void> {
    await this.body.press("Control+a");
  }

  /**
   * Locator for a chemistry element inserted via the Ketcher plugin.
   * inside the editor body.
   */
  get chemElement(): Locator {
    return this.frame.locator('img[src*="sourceType=CHEM"]');
  }
}
