import type { Page } from "@playwright/test";

export class DropboxPickerFlow {
  constructor(private readonly popup: Page) {}

  async login(email: string, password: string): Promise<void> {
    await this.popup.getByRole("textbox", { name: "Email" }).fill(email);
    await this.popup.getByRole("button", { name: "Continue", exact: true }).click();
    await this.popup.getByRole("textbox", { name: "Password" }).fill(password);
    await this.popup.getByRole("button", { name: "Log in" }).click();
    await this.popup.getByRole("button", { name: "My files" }).waitFor({ state: "visible" });
  }

  async selectFirstFileAndChoose(): Promise<string> {
    await this.popup.getByRole("button", { name: "My files" }).click();
    const checkbox = this.popup.getByRole("checkbox").first();
    const accessibleName = (await checkbox.getAttribute("aria-label")) ?? "";
    const fileName = accessibleName.split(" ")[0].replace(/^\//, "");
    if (!fileName) throw new Error("selectFirstFileAndChoose: matched a file checkbox with no accessible name");

    await checkbox.click();
    const chooseButton = this.popup.getByRole("button", { name: "Choose", exact: true });
    await Promise.all([this.popup.waitForEvent("close"), chooseButton.click()]);
    return fileName;
  }
}
