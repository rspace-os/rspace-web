import type { Locator, Page } from "@playwright/test";

export class ChangePiDialogComponent {
  readonly root: Locator;

  constructor(page: Page) {
    this.root = page.getByRole("dialog", { name: "Change LabGroup's PI" });
  }

  async waitUntilVisible(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  async submit(newPiFullName: string): Promise<void> {
    await this.root.locator(".setNewPiRadioDiv").filter({ hasText: newPiFullName }).getByRole("radio").click();
    await this.root.getByRole("button", { name: "Submit", exact: true }).click();
  }
}
