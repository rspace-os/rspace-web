import type { Locator, Page } from "@playwright/test";
import { GroupRaidConnectionsComponent } from "@/__tests__/e2e/components/groups/GroupRaidConnectionsComponent";
import { BasePage } from "../BasePage";

export class GroupViewPage extends BasePage {
  readonly path = "/groups/view";

  readonly raidConnections: GroupRaidConnectionsComponent;

  constructor(page: Page) {
    super(page);
    this.raidConnections = new GroupRaidConnectionsComponent(page);
  }

  /** Omit groupId to view the current user's own PI group (mirrors the "My RSpace" nav link). */
  override async open(groupId?: string | number): Promise<void> {
    await this.page.goto(groupId !== undefined ? `${this.path}/${groupId}` : "/groups/viewPIGroup");
  }

  private enforceOntologiesButton(): Locator {
    return this.page
      .getByRole("button", { name: "enforce Ontologies", exact: true })
      .or(this.page.getByRole("button", { name: "un-enforce Ontologies", exact: true }));
  }

  async isOntologiesEnforced(): Promise<boolean> {
    return this.page.getByRole("button", { name: "un-enforce Ontologies", exact: true }).isVisible();
  }

  /** Toggles the group's "Enforce Ontologies" setting, confirming the dialog RSpace shows either way. */
  async toggleEnforceOntologies(): Promise<void> {
    const wasEnforced = await this.isOntologiesEnforced();
    await this.enforceOntologiesButton().click();
    const dialog = this.page.getByRole("dialog");
    await dialog.getByRole("button", { name: "Confirm", exact: true }).click();
    const expectedLabel = wasEnforced ? "enforce Ontologies" : "un-enforce Ontologies";
    await this.page.getByRole("button", { name: expectedLabel, exact: true }).waitFor({ state: "visible" });
  }
}
