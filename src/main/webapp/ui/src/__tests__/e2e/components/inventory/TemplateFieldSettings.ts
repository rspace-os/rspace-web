import type { Locator, Page } from "@playwright/test";

export class TemplateFieldSettings {
  constructor(
    private readonly customFieldsRoot: Locator,
    private readonly page: Page,
  ) {}

  private field(name: string): Locator {
    return this.customFieldsRoot.getByRole("region", { name, exact: true });
  }

  async setMandatory(name: string): Promise<void> {
    await this.field(name).getByRole("group", { name: "Mandatory" }).getByRole("switch").click();
  }

  async addAllowedRelationType(name: string, relationType: string): Promise<void> {
    const combobox = this.field(name).getByRole("combobox", { name: "Allowed relationship types" });
    await combobox.click();
    await this.page.getByRole("option", { name: relationType, exact: true }).click();
  }
}
