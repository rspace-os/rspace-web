import type { Locator, Page } from "@playwright/test";
import { BatchUserRegistrationComponent } from "@/__tests__/e2e/components/system/accounts/BatchUserRegistrationComponent";
import { BasePage } from "@/__tests__/e2e/pageObjects/BasePage";

export type AccountType = "User" | "PI" | "Community Admin" | "System Admin";

export class CreateAccountPage extends BasePage {
  readonly path = "/system/createAccount";
  readonly batchRegistration: BatchUserRegistrationComponent;

  constructor(page: Page) {
    super(page);
    this.batchRegistration = new BatchUserRegistrationComponent(page);
  }

  async selectTab(tab: AccountType): Promise<void> {
    await this.page.getByRole("link", { name: tab, exact: true }).click();
  }

  get firstNameField(): Locator {
    return this.page.getByRole("textbox", { name: "First name", exact: true });
  }

  get lastNameField(): Locator {
    return this.page.getByRole("textbox", { name: "Last name", exact: true });
  }

  get usernameField(): Locator {
    return this.page.getByRole("textbox", { name: "Minimum 6 alphanumeric characters" });
  }

  get emailField(): Locator {
    return this.page.getByRole("textbox", { name: "E-mail", exact: true });
  }

  get passwordField(): Locator {
    return this.page.getByRole("textbox", { name: "8 - 50 characters", exact: false });
  }

  get confirmPasswordField(): Locator {
    return this.page.getByRole("textbox", { name: "Confirm Password", exact: true });
  }

  get newLabGroupNameField(): Locator {
    return this.page.getByRole("textbox", { name: "New LabGroup name", exact: true });
  }

  private get communityRadios(): Locator {
    return this.page.locator("#communitiesList:visible").getByRole("radio");
  }

  get piCommunityRadio(): Locator {
    return this.communityRadios.first();
  }

  get noneCommunityRadio(): Locator {
    return this.communityRadios.nth(0);
  }

  get allGroupsCommunityRadio(): Locator {
    return this.communityRadios.nth(1);
  }

  get communitySelectionCell(): Locator {
    return this.page.getByRole("cell").filter({ has: this.noneCommunityRadio });
  }

  get systemAdminTabLink(): Locator {
    return this.page.getByRole("link", { name: "System Admin", exact: true });
  }

  get createButton(): Locator {
    return this.page.getByRole("button", { name: "Create", exact: true });
  }

  get labGroupFilterField(): Locator {
    return this.page.getByRole("textbox", { name: "Filter and choose a LabGroup", exact: true });
  }

  async selectLabGroup(groupName: string): Promise<void> {
    await this.labGroupFilterField.fill(groupName);
    const resultsTable = this.page
      .getByRole("table")
      .filter({ has: this.page.getByRole("columnheader", { name: "Group name", exact: true }) })
      .last();
    await resultsTable.getByRole("row", { name: groupName }).getByRole("radio").check();
  }

  duplicateAccountError(): Locator {
    return this.page.getByText("already exists", { exact: false });
  }

  async fillBasicFields(fields: {
    firstName: string;
    lastName: string;
    username: string;
    email: string;
    password: string;
  }): Promise<void> {
    await this.firstNameField.fill(fields.firstName);
    await this.lastNameField.fill(fields.lastName);
    await this.usernameField.fill(fields.username);
    await this.emailField.fill(fields.email);
    await this.passwordField.fill(fields.password);
    await this.confirmPasswordField.fill(fields.password);
  }

  async submitExpectingSuccess(): Promise<void> {
    await this.createButton.click();
    await this.usernameField.waitFor({ state: "detached" });
  }

  async hasBlockedInvalidSubmit(): Promise<boolean> {
    return (await this.page.locator(":invalid").count()) > 0;
  }

  async selectBatchRegistrationTab(): Promise<void> {
    await this.page.getByRole("link", { name: "Batch User Registration", exact: true }).click();
  }

  get batchRegistrationTabLink(): Locator {
    return this.page.getByRole("link", { name: "Batch User Registration", exact: true });
  }
}
