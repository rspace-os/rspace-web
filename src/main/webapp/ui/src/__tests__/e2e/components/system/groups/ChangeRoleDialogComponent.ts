import type { Locator, Page } from "@playwright/test";

export class ChangeRoleDialogComponent {
  readonly root: Locator;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("dialog", { name: "Change User's Role" });
  }

  async waitUntilVisible(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  async makeUser(): Promise<void> {
    await this.root.getByRole("radio", { name: "User", exact: true }).check();
    await this.submit();
  }

  async makeLabAdmin(canViewAllDocuments: boolean): Promise<void> {
    await this.root.getByRole("radio", { name: "Lab Admin", exact: true }).check();
    const permission = canViewAllDocuments
      ? "Lab Admin can view all group's documents."
      : "Lab Admin cannot view all group's documents.";
    await this.root.getByRole("radio", { name: permission }).check();
    await this.submit();
  }

  /** Success navigates back to the group page; refusal is 200 + errorMsg. Done means the reload has settled. */
  private async submit(): Promise<void> {
    const reloaded = this.page.waitForEvent("load");
    reloaded.catch(() => undefined);
    const [response] = await Promise.all([
      this.page.waitForResponse((res) => new URL(res.url()).pathname.startsWith("/groups/ajax/admin/changeRole/")),
      this.root.getByRole("button", { name: "OK", exact: true }).click(),
    ]);
    if (!response.ok()) {
      throw new Error(`Changing the role failed: ${response.status()} ${response.statusText()}`);
    }
    // An unreadable body means the page already navigated, i.e. success.
    const body = (await response.json().catch(() => null)) as { errorMsg?: unknown } | null;
    if (body?.errorMsg) {
      throw new Error(`Changing the role was refused: ${JSON.stringify(body.errorMsg)}`);
    }
    await reloaded;
    await this.page.getByRole("button", { name: "Account Menu" }).waitFor({ state: "visible" });
  }
}
