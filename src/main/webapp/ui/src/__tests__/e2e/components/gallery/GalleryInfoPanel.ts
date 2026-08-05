import { expect, type Locator, type Page } from "@playwright/test";

export type GalleryInfoPanelDetail = "Global ID" | "Owner" | "Type" | "Size" | "Created" | "Modified" | "Version";

export class GalleryInfoPanel {
  readonly root: Locator;
  readonly viewButton: Locator;
  readonly descriptionInput: Locator;
  readonly linkedDocumentsGrid: Locator;
  readonly relatedInventoryGrid: Locator;
  readonly relatedInventoryHeading: Locator;

  readonly expandToggleButton: Locator;

  constructor(page: Page) {
    this.root = page.getByRole("region", { name: "info panel" });
    this.viewButton = this.root.getByRole("button", { name: "View" });
    this.descriptionInput = this.root.getByRole("textbox", { name: "No description" });
    this.linkedDocumentsGrid = this.root.getByRole("grid").nth(0);
    this.relatedInventoryGrid = this.root.getByRole("grid").nth(1);
    this.relatedInventoryHeading = this.root.getByRole("heading", {
      level: 4,
      name: "Related inventory items",
    });
    this.expandToggleButton = this.root.getByRole("button").first();
  }

  relatedInventoryRow(name: string): Locator {
    return this.relatedInventoryGrid.getByRole("row").filter({ hasText: name });
  }

  async waitUntilSelected(name: string): Promise<void> {
    const fileName = this.root.getByRole("heading", { level: 3 }).or(this.root.getByRole("textbox", { name: "Name" }));
    await expect(fileName).toBeVisible();
    await expect.poll(() => this.getFileName()).toBe(name);
  }

  async getFileName(): Promise<string> {
    const heading = this.root.getByRole("heading", { level: 3 });
    if ((await heading.count()) > 0) {
      return heading.innerText();
    }
    return this.root.getByRole("textbox", { name: "Name" }).inputValue();
  }

  async detail(name: GalleryInfoPanelDetail): Promise<string> {
    return this.root.locator(`dt:has-text("${name}") + dd`).innerText();
  }
}
