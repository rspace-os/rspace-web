import type { Locator, Page } from "@playwright/test";
import { uploadViaFileChooser } from "./DialogHelpers";

export class AttachmentsEditor {
  constructor(
    private readonly page: Page,
    private readonly root: Locator,
  ) {}

  async upload(path: string): Promise<void> {
    await uploadViaFileChooser(this.page, this.root.getByRole("button", { name: "Upload" }), path);
  }

  row(fileName: string): Locator {
    return this.root.getByRole("row", { name: fileName });
  }

  async setAsPreviewImage(fileName: string): Promise<void> {
    await this.row(fileName).getByRole("button", { name: "Set as Preview Image" }).click();
  }
}
