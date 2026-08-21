import type { Locator, Page } from "@playwright/test";

export type PidinstProvider = "DataCite" | "B2INST";

export interface PidinstDataciteFields {
  server?: "Production" | "Test";
  username?: string;
  password?: string;
  repositoryPrefix?: string;
}

export interface PidinstB2InstFields {
  serverUrl?: string;
  communityId?: string;
  token?: string;
}

export class IdentifierSettingsDialog {
  readonly root: Locator;
  readonly pidinstTab: Locator;
  readonly igsnTab: Locator;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("dialog", { name: "Configure Identifier Registries" });
    this.pidinstTab = this.root.getByRole("tab", { name: "PIDINST" });
    this.igsnTab = this.root.getByRole("tab", { name: "IGSN" });
  }

  async waitForOpen(): Promise<void> {
    await this.pidinstTab.waitFor({ state: "visible" });
  }

  async openPidinstTab(): Promise<void> {
    await this.pidinstTab.click();
  }

  async openIgsnTab(): Promise<void> {
    await this.igsnTab.click();
  }

  async pidinstTabStatusText(): Promise<string> {
    return (await this.pidinstTab.innerText()).replace(/\s+/g, " ").trim();
  }

  async selectPidinstProvider(provider: PidinstProvider): Promise<void> {
    await this.root.getByRole("radio", { name: provider, exact: true }).click();
  }

  async fillPidinstDatacite(fields: PidinstDataciteFields): Promise<void> {
    if (fields.server) {
      await this.root.getByRole("radio", { name: fields.server, exact: true }).click();
    }
    if (fields.username !== undefined) {
      await this.root.getByRole("textbox", { name: "Repository Account ID" }).fill(fields.username);
    }
    if (fields.password !== undefined) {
      await this.root.getByRole("textbox", { name: "Password" }).fill(fields.password);
    }
    if (fields.repositoryPrefix !== undefined) {
      await this.root.getByRole("textbox", { name: "Repository Prefix" }).fill(fields.repositoryPrefix);
    }
  }

  async fillPidinstB2Inst(fields: PidinstB2InstFields): Promise<void> {
    if (fields.serverUrl !== undefined) {
      await this.root.getByRole("textbox", { name: "Server URL" }).fill(fields.serverUrl);
    }
    if (fields.communityId !== undefined) {
      await this.root.getByRole("textbox", { name: "Community ID" }).fill(fields.communityId);
    }
    if (fields.token !== undefined) {
      await this.root.getByRole("textbox", { name: "Token" }).fill(fields.token);
    }
  }

  pidinstEnableToggle(provider: PidinstProvider): Locator {
    return this.root.getByRole("checkbox", { name: `Enable PIDINST for Instruments via ${provider}` });
  }

  async savePidinst(): Promise<void> {
    // AuthStore.updateSystemSettings PUTs to this endpoint and only then
    // resolves; a bare .click() would return before the save request
    // actually lands, so a subsequent close+reopen (which forces a fresh
    // GET) could race it and read stale settings.
    await Promise.all([
      this.page.waitForResponse(
        (res) => res.url().includes("/api/inventory/v1/system/settings") && res.request().method() === "PUT",
      ),
      this.root.getByRole("button", { name: "Save", exact: true }).click(),
    ]);
  }

  async close(): Promise<void> {
    await this.root.getByRole("button", { name: "Close", exact: true }).click();
    await this.root.waitFor({ state: "detached" });
  }
}
