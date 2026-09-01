import type { Locator, Page } from "@playwright/test";
import { BasePage } from "@/__tests__/e2e/pageObjects/BasePage";

export class BookingPermissionsPage extends BasePage {
  readonly path = "/booking/bookable-items";

  readonly accessTab: Locator;
  /** The wide-screen presentation. A narrow viewport shows the same rows as cards instead. */
  readonly assignments: Locator;
  readonly addUserOrGroup: Locator;
  readonly saveChanges: Locator;
  readonly savedStatus: Locator;

  constructor(page: Page) {
    super(page);
    this.accessTab = page.getByRole("tab", { name: "Access" });
    this.assignments = page.getByRole("table", { name: "Access assignments" });
    this.addUserOrGroup = page.getByRole("combobox", { name: "Add user or group" });
    this.saveChanges = page.getByRole("button", { name: "Save changes" });
    this.savedStatus = page.getByText("Access changes saved.", { exact: true });
  }

  heading(name: string): Locator {
    return this.page.getByRole("heading", { level: 1, name });
  }

  assignment(detail: string): Locator {
    return this.assignments.getByRole("row").filter({ hasText: detail });
  }

  async openRecord(globalId: string): Promise<void> {
    await this.page.goto(`${this.path}/${globalId}`);
    await this.page.getByText(globalId, { exact: true }).waitFor();
  }

  async openAccess(): Promise<void> {
    await this.accessTab.click();
    await this.assignments.waitFor();
  }

  async addUser(username: string, displayName: string, role: string): Promise<void> {
    await this.addUserOrGroup.fill(username);
    const option = this.page.getByRole("option", { name: new RegExp(displayName) });
    await option.waitFor({ timeout: 15_000 });
    await option.click();
    const assignment = this.assignment(displayName);
    await assignment.waitFor({ timeout: 15_000 });
    await this.chooseRole(displayName, role);
    await this.save();
  }

  /**
   * Opens the row's role menu and picks one option. Callers pass the role key ("BOOKER"), which the
   * menu renders as its translated label ("Booker"), so the match is case-insensitive.
   */
  async chooseRole(displayName: string, role: string): Promise<void> {
    await this.assignment(displayName)
      .getByRole("button", { name: `Direct role for ${displayName}` })
      .click();
    await this.page.getByRole("menuitem", { name: new RegExp(`^${role}$`, "i") }).click();
  }

  async removeAssignment(_detail: string, displayName: string): Promise<void> {
    await this.assignment(displayName)
      .getByRole("button", { name: `Remove ${displayName}` })
      .click();
    await this.save();
  }

  /** The staged-change marker a row carries before the draft is saved. */
  stagedMarker(detail: string): Locator {
    return this.assignment(detail).locator('[title^="Staged"]');
  }

  private async save(): Promise<void> {
    const responsePromise = this.page.waitForResponse(
      (response) =>
        response.request().method() === "PUT" && /\/api\/v2\/booking-configurations\/\d+\/access$/.test(response.url()),
      { timeout: 15_000 },
    );
    await this.saveChanges.click({ timeout: 15_000 });
    const response = await responsePromise;
    if (!response.ok()) {
      throw new Error(`Saving booking access failed with status ${response.status()}: ${await response.text()}`);
    }
    await this.savedStatus.waitFor({ timeout: 15_000 });
  }
}
