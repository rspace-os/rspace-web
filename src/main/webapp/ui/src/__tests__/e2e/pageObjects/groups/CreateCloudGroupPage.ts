import type { Locator, Page } from "@playwright/test";
import { BasePage } from "../BasePage";

export interface CloudGroupDetails {
  name: string;
  /** Existing user asked to be PI; omit to become the PI yourself. */
  nominatedPiEmail?: string;
  inviteExistingEmails?: string[];
  inviteNewEmails?: string[];
}

/** Community (cloud) "Create a LabGroup" page and its stepper dialog. */
export class CreateCloudGroupPage extends BasePage {
  readonly path = "/cloud/group/new";
  private readonly dialog: Locator;

  constructor(page: Page) {
    super(page);
    this.dialog = page.getByRole("dialog");
  }

  get resultAlert(): Locator {
    return this.page.getByRole("alert");
  }

  async createGroup(details: CloudGroupDetails): Promise<void> {
    await this.page.getByRole("button", { name: "Create Group", exact: true }).click();

    await this.dialog.getByRole("textbox", { name: "Group Name", exact: true }).fill(details.name);
    await this.next();

    if (details.nominatedPiEmail) {
      await this.dialog.getByRole("radio", { name: /^Nominate a PI/ }).check();
      // The react-select inputs have no accessible name, so scope them by their test-id wrappers.
      await this.chooseUser(this.dialog.locator('[data-test-id="createGroupChoosePI"]'), details.nominatedPiEmail);
    } else {
      await this.dialog.getByRole("radio", { name: "Make me the group PI and invite users", exact: true }).check();
    }
    await this.next();

    for (const email of details.inviteExistingEmails ?? []) {
      await this.chooseUser(this.dialog.locator('[data-test-id="createGroupInviteMembers"]'), email);
    }
    const newUsersInput = this.dialog.getByRole("textbox", { name: "Enter email address", exact: true });
    for (const email of details.inviteNewEmails ?? []) {
      await newUsersInput.fill(email);
      await newUsersInput.press("Enter");
    }
    await this.next();

    await this.dialog.getByRole("heading", { name: "Summary", exact: true }).waitFor();
    await this.dialog.getByRole("button", { name: "Create labGroup", exact: true }).click();
    await this.resultAlert.waitFor();
  }

  private async next(): Promise<void> {
    await this.dialog.getByRole("button", { name: "Next", exact: true }).click();
  }

  private async chooseUser(picker: Locator, email: string): Promise<void> {
    await picker.getByRole("combobox").pressSequentially(email);
    await this.page.getByRole("option", { name: email, exact: true }).click();
  }
}
