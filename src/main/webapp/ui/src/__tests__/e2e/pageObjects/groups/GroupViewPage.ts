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

  /** groupId stays optional only to match BasePage.open(); every caller views a specific group. */
  override async open(groupId?: string | number): Promise<void> {
    if (groupId === undefined) {
      throw new Error("GroupViewPage.open() needs a groupId.");
    }
    await this.page.goto(`${this.path}/${groupId}`);
  }

  private enforceOntologiesButton(): Locator {
    return this.page
      .getByRole("button", { name: "enforce Ontologies", exact: true })
      .or(this.page.getByRole("button", { name: "un-enforce Ontologies", exact: true }));
  }

  /** GroupOntologiesManager renders neither button until its status request returns. */
  async isOntologiesEnforced(): Promise<boolean> {
    await this.enforceOntologiesButton().waitFor({ state: "visible" });
    return this.page.getByRole("button", { name: "un-enforce Ontologies", exact: true }).isVisible();
  }

  private bioPortalButton(allowed: boolean): Locator {
    const name = allowed ? "disallow BioPortal Ontologies" : "allow BioPortal Ontologies";
    return this.page.getByRole("button", { name, exact: true });
  }

  /** GroupBioOntologiesManager shows "disallow" once allowed; it loads asynchronously, so wait for either state. */
  async isBioPortalOntologiesAllowed(): Promise<boolean> {
    await this.bioPortalButton(true).or(this.bioPortalButton(false)).waitFor({ state: "visible" });
    return this.bioPortalButton(true).isVisible();
  }

  async setBioPortalOntologiesAllowed(allowed: boolean): Promise<void> {
    if ((await this.isBioPortalOntologiesAllowed()) === allowed) {
      return;
    }
    await this.bioPortalButton(!allowed).click();
    const [response] = await Promise.all([
      this.page.waitForResponse((res) =>
        res.url().includes(allowed ? "/groups/ajax/allowBioOntologies/" : "/groups/ajax/disallowBioOntologies/"),
      ),
      this.page.getByRole("dialog").getByRole("button", { name: "Confirm", exact: true }).click(),
    ]);
    if (!response.ok()) {
      throw new Error(`Changing BioPortal Ontologies failed: ${response.status()} ${response.statusText()}`);
    }
    await this.bioPortalButton(allowed).waitFor({ state: "visible" });
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
