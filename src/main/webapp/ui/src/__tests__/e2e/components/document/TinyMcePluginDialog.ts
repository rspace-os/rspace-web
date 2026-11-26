import type { FrameLocator, Locator, Page } from "@playwright/test";

/** A TinyMCE plugin dialog that shows a same-origin page in a frame, as the external tool plugins do. */
export class TinyMcePluginDialog {
  constructor(private readonly page: Page) {}

  dialog(title: string): Locator {
    return this.page.getByRole("dialog", { name: title });
  }

  /** Opens `url` in a dialog of the active editor and returns its frame once the page has loaded. */
  async openUrl(title: string, url: string): Promise<FrameLocator> {
    await this.page.evaluate(
      ({ title, url }) =>
        (
          window as unknown as {
            tinymce: { activeEditor: { windowManager: { openUrl(spec: { title: string; url: string }): void } } };
          }
        ).tinymce.activeEditor.windowManager.openUrl({ title, url }),
      { title, url },
    );
    const frame = this.dialog(title).locator("iframe").contentFrame();
    await frame.locator("body").waitFor({ state: "attached" });
    return frame;
  }
}
