import type { Locator } from "@playwright/test";
import { AppriseAlertComponent } from "@/__tests__/e2e/components/system/AppriseAlertComponent";
import { ChangePiDialogComponent } from "@/__tests__/e2e/components/system/groups/ChangePiDialogComponent";
import { ChangeRoleDialogComponent } from "@/__tests__/e2e/components/system/groups/ChangeRoleDialogComponent";
import { InviteMembersDialogComponent } from "@/__tests__/e2e/components/system/groups/InviteMembersDialogComponent";
import { RenameGroupDialogComponent } from "@/__tests__/e2e/components/system/groups/RenameGroupDialogComponent";
import { SendMessageDialogComponent } from "@/__tests__/e2e/components/system/groups/SendMessageDialogComponent";
import { BasePage } from "@/__tests__/e2e/pageObjects/BasePage";

/** Group details/membership view. */
export class GroupDetailsPage extends BasePage {
  readonly path = "/groups/view";

  async openGroup(groupId: number): Promise<void> {
    await this.page.goto(`${this.path}/${groupId}`);
  }

  get heading(): Locator {
    return this.page.getByRole("heading", { level: 2 });
  }

  get sharedFolderLink(): Locator {
    return this.page.getByRole("link").filter({ has: this.page.getByRole("img", { name: "Folder", exact: true }) });
  }

  memberRow(username: string): Locator {
    return this.page.locator("#grpDetails").getByRole("row").filter({ hasText: username });
  }

  activityRow(fullName: string): Locator {
    return this.page.locator("#groupActivity").getByRole("row").filter({ hasText: fullName });
  }

  async rename(newName: string): Promise<void> {
    await this.page.getByRole("button", { name: "Rename", exact: true }).click();
    const dialog = new RenameGroupDialogComponent(this.page);
    await dialog.waitUntilVisible();
    await dialog.submit(newName);
    await this.heading.filter({ hasText: newName }).waitFor({ state: "visible" });
  }

  get changePiButton(): Locator {
    return this.page.getByRole("button", { name: "Change PI", exact: true });
  }

  async changePi(newPiFullName: string): Promise<void> {
    await this.changePiButton.click();
    const dialog = new ChangePiDialogComponent(this.page);
    await dialog.waitUntilVisible();
    await dialog.submit(newPiFullName);
    await this.page.waitForLoadState("load");
  }

  async clickChangePiExpectingNoCandidate(): Promise<AppriseAlertComponent> {
    await this.changePiButton.click();
    const alert = new AppriseAlertComponent(this.page);
    await alert.waitUntilVisible();
    return alert;
  }

  get deleteGroupButton(): Locator {
    return this.page.getByRole("button", { name: "Delete Group", exact: true });
  }

  async deleteGroup(): Promise<void> {
    await this.deleteGroupButton.click();
    await this.page.getByRole("dialog", { name: "Confirm Deletion" }).getByRole("button", { name: "Confirm" }).click();
    await this.page.waitForURL((url) => url.pathname === "/userform");
  }

  async requestCollaborationGroup(withUsername: string): Promise<void> {
    await this.page.getByRole("button", { name: "Create Collaboration Group", exact: true }).click();
    const dialog = new SendMessageDialogComponent(this.page);
    await dialog.waitUntilVisible();
    await dialog.sendTo(withUsername);
  }

  async inviteAnotherPiToCollaboration(withUsername: string): Promise<void> {
    await this.page.getByRole("button", { name: "Invite a New PI", exact: true }).click();
    const dialog = new SendMessageDialogComponent(this.page);
    await dialog.waitUntilVisible();
    await dialog.sendTo(withUsername);
  }

  async inviteMember(username: string): Promise<void> {
    await this.page.getByRole("button", { name: "Invite", exact: true }).click();
    const dialog = new InviteMembersDialogComponent(this.page);
    await dialog.waitUntilVisible();
    await dialog.invite(username);
  }

  async leaveCollaboration(): Promise<void> {
    await this.page.getByRole("button", { name: "Leave Collaboration", exact: true }).click();
    await this.page.waitForURL((url) => url.pathname === "/userform");
  }

  memberChangeRoleButton(username: string): Locator {
    return this.memberRow(username).getByRole("button", { name: "Change Role", exact: true });
  }

  memberRemoveButton(username: string): Locator {
    return this.memberRow(username).getByRole("button", { name: "Remove", exact: true });
  }

  async makeMemberLabAdmin(username: string, canViewAllDocuments: boolean): Promise<void> {
    await this.memberChangeRoleButton(username).click();
    const dialog = new ChangeRoleDialogComponent(this.page);
    await dialog.waitUntilVisible();
    await dialog.makeLabAdmin(canViewAllDocuments);
  }

  async makeMemberUser(username: string): Promise<void> {
    await this.memberChangeRoleButton(username).click();
    const dialog = new ChangeRoleDialogComponent(this.page);
    await dialog.waitUntilVisible();
    await dialog.makeUser();
  }

  get editProfileButton(): Locator {
    return this.page.getByRole("button", { name: "Edit profile settings", exact: true });
  }

  get piCanEditAllWorkCheckbox(): Locator {
    return this.page.getByRole("checkbox", { name: "PI can edit all work in this lab group.", exact: true });
  }

  async setPiCanEditAllWork(enabled: boolean): Promise<void> {
    await this.editProfileButton.click();
    await this.piCanEditAllWorkCheckbox.setChecked(enabled);
    const saveButton = this.page.getByRole("button", { name: "Save", exact: true });
    await Promise.all([
      this.page.waitForResponse((res) => res.url().includes("/groups/editProfile/") && res.status() === 200),
      this.page.waitForResponse(
        (res) => res.url().includes("/groups/ajax/admin/changePiCanEditAll/") && res.status() === 200,
      ),
      saveButton.click(),
    ]);
    await saveButton.waitFor({ state: "hidden" });
  }
}
