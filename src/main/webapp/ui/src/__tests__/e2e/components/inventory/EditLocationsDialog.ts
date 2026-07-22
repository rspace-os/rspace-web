import type { Locator, Page } from "@playwright/test";

export async function waitForImageDecoded(page: Page, image: Locator): Promise<void> {
  const handle = await image.elementHandle();
  if (!handle) return;
  await page.waitForFunction(
    (el) => (el as HTMLImageElement).complete && (el as HTMLImageElement).naturalWidth > 0,
    handle,
  );
}

export class EditLocationsDialog {
  readonly root: Locator;
  private readonly image: Locator;
  private readonly doneButton: Locator;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("dialog", { name: "Edit Locations" });

    // Responsive layout mounts a hidden duplicate; target the last visible content image.
    this.image = this.root.locator("img:visible").last();
    this.doneButton = this.root.getByRole("button", { name: "Done" });
  }

  async waitForOpen(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  async addMarker(xFraction: number, yFraction: number): Promise<void> {
    const imageTab = this.root.getByRole("tab", { name: "Image" });
    if (await imageTab.isVisible().catch(() => false)) {
      await imageTab.click();
    }
    await this.image.waitFor({ state: "visible" });
    await waitForImageDecoded(this.page, this.image);
    const box = await this.image.boundingBox();
    if (!box) {
      throw new Error("Locations image is not visible — upload an image before opening Edit Locations.");
    }
    await this.page.mouse.click(box.x + box.width * xFraction, box.y + box.height * yFraction);
  }

  async markerCount(): Promise<number> {
    await this.root.getByRole("tab", { name: "Compact" }).click();
    return this.root.locator("table tbody tr").count();
  }

  async done(): Promise<void> {
    await this.doneButton.click();
    await this.root.waitFor({ state: "detached" });
  }
}
