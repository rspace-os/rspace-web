import type { Locator, Page } from "@playwright/test";
import { ToastsComponent } from "@/__tests__/e2e/components/shared/ToastsComponent";

export class AppsPage {
  private readonly toasts: ToastsComponent;

  constructor(private readonly page: Page) {
    this.toasts = new ToastsComponent(page);
  }

  private async openCard(name: string): Promise<Locator> {
    await this.page.goto("/apps");
    await this.page.locator(`div[aria-label="${name}"]`).click();
    const dialog = this.page.getByRole("dialog");
    await dialog.waitFor({ state: "visible" });
    return dialog;
  }

  private async clickEnableOrClose(dialog: Locator, targetLabel: "ENABLE" | "DISABLE" = "ENABLE"): Promise<void> {
    const btn = dialog.getByRole("button", { name: targetLabel });
    if (await btn.isVisible().catch(() => false)) {
      await this.toasts.dismissAll();
      await btn.click();
      await this.toasts.byVariant("success", "Update successful.").first().waitFor({ state: "visible" });
      await this.toasts.dismissAll();
    } else {
      await dialog.getByRole("button", { name: "Close" }).click();
    }
    await dialog.waitFor({ state: "detached" });
  }

  async setEnabled(name: string, enabled: boolean): Promise<void> {
    const dialog = await this.openCard(name);
    await this.clickEnableOrClose(dialog, enabled ? "ENABLE" : "DISABLE");
  }

  async setEnabledWithApiKey(
    name: string,
    apiKey: string,
    opts?: { textboxName?: string; serverAlias?: string },
  ): Promise<void> {
    const textboxName = opts?.textboxName ?? "API Key";
    const dialog = await this.openCard(name);

    if (opts?.serverAlias) {
      const alreadyConfigured = await dialog.getByRole("textbox", { name: textboxName }).count();
      if (alreadyConfigured === 0) {
        await dialog.getByRole("button", { name: "Add", exact: true }).click();
        await this.page.getByRole("menuitem", { name: opts.serverAlias }).click();
      }
    }

    await dialog.getByRole("textbox", { name: textboxName }).fill(apiKey);
    await dialog.getByRole("button", { name: "Save" }).click();
    await this.clickEnableOrClose(dialog);
  }

  async setEnabledWithMultiConnection(
    name: string,
    opts: {
      aliasFieldLabel: string;
      aliasValue: string;
      serverUrl: string;
      apiKey: string;
      configuredFormAriaLabelPrefix: string;
    },
  ): Promise<void> {
    const dialog = await this.openCard(name);

    const savedForm = dialog.locator(`form[aria-label="${opts.configuredFormAriaLabelPrefix} ${opts.aliasValue}"]`);
    if ((await savedForm.count()) === 0) {
      await dialog.getByRole("button", { name: "Add", exact: true }).click();
      const newForm = dialog.locator("form").last();
      await newForm.getByRole("textbox", { name: opts.aliasFieldLabel }).fill(opts.aliasValue);
      await newForm.getByRole("textbox", { name: "Server URL" }).fill(opts.serverUrl);
      await newForm.getByRole("textbox", { name: "API key" }).fill(opts.apiKey);
      await newForm.getByRole("button", { name: "Save" }).click();
    }

    await savedForm.getByRole("button", { name: "Test" }).click();

    await this.clickEnableOrClose(dialog);
  }

  async setEnabledForOmero(name: string, opts: { username: string; password: string }): Promise<void> {
    const dialog = await this.openCard(name);

    await dialog.getByRole("textbox", { name: "Username" }).fill(opts.username);
    await dialog.getByRole("textbox", { name: "Password" }).fill(opts.password);
    await dialog.getByRole("button", { name: "Connect" }).click();
    await this.page.getByText("Successfully connected to OMERO.").waitFor({ state: "visible", timeout: 10_000 });

    await this.clickEnableOrClose(dialog);
  }
}
