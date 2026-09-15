import type { Page } from "@playwright/test";

export class OneDrivePickerFlow {
  constructor(private readonly popup: Page) {}

  async login(email: string, password: string): Promise<void> {
    await this.popup.getByRole("textbox", { name: email }).fill(email);
    await this.popup.getByRole("button", { name: "Next" }).click();
    await this.popup.getByRole("textbox", { name: "Password" }).fill(password);
    await this.popup.getByRole("button", { name: "Sign in" }).click();
  }

  async selectFirstFileAndChoose(): Promise<string> {
    const row = this.popup.getByRole("row").nth(1);
    const fileName = await row.innerText();
    if (!fileName) throw new Error("selectFirstFileAndChoose: matched a file row with no text content");

    await row.click();
    const chooseButton = this.popup.getByRole("button", { name: "Open", exact: true });
    await Promise.all([this.popup.waitForEvent("close"), chooseButton.click()]);
    return fileName;
  }
}
