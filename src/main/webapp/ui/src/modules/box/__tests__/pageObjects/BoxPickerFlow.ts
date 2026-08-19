import { expect, type Page } from "@playwright/test";

export class BoxPickerFlow {
  constructor(private readonly popup: Page) {}

  async login(email: string, password: string): Promise<void> {
    await this.popup.getByRole("textbox", { name: "Email Address" }).fill(email);
    await this.popup.getByRole("button", { name: "Next" }).click();
    await this.popup.getByRole("textbox", { name: "Password" }).fill(password);
    await this.popup.getByRole("button", { name: "Log In" }).click();
    await this.popup.getByRole("radio").first().waitFor({ state: "visible" });
    await this.popup.waitForLoadState("networkidle");
  }

  async selectFirstFileAndChoose(): Promise<string> {
    const radio = this.popup.getByRole("radio").first();
    const row = this.popup.getByRole("listitem").filter({ has: radio }).first();
    const fullText = await row.innerText();
    const name = fullText.split("\nUpdated")[0].trim();
    if (!name) throw new Error("selectFirstFileAndChoose: matched a file row with no text content");

    await radio.click();
    await this.popup.locator("#access-level-dropdown").selectOption({ label: "People with the link" });
    const chooseButton = this.popup.getByRole("button", { name: "Choose", exact: true });
    await expect(chooseButton).toBeEnabled();
    await Promise.all([this.popup.waitForEvent("close"), chooseButton.click()]);
    return name;
  }
}
