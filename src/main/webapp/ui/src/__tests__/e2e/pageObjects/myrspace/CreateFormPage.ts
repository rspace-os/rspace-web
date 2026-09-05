import { FieldEditorDialogComponent } from "@/__tests__/e2e/components/myrspace/FieldEditorDialogComponent";
import { WorkspaceRenameDialog } from "@/__tests__/e2e/components/workspace/WorkspaceRenameDialog";
import { BasePage } from "../BasePage";
import { ManageFormsPage } from "./ManageFormsPage";

export class CreateFormPage extends BasePage {
  readonly path = "/workspace/editor/form/";

  private get saveAndCloseButton() {
    return this.page.getByRole("button", { name: "Save and Close", exact: true });
  }

  private get addFieldButton() {
    return this.page.getByRole("button", { name: "Add Field", exact: true });
  }

  // Legacy jQuery-rendered form-name span
  private get formNameDisplay() {
    return this.page.locator("#documentName .recordName");
  }

  async isLoaded(): Promise<void> {
    await this.saveAndCloseButton.waitFor({ state: "visible" });
  }

  async addNumberField(fieldName: string, { required = true }: { required?: boolean } = {}): Promise<void> {
    await this.addFieldButton.click();
    const dialog = new FieldEditorDialogComponent(this.page);
    await dialog.waitUntilVisible();
    await dialog.selectType("Number");
    await dialog.setName(fieldName);
    await dialog.setRequired(required);
    await dialog.save();
  }

  async rename(newName: string): Promise<void> {
    await this.formNameDisplay.click();
    const dialog = new WorkspaceRenameDialog(this.page);
    await dialog.waitUntilVisible();
    await dialog.submit(newName);
    await this.formNameDisplay.filter({ hasText: newName }).waitFor({ state: "visible" });
  }

  async saveAndClose(): Promise<ManageFormsPage> {
    await this.saveAndCloseButton.click();
    await this.page.waitForURL("**/workspace/editor/form/list**");
    const manageForms = new ManageFormsPage(this.page);
    await manageForms.isLoaded();
    return manageForms;
  }
}
