import type { Locator } from "@playwright/test";
import {
  PublishShareDialogComponent,
  type SharePermission,
  type WorldSharePermission,
} from "@/__tests__/e2e/components/myrspace/PublishShareDialogComponent";
import { BasePage } from "../BasePage";

export class ManageFormsPage extends BasePage {
  readonly path = "/workspace/editor/form/list";

  private get allFormsRadio(): Locator {
    return this.page.getByRole("radio", { name: "All forms:", exact: true });
  }

  private get searchInput(): Locator {
    return this.page.getByRole("textbox", { name: "Search", exact: true });
  }

  private get searchButton(): Locator {
    return this.page.getByRole("button", { name: "Search", exact: true });
  }

  // Stable legacy JSP table id
  private get formsTable(): Locator {
    return this.page.locator("#templateList");
  }

  async isLoaded(): Promise<void> {
    await this.page.getByRole("heading", { name: "Manage Forms" }).waitFor({ state: "visible" });
  }

  async showAllForms(): Promise<void> {
    await Promise.all([
      this.page.waitForResponse((res) => res.url().includes("/workspace/editor/form/ajax/list")),
      this.allFormsRadio.check(),
    ]);
  }

  async search(formName: string): Promise<void> {
    await Promise.all([
      this.page.waitForResponse((res) => res.url().includes("/workspace/editor/form/ajax/")),
      this.searchInput.fill(formName).then(() => this.searchButton.click()),
    ]);
    await this.formRow(formName).waitFor({ state: "visible" });
  }

  formRow(formName: string): Locator {
    return this.formsTable
      .getByRole("row")
      .filter({ has: this.page.getByRole("link", { name: formName, exact: true }) });
  }

  async checkForm(formName: string): Promise<void> {
    await this.formRow(formName).getByRole("checkbox", { name: "Select form", exact: true }).check();
  }

  private formAction(action: string): Locator {
    return this.page.locator(`#formActions li.formAction.${action}`);
  }

  async publishWithPermissions(formName: string, group: SharePermission, world: WorldSharePermission): Promise<void> {
    await this.checkForm(formName);
    const publishAction = this.formAction("publish");
    await publishAction.waitFor({ state: "visible" });
    await publishAction.click();
    const dialog = new PublishShareDialogComponent(this.page);
    await dialog.waitUntilVisible();
    await dialog.setGroup(group);
    await dialog.setWorld(world);
    await dialog.ok();
  }

  async addToMenu(formName: string): Promise<void> {
    await this.checkForm(formName);
    const addToMenuAction = this.formAction("addToMenu");
    await addToMenuAction.waitFor({ state: "visible" });
    await Promise.all([
      this.page.waitForResponse((res) => res.url().includes("/workspace/editor/form/ajax/menutoggle")),
      addToMenuAction.click(),
    ]);
  }

  isTextInDOM(text: string): Promise<boolean> {
    return this.formsTable.getByText(text, { exact: true }).first().isVisible();
  }
}
