import type { Locator, Page } from "@playwright/test";
import { expect } from "@playwright/test";
import { ToolbarCreateMenu } from "@/__tests__/e2e/components/shared/ToolbarCreateMenu";

export type ViewLayout = "tree" | "list";
export type FolderScope = "folder" | "all";
export type ContentFilter = "favorites" | "shared" | "templates" | "ontology";

const FILTER_ACCESSIBLE_NAME: Record<ContentFilter, string> = {
  favorites: "Favorites",
  shared: "Shared with me",
  templates: "Templates",
  ontology: "Ontology files",
};

export class WorkspaceToolbar {
  readonly createMenu: ToolbarCreateMenu;
  readonly notificationsButton: Locator;
  readonly messagesButton: Locator;
  readonly sendMessageButton: Locator;
  readonly calendarButton: Locator;
  readonly layoutMenuButton: Locator;
  readonly scopeMenuButton: Locator;

  constructor(private readonly page: Page) {
    this.createMenu = new ToolbarCreateMenu(page);
    this.notificationsButton = page.getByRole("button", { name: "Notifications", exact: true });
    this.messagesButton = page.getByRole("button", { name: "Received messages", exact: true });
    this.sendMessageButton = page.getByRole("button", { name: "Send a message", exact: true });
    this.calendarButton = page.getByRole("button", { name: "Create a calendar entry", exact: true });
    const viewModeButtons = page.getByRole("button", { name: "View mode", exact: true });
    this.layoutMenuButton = viewModeButtons.first();
    this.scopeMenuButton = viewModeButtons.last();
  }

  private async clickAndWaitForView(click: () => Promise<void>): Promise<void> {
    await Promise.all([this.page.waitForResponse((res) => res.url().includes("/workspace/ajax/view/")), click()]);
  }

  async switchLayout(to: ViewLayout): Promise<void> {
    await this.layoutMenuButton.click();
    await this.page.getByRole("menuitem", { name: to === "tree" ? "Tree view" : "List view", exact: true }).click();
    await (to === "tree" ? this.page.getByRole("tree") : this.page.locator("#file_table")).waitFor({
      state: "visible",
    });
  }

  async switchScope(to: FolderScope): Promise<void> {
    await this.scopeMenuButton.click();
    await this.clickAndWaitForView(() =>
      this.page.getByRole("menuitem", { name: to === "folder" ? "Folder view" : "View all", exact: true }).click(),
    );
  }

  async toggleFilter(filter: ContentFilter): Promise<void> {
    const button = this.page.getByRole("button", { name: FILTER_ACCESSIBLE_NAME[filter], exact: true });
    const wasActive = ((await button.getAttribute("class")) ?? "").includes("active");
    await button.click();
    if (wasActive) {
      await expect(button).not.toHaveClass(/active/);
    } else {
      await expect(button).toHaveClass(/active/);
    }
  }

  async clickLabGroupShortcut(): Promise<void> {
    await this.page.getByRole("button", { name: "Labgroup records", exact: true }).click();
    await this.page.getByText("LabGroups").waitFor({ state: "visible" });
  }
}
