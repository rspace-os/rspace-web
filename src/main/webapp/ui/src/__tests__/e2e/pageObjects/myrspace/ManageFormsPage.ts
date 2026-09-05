import type { Locator } from "@playwright/test";
import {
  FormAccessDialogComponent,
  type GroupFormPermission,
  type WorldFormPermission,
} from "@/__tests__/e2e/components/myrspace/FormAccessDialogComponent";
import { BasePage } from "../BasePage";
import { CreateFormPage } from "./CreateFormPage";

export type FormAction =
  | "Delete"
  | "Publish"
  | "Unpublish"
  | "Permissions"
  | "Duplicate"
  | "Add to Menu"
  | "Remove from Menu";

export class ManageFormsPage extends BasePage {
  readonly path = "/workspace/editor/form/list?orderBy=name&sortOrder=ASC&userFormsOnly=true";

  async waitUntilLoaded(): Promise<void> {
    await this.page.getByRole("heading", { name: "Manage Forms" }).waitFor({ state: "visible" });
  }

  get formsTable(): Locator {
    return this.page.getByRole("table");
  }

  formRow(name: string): Locator {
    return this.formsTable.getByRole("row").filter({
      has: this.page.getByRole("link", { name, exact: true }),
    });
  }

  async showAllForms(): Promise<void> {
    await this.page.getByRole("radio", { name: "All forms:" }).check();
    await this.page.waitForLoadState("networkidle");
  }

  async search(query: string): Promise<void> {
    await this.page.getByRole("textbox", { name: "Search" }).fill(query);
    await this.page.getByRole("button", { name: "Search" }).click();
    await this.page.getByRole("button", { name: "Clear search" }).waitFor({ state: "visible" });
  }

  async selectForm(name: string): Promise<void> {
    await this.formRow(name).getByRole("checkbox", { name: "Select form" }).check();
  }

  action(name: FormAction): Locator {
    return this.page.getByRole("listitem").filter({
      has: this.page.getByText(name, { exact: true }),
    });
  }

  async configureAccess(name: string, group: GroupFormPermission, world: WorldFormPermission): Promise<void> {
    await this.selectForm(name);
    await this.action("Publish").click();
    const dialog = new FormAccessDialogComponent(this.page);
    await dialog.waitUntilVisible();
    await dialog.setGroup(group);
    await dialog.setWorld(world);
    await dialog.confirm();
  }

  async toggleMenu(name: string, action: "Add to Menu" | "Remove from Menu"): Promise<void> {
    await this.selectForm(name);
    await this.action(action).click();
    await this.action(action).waitFor({ state: "hidden" });
  }

  async duplicate(name: string): Promise<void> {
    await this.selectForm(name);
    await this.action("Duplicate").click();
    await this.page.waitForLoadState("networkidle");
  }

  async unpublish(name: string): Promise<void> {
    await this.selectForm(name);
    await this.action("Unpublish").click();
    await this.page.waitForLoadState("networkidle");
  }

  async delete(name: string): Promise<void> {
    await this.selectForm(name);
    await this.action("Delete").click();
    const dialog = this.page.getByRole("dialog").filter({ hasText: "delete" });
    await dialog.getByRole("button", { name: "Confirm" }).click();
    await this.formRow(name).waitFor({ state: "hidden" });
  }

  async edit(name: string): Promise<CreateFormPage> {
    await this.formRow(name).getByRole("link", { name, exact: true }).click();
    const page = new CreateFormPage(this.page);
    await page.waitUntilLoaded();
    return page;
  }

  status(name: string): Locator {
    return this.formRow(name).getByRole("cell").last();
  }

  owner(name: string): Locator {
    return this.formRow(name).getByRole("cell").nth(3);
  }

  get resultRows(): Locator {
    return this.formsTable.getByRole("row").filter({ has: this.page.getByRole("checkbox", { name: "Select form" }) });
  }
}
