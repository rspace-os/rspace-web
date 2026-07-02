import type { Locator, Page } from "@playwright/test";
import { ToolbarCreateMenu } from "@/__tests__/e2e/components/shared/ToolbarCreateMenu";

export type ViewLayout = "tree" | "list";
export type FolderScope = "folder" | "all";
export type ContentFilter = "labgroup" | "favorites" | "shared" | "templates" | "ontology";

const FILTER_TEST_ID: Record<ContentFilter, string> = {
  labgroup: "toolbar-filter-labgroup",
  favorites: "toolbar-filter-favorites",
  shared: "toolbar-filter-shared",
  templates: "toolbar-filter-templates",
  ontology: "toolbar-filter-ontology",
};

/**
 * Workspace toolbar — the MUI
 * `<header>` row above the record table. Search (simple + advanced) is a
 * separate component, `WorkspaceSearchBar` — it's rendered inline here but
 * has its own source file and is large enough to own its own class.
 *
 * `toolbar-views-2` (folder scope: "Folder view" / "View all") is only
 * present in list view; tree view replaces this whole search/filter section
 * with sort controls (see `WorkspaceTree`).
 *
 * `createMenu` is `ToolbarCreateMenu`, composed rather than owned here — the
 * same React component also backs the Notebook view toolbar (see that file's
 * doc comment for why), so neither toolbar should own it outright.
 */
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
    this.notificationsButton = page.getByTestId("toolbar-notifications");
    this.messagesButton = page.getByTestId("toolbar-messages");
    this.sendMessageButton = page.getByTestId("toolbar-send-message");
    this.calendarButton = page.getByTestId("toolbar-calendar");
    this.layoutMenuButton = page.getByTestId("toolbar-views");
    this.scopeMenuButton = page.getByTestId("toolbar-views-2");
  }

  async switchLayout(to: ViewLayout): Promise<void> {
    await this.layoutMenuButton.click();
    await this.page.getByTestId(to === "tree" ? "toolbar-view-tree" : "toolbar-view-list").click();
  }

  /** List view only — tree view has no folder-scope toggle. */
  async switchScope(to: FolderScope): Promise<void> {
    await this.scopeMenuButton.click();
    await this.page.getByTestId(to === "folder" ? "toolbar-view-folders" : "toolbar-view-all").click();
  }

  async toggleFilter(filter: ContentFilter): Promise<void> {
    await this.page.getByTestId(FILTER_TEST_ID[filter]).click();
  }
}
