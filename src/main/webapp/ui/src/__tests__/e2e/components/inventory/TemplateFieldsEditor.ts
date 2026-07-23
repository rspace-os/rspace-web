import type { Locator, Page } from "@playwright/test";
import { TemplateFieldSettings } from "./TemplateFieldSettings";

export type TemplateFieldType =
  | "Text"
  | "Number"
  | "Choice"
  | "Radio"
  | "Date"
  | "Time"
  | "FormattedText"
  | "Uri"
  | "Attachment"
  | "Link";

const FIELD_TYPE_LABEL: Record<TemplateFieldType, string> = {
  Text: "Plain text",
  Number: "Number",
  Choice: "Choice",
  Radio: "Radio",
  Date: "Date",
  Time: "Time",
  FormattedText: "Formatted text",
  Uri: "URI",
  Attachment: "Attachment",
  Link: "Link",
};

export class TemplateFieldsEditor {
  private readonly fieldSettings: TemplateFieldSettings;

  constructor(
    private readonly page: Page,
    private readonly root: Locator,
  ) {
    this.fieldSettings = new TemplateFieldSettings(this.root, page);
  }

  fieldRegion(name: string): Locator {
    return this.root.getByRole("region", { name, exact: true });
  }

  async addCustomField(type: TemplateFieldType, name: string, option?: string): Promise<void> {
    await this.root.getByRole("button", { name: "Add new field" }).click();

    const newField = this.root.getByRole("region").last();
    await newField.getByRole("group", { name: "Name" }).getByRole("textbox").fill(name);

    const fieldRegion = this.fieldRegion(name);
    await fieldRegion.getByRole("group", { name: "Type" }).getByRole("menuitem").click();
    await this.page
      .getByRole("menu")
      .last()
      .getByRole("menuitem")
      .filter({ has: this.page.getByText(FIELD_TYPE_LABEL[type], { exact: true }) })
      .click();

    if (option !== undefined) {
      await fieldRegion.getByRole("button", { name: "Add Value" }).click();
      await fieldRegion.getByRole("textbox", { name: "Option 1", exact: true }).first().fill(option);
    }
  }

  async setFieldMandatory(name: string): Promise<void> {
    await this.fieldSettings.setMandatory(name);
  }

  async addAllowedRelationType(name: string, relationType: string): Promise<void> {
    await this.fieldSettings.addAllowedRelationType(name, relationType);
  }
}
