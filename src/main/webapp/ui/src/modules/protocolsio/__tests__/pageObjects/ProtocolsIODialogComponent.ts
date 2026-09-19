import type { FrameLocator, Locator, Page } from "@playwright/test";

export class ProtocolsIODialogComponent {
  readonly root: Locator;
  readonly frame: FrameLocator;
  readonly importButton: Locator;
  readonly closeButton: Locator;

  constructor(page: Page) {
    this.root = page.getByRole("dialog", { name: "Protocols.io (beta)" });
    this.frame = this.root.frameLocator("iframe");
    this.importButton = this.root.getByRole("button", { name: "Import" });
    this.closeButton = this.root.getByRole("button", { name: "Close" });
  }

  // Legacy jQuery/Mustache widget — no ARIA roles here at all.
  async waitForOpen(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
    await this.frame.locator("#protocols_ioListings").waitFor({ state: "visible" });
  }

  async selectProtocol(title: string): Promise<void> {
    await this.frame.locator(".protocols_ioDocument", { hasText: title }).locator(".protocols_ioChoice").check();
  }

  async clickImport(): Promise<void> {
    await this.importButton.click();
    await this.root.waitFor({ state: "detached" });
  }

  async close(): Promise<void> {
    await this.closeButton.click();
    await this.root.waitFor({ state: "detached" });
  }
}
