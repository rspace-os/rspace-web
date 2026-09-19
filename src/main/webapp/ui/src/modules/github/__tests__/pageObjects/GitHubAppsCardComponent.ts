import type { Locator, Page } from "@playwright/test";
import { ToastsComponent } from "@/__tests__/e2e/components/shared/ToastsComponent";

export class GitHubAppsCardComponent {
  private readonly toasts: ToastsComponent;

  constructor(private readonly page: Page) {
    this.toasts = new ToastsComponent(page);
  }

  private async openCard(): Promise<Locator> {
    await this.page.goto("/apps");
    await this.page.locator('div[aria-label="GitHub"]').click();
    const dialog = this.page.getByRole("dialog");
    await dialog.waitFor({ state: "visible" });
    return dialog;
  }

  async connectAndLinkRepository(repoFullName: string): Promise<void> {
    const dialog = await this.openCard();

    await dialog.getByRole("button", { name: "Add", exact: true }).click();

    const repoRow = dialog.getByRole("row").filter({ hasText: repoFullName });
    await repoRow.waitFor({ state: "visible", timeout: 10_000 });
    await repoRow.getByRole("button", { name: "Add", exact: true }).click();
    await this.toasts.byVariant("success", "Successfully added repository.").first().waitFor({ state: "visible" });
    await this.toasts.dismissAll();

    await dialog.getByRole("button", { name: "ENABLE" }).click();
    await this.toasts.byVariant("success", "Update successful.").first().waitFor({ state: "visible" });
    await this.toasts.dismissAll();

    await dialog.waitFor({ state: "detached" });
  }
}
