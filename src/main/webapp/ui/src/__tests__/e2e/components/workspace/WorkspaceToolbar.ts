import type { Locator, Page } from "@playwright/test";
import { ToolbarCreateMenu } from "@/__tests__/e2e/components/shared/ToolbarCreateMenu";

export type ViewLayout = "tree" | "list";
export type FolderScope = "folder" | "all";
export type ContentFilter = "labgroup" | "favorites" | "shared" | "templates" | "ontology";

const FILTER_ACCESSIBLE_NAME: Record<ContentFilter, string> = {
  labgroup: "Labgroup records",
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

  async switchLayout(to: ViewLayout): Promise<void> {
    await this.layoutMenuButton.click();
    await this.page.getByRole("menuitem", { name: to === "tree" ? "Tree view" : "List view", exact: true }).click();
  }

  async switchScope(to: FolderScope): Promise<void> {
    await this.scopeMenuButton.click();
    await this.page.getByRole("menuitem", { name: to === "folder" ? "Folder view" : "View all", exact: true }).click();
  }

  async toggleFilter(filter: ContentFilter): Promise<void> {
    await this.page.getByRole("button", { name: FILTER_ACCESSIBLE_NAME[filter], exact: true }).click();
  }
}
