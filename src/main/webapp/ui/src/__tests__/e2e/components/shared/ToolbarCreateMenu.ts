import { expect, type Locator, type Page } from "@playwright/test";

export type CreateMenuItem =
  | "Folder"
  | "Notebook"
  | "From Form"
  | "From Template"
  | "From Evernote"
  | "From Protocols.io"
  | "New Form";

const CREATE_ACCESSIBLE_NAME: Record<CreateMenuItem, string> = {
  Folder: "Folder",
  Notebook: "Notebook",
  "From Form": "From Form",
  "From Template": "From Template",
  "From Evernote": "From Evernote",
  "From Protocols.io": "From Protocols.io",
  "New Form": "New Form",
};

export class ToolbarCreateMenu {
  readonly createButton: Locator;

  constructor(private readonly page: Page) {
    this.createButton = page.getByRole("button", { name: "Create a record", exact: true });
  }

  async create(item: CreateMenuItem): Promise<void> {
    await this.select(CREATE_ACCESSIBLE_NAME[item]);
  }

  async createFromCustomForm(name: string): Promise<void> {
    await this.select(name);
  }

  private async select(name: string): Promise<void> {
    await this.createButton.click();
    const menu = this.page.getByRole("menu").filter({ visible: true });
    await expect(menu).toHaveCount(1);
    const menuItem = menu.getByRole("menuitem", { name, exact: true });
    await menuItem.click();
  }
}
