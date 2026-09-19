import type { Page } from "@playwright/test";
import { GroupRaidConnectionsComponent } from "@/__tests__/e2e/components/groups/GroupRaidConnectionsComponent";
import { BasePage } from "../BasePage";

export class GroupViewPage extends BasePage {
  readonly path = "/groups/view";

  readonly raidConnections: GroupRaidConnectionsComponent;

  constructor(
    page: Page,
    private readonly groupId: string | number,
  ) {
    super(page);
    this.raidConnections = new GroupRaidConnectionsComponent(page);
  }

  override async open(): Promise<void> {
    await this.page.goto(`${this.path}/${this.groupId}`);
  }
}
