import { expect, type Locator, type Page } from "@playwright/test";

export type CreateMenuItem =
  | "Folder"
  | "Notebook"
  | "New entry"
  | "From Form"
  | "From Template"
  | "From Protocols.io"
  | "New Form";

const CREATE_ACCESSIBLE_NAME: Record<CreateMenuItem, string> = {
  Folder: "Folder",
  Notebook: "Notebook",
  "New entry": "New entry",
  "From Form": "From Form",
  "From Template": "From Template",
  "From Protocols.io": "From Protocols.io",
  "New Form": "New Form",
};

export class ToolbarCreateMenu {
  readonly createButton: Locator;
  readonly availableActions: Locator;
  private readonly menu: Locator;

  constructor(private readonly page: Page) {
    this.createButton = page.getByRole("button", { name: "Create a record", exact: true });
    this.menu = page.getByRole("menu").filter({ visible: true });
    this.availableActions = this.menu.getByRole("menuitem");
  }

  async create(item: CreateMenuItem): Promise<void> {
    await this.select(CREATE_ACCESSIBLE_NAME[item]);
  }

  async open(): Promise<void> {
    await this.createButton.click();
    await expect(this.menu).toBeVisible();
  }

  async close(): Promise<void> {
    await this.menu.press("Escape");
    await this.menu.waitFor({ state: "hidden" });
  }

  /**
   * Uses the form's own menu entry when it has one, otherwise "From Form" > "Choose a form" (a
   * paginated picker). Per-form entries load asynchronously (createMenuEntries) and "From Form"
   * renders in the same update, so its appearance means the direct entries are complete.
   */
  async createFromCustomForm(name: string): Promise<void> {
    await this.createButton.click();
    const menu = this.page.getByRole("menu").filter({ visible: true });
    await expect(menu).toHaveCount(1);
    const fromForm = menu.getByRole("menuitem", { name: "From Form", exact: true });
    await fromForm.waitFor({ state: "visible" });
    const directItem = menu.getByRole("menuitem", { name, exact: true });
    if ((await directItem.count()) > 0) {
      await directItem.click();
      return;
    }
    await fromForm.click();
    const dialog = this.page.getByRole("dialog", { name: "Choose a form" });
    await dialog.waitFor({ state: "visible" });
    await dialog.getByRole("link", { name, exact: true }).click();
  }

  private async select(name: string): Promise<void> {
    await this.createButton.click();
    const menu = this.page.getByRole("menu").filter({ visible: true });
    await expect(menu).toHaveCount(1);
    const menuItem = menu.getByRole("menuitem", { name, exact: true });
    await menuItem.click();
  }
}
