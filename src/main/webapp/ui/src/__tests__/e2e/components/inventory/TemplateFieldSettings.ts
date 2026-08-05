import { expect, type Locator, type Page } from "@playwright/test";

export class TemplateFieldSettings {
  constructor(
    private readonly customFieldsRoot: Locator,
    private readonly page: Page,
  ) {}

  private field(name: string): Locator {
    return this.customFieldsRoot.getByRole("region", { name, exact: true });
  }

  async setMandatory(name: string): Promise<void> {
    const toggle = this.field(name).getByRole("group", { name: "Mandatory" }).getByRole("switch");
    if (!(await toggle.isChecked())) {
      await toggle.click();
    }
    await expect(toggle).toBeChecked();
  }

  async addAllowedRelationType(name: string, relationType: string): Promise<void> {
    const fieldRegion = this.field(name);
    const combobox = fieldRegion.getByRole("combobox", { name: "Allowed relationship types" });
    await combobox.click();
    await this.page.getByRole("option", { name: relationType, exact: true }).click();
    await expect(fieldRegion.getByRole("button", { name: relationType, exact: true })).toBeVisible();
  }
}
