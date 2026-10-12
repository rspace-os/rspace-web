import type { Locator, Page } from "@playwright/test";
import { assertAjaxSuccess, responseTo } from "@/__tests__/e2e/responses";

export class RorRegistryComponent {
  readonly searchField: Locator;
  readonly linkedMessage: Locator;
  private readonly root: Locator;
  private readonly linkButton: Locator;
  private readonly unlinkButton: Locator;
  private readonly errorAlert: Locator;

  constructor(private readonly page: Page) {
    // Rendered into the legacy #mainArea, which has no accessible container.
    this.root = page.locator("#mainArea");
    this.searchField = this.root.getByRole("textbox", { name: "Search Registry" });
    this.linkedMessage = this.root.getByText("A ROR ID is linked to this RSpace Instance.");
    this.linkButton = this.root.getByRole("button", { name: "Link", exact: true });
    this.unlinkButton = this.root.getByRole("button", { name: "UnLink", exact: true });
    this.errorAlert = this.root.getByRole("alert");
  }

  async waitUntilLoaded(): Promise<void> {
    await this.root.getByRole("heading", { name: "Research Organization Registry (ROR) Integration" }).waitFor();
    await this.searchField.or(this.unlinkButton).waitFor();
  }

  detail(text: string): Locator {
    return this.root.getByRole("heading", { name: text, exact: true });
  }

  async search(rorId: string): Promise<void> {
    await this.searchField.fill(rorId);
    await this.searchField.press("Enter");
    await this.linkButton.or(this.errorAlert).waitFor();
  }

  async link(): Promise<void> {
    await this.mutate("POST", () => this.linkButton.click());
    await this.unlinkButton.waitFor();
  }

  async unlink(): Promise<void> {
    await this.mutate("DELETE", () => this.unlinkButton.click());
    await this.searchField.waitFor();
  }

  private async mutate(method: "POST" | "DELETE", trigger: () => Promise<void>): Promise<void> {
    const response = await responseTo(
      this.page,
      method,
      ({ pathname }) => pathname.startsWith("/system/ror/rorForID"),
      trigger,
    );
    assertAjaxSuccess(await response.text(), `${method} ROR link`);
  }
}
