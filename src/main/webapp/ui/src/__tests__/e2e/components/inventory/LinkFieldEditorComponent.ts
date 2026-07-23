import type { Locator, Page } from "@playwright/test";
import { LinkVersionLockDialog } from "./LinkVersionLockDialog";

export class LinkFieldEditorComponent {
  private readonly nameInput: Locator;
  private readonly relationTypeCombobox: Locator;
  private readonly targetGlobalIdInput: Locator;
  private readonly pinVersionButton: Locator;
  private readonly applyButton: Locator;
  private readonly discardButton: Locator;

  constructor(
    private readonly page: Page,
    private readonly root: Locator,
  ) {
    this.nameInput = this.root.getByRole("textbox", { name: "Field name" });

    this.relationTypeCombobox = this.root.getByRole("combobox", { name: "Relation" });
    this.targetGlobalIdInput = this.root.getByRole("textbox", { name: "Target Global ID" });
    this.pinVersionButton = this.root.getByRole("button", { name: "Pin version" });

    this.applyButton = this.root.getByRole("button", { name: /^(Update field|Apply link)$/ });
    this.discardButton = this.root.getByRole("button", { name: /^(Cancel update|Discard link changes)$/ });
  }

  async setName(name: string): Promise<void> {
    await this.nameInput.fill(name);
  }

  async setRelationType(relationType: string): Promise<void> {
    await this.relationTypeCombobox.click();
    await this.page.getByRole("option", { name: relationType, exact: true }).click();
  }

  async setTargetGlobalId(globalId: string): Promise<void> {
    await this.targetGlobalIdInput.fill(globalId);
    await this.targetGlobalIdInput.press("Tab");
  }

  async clearTarget(): Promise<void> {
    await this.targetGlobalIdInput.fill("");
  }

  async isPinVersionEnabled(): Promise<boolean> {
    return this.pinVersionButton.isEnabled();
  }

  private async helperTextFor(input: Locator): Promise<string> {
    const describedBy = await input.getAttribute("aria-describedby");
    return (await this.page.locator(`#${describedBy}`).innerText()).trim();
  }

  async getTargetHelperText(): Promise<string> {
    return this.helperTextFor(this.targetGlobalIdInput);
  }

  async getVersionLabel(): Promise<string> {
    return (await this.pinVersionButton.locator("..").innerText()).trim();
  }

  async openVersionLockDialog(targetGlobalId: string): Promise<LinkVersionLockDialog> {
    await this.pinVersionButton.click();
    const dialog = new LinkVersionLockDialog(this.page, targetGlobalId);
    await dialog.waitForOpen();
    return dialog;
  }

  private waitForTargetCheck(globalId: string): Promise<unknown> {
    const recordId = Number(globalId.slice(2));
    const idPattern = recordId > 0 ? String(recordId) : "\\d+";
    const urlPattern = new RegExp(
      `/api/inventory/v1/(samples|subSamples|containers|instruments|sampleTemplates)/${idPattern}/?$`,
    );
    return this.page
      .waitForResponse((res) => res.request().method() === "GET" && urlPattern.test(res.url()))
      .catch(() => null);
  }

  async apply(): Promise<void> {
    const targetCheck = this.waitForTargetCheck(await this.targetGlobalIdInput.inputValue());
    await this.applyButton.click();
    await Promise.race([
      targetCheck,
      this.root.getByRole("group", { name: "New extra field" }).waitFor({ state: "detached" }),
    ]);
  }

  async applyAndGetTargetHelperText(): Promise<string> {
    const targetCheck = this.waitForTargetCheck(await this.targetGlobalIdInput.inputValue());
    await this.applyButton.click();
    await targetCheck;
    return this.getTargetHelperText();
  }

  async discard(): Promise<void> {
    await this.discardButton.click();
  }
}
