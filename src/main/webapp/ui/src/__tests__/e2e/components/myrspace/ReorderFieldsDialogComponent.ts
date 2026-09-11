import type { Locator, Page } from "@playwright/test";
import type { FormFieldType } from "./FieldEditorDialogComponent";

export type ReorderAction = "Top" | "Up" | "Down" | "Bottom";

export class ReorderFieldsDialogComponent {
  readonly root: Locator;

  constructor(page: Page) {
    this.root = page.getByRole("dialog", { name: "Order Form Fields" });
  }

  async waitUntilVisible(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  async select(fieldName: string, type: FormFieldType): Promise<void> {
    await this.root
      .getByRole("row")
      .filter({ hasText: `${fieldName.toLowerCase()} (${type.toLowerCase()})` })
      .getByRole("radio")
      .check();
  }

  async move(action: ReorderAction): Promise<void> {
    await this.root.getByRole("button", { name: action, exact: true }).click();
  }

  async done(): Promise<void> {
    await this.root.getByRole("button", { name: "Done" }).click();
    await this.root.waitFor({ state: "hidden" });
  }
}
