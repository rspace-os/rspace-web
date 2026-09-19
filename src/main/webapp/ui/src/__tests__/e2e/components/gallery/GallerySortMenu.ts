import type { Locator, Page } from "@playwright/test";

export type GallerySortField = "Name" | "Modification Date";

const ASCENDING_LABEL: Record<GallerySortField, string> = {
  Name: "Sorted from A to Z",
  "Modification Date": "Sorted oldest first",
};

export class GallerySortMenu {
  readonly button: Locator;
  readonly menu: Locator;

  constructor(private readonly page: Page) {
    this.button = page.getByRole("button", { name: "Sort" });
    this.menu = page.getByRole("menu", { name: "sort listing" });
  }

  async sortBy(field: GallerySortField): Promise<void> {
    await this.button.click();
    const item = this.menu.getByRole("menuitem", { name: field });
    const text = await item.innerText();
    if (text.includes(ASCENDING_LABEL[field])) {
      await this.page.keyboard.press("Escape");
      return;
    }
    await item.click();
  }

  async toggleActiveDirection(): Promise<void> {
    const { item } = await this.findActiveMenuItem();
    await item.click();
  }

  async activeSort(): Promise<GallerySortField> {
    const { text } = await this.findActiveMenuItem();
    await this.page.keyboard.press("Escape");
    return text.split("(")[0].trim() as GallerySortField;
  }

  private async findActiveMenuItem(): Promise<{ item: Locator; text: string }> {
    await this.button.click();
    const items = this.menu.getByRole("menuitem");
    const count = await items.count();
    for (let i = 0; i < count; i++) {
      const item = items.nth(i);
      const text = await item.innerText();
      if (text.includes("(Sorted")) {
        return { item, text };
      }
    }
    await this.page.keyboard.press("Escape");
    throw new Error("findActiveMenuItem(): no menu item's label contained '(Sorted' — none appear active");
  }
}
