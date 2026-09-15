import type { Page } from "@playwright/test";
import { UserPickerComponent } from "@/__tests__/e2e/components/system/groups/UserPickerComponent";
import { BasePage } from "@/__tests__/e2e/pageObjects/BasePage";

export type GroupType = "Lab Group" | "Project Group";

export class GroupAdminPage extends BasePage {
  readonly path = "/groups/admin/";

  readonly piPicker: UserPickerComponent;
  readonly ownerPicker: UserPickerComponent;
  readonly memberPicker: UserPickerComponent;

  constructor(page: Page) {
    super(page);
    this.piPicker = new UserPickerComponent(page, "Available PIs", "Group PIs");
    this.ownerPicker = new UserPickerComponent(page, "Available users", "Group owners");
    this.memberPicker = new UserPickerComponent(page, "Available users", "Group members");
  }

  override async open(groupId?: number): Promise<void> {
    await this.page.goto(groupId !== undefined ? `${this.path}${groupId}` : `${this.path}?new`);
  }

  async setName(name: string): Promise<void> {
    await this.page.getByRole("textbox", { name: "Group's identifying name" }).fill(name);
  }

  async selectGroupType(type: GroupType): Promise<void> {
    await this.page.getByRole("combobox").click();
    await this.page.getByRole("option", { name: type, exact: true }).click();
  }

  async submit(): Promise<void> {
    await this.page.getByRole("button", { name: "Submit" }).click();
    await this.page.waitForURL((url) => url.pathname.includes("/groups/view/"));
  }
}
