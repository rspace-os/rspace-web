import { expect, type Locator, type Page } from "@playwright/test";
import { LinkFieldEditorComponent } from "./LinkFieldEditorComponent";
import { TemplateFieldSettings } from "./TemplateFieldSettings";

export class CustomFieldsEditor {
  private readonly addFieldButton: Locator;
  private readonly fieldTypeSelect: Locator;
  private readonly fieldSettings: TemplateFieldSettings;

  constructor(
    private readonly page: Page,
    private readonly root: Locator,
  ) {
    this.fieldSettings = new TemplateFieldSettings(this.root, page);
    this.addFieldButton = this.root.getByRole("button", { name: "Add new field", exact: true });
    this.fieldTypeSelect = this.root.getByRole("combobox", { name: "Field type", exact: true });
  }

  async addNewLinkField(name: string): Promise<LinkFieldEditorComponent> {
    await this.startNewField(name);
    await this.fieldTypeSelect.click();
    await this.page.getByRole("option", { name: "Link", exact: true }).click();
    return new LinkFieldEditorComponent(this.page, this.root);
  }

  async addNewTextField(name: string): Promise<void> {
    await this.startNewField(name);
    await this.root.getByRole("button", { name: "Update field", exact: true }).click();
  }

  async startNewField(name: string): Promise<void> {
    const nameInput = this.root.getByRole("textbox", { name: "Field name" });

    await expect(async () => {
      if (!(await nameInput.isVisible())) {
        await this.addFieldButton.click();
      }
      await nameInput.waitFor({ state: "visible", timeout: 2_000 });
    }).toPass();
    await nameInput.fill(name);
  }

  async getNewFieldNameHelperText(): Promise<string> {
    return (await this.root.getByRole("group", { name: "Field name" }).locator("p").innerText()).trim();
  }

  linkFieldEditor(): LinkFieldEditorComponent {
    return new LinkFieldEditorComponent(this.page, this.root);
  }

  async editLinkField(name: string): Promise<LinkFieldEditorComponent> {
    await this.root.getByText(name, { exact: true }).first().waitFor({ state: "visible" });
    const settingsButton = this.root.getByRole("button", { name: "Field settings" });
    await settingsButton.evaluate((el) => el.scrollIntoView({ block: "center" }));
    await settingsButton.click();
    return new LinkFieldEditorComponent(this.page, this.root);
  }

  fieldVersionLabel(label: string): Locator {
    return this.root.getByText(label, { exact: true });
  }

  linkFieldCard(name: string): Locator {
    return this.root.getByLabel(`Link field ${name}`, { exact: true });
  }

  async setFieldMandatory(name: string): Promise<void> {
    await this.fieldSettings.setMandatory(name);
  }

  async addAllowedRelationType(name: string, relationType: string): Promise<void> {
    await this.fieldSettings.addAllowedRelationType(name, relationType);
  }
}
