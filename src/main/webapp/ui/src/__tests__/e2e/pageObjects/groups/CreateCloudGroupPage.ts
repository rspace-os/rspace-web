import { expect, type Locator, type Page } from "@playwright/test";
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
    this.dialog = page.getByRole("dialog", { name: "Create group" });
  }

  resultAlert(groupName: string): Locator {
    return this.page.getByRole("alert").filter({ hasText: groupName });
  }

  async createGroup(details: CloudGroupDetails): Promise<void> {
    await this.page.getByRole("button", { name: "Create Group", exact: true }).click();

    await this.dialog.getByRole("textbox", { name: "Group Name", exact: true }).fill(details.name);
    await this.next();

    if (details.nominatedPiEmail) {
      await this.dialog.getByRole("radio", { name: "Nominate a PI" }).check();
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
      await expect(newUsersInput).toHaveValue("");
    }
    await this.next();

    await this.dialog.getByRole("heading", { name: "Summary", exact: true }).waitFor();
    const response = this.page.waitForResponse(
      (res) => res.request().method() === "POST" && new URL(res.url()).pathname === "/cloud/createCloudGroup2",
    );
    await this.dialog.getByRole("button", { name: "Create labGroup", exact: true }).click();
    // Failures show only as dialog text, so surface the server's error instead of timing out on the toast.
    const res = await response;
    const body = (await res.json().catch(() => undefined)) as { success?: boolean } | undefined;
    if (!res.ok() || body?.success !== true) {
      throw new Error(`Group creation failed: HTTP ${res.status()} ${JSON.stringify(body)}`);
    }
    await this.resultAlert(details.name).waitFor();
  }

  private async next(): Promise<void> {
    await this.dialog.getByRole("button", { name: "Next", exact: true }).click();
  }

  private async chooseUser(picker: Locator, email: string): Promise<void> {
    await picker.getByRole("combobox").pressSequentially(email);
    await this.page.getByRole("option", { name: email, exact: true }).click();
  }
}
