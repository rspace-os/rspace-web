import type { Locator, Page } from "@playwright/test";

export type ViewLayout = "tree" | "list";
export type FolderScope = "folder" | "all";
export type ContentFilter = "labgroup" | "favorites" | "shared" | "templates" | "ontology";

export type CreateMenuItem =
  | "Folder"
  | "Notebook"
  | "Basic Document"
  | "From Form"
  | "From Template"
  | "From Evernote"
  | "From Protocols.io"
  | "New Form";

const CREATE_TEST_ID: Record<CreateMenuItem, string> = {
  Folder: "create-btn-folder",
  Notebook: "create-btn-notebook",
  "Basic Document": "create-btn-basic-document",
  "From Form": "create-btn-from-form",
  "From Template": "create-btn-template",
  "From Evernote": "create-btn-evernote",
  "From Protocols.io": "create-btn-protocols",
  "New Form": "create-btn-new-form",
};

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
 */
export class WorkspaceToolbar {
  readonly createButton: Locator;
  readonly notificationsButton: Locator;
  readonly messagesButton: Locator;
  readonly sendMessageButton: Locator;
  readonly calendarButton: Locator;
  readonly layoutMenuButton: Locator;
  readonly scopeMenuButton: Locator;

  constructor(private readonly page: Page) {
    this.createButton = page.getByTestId("create-btn");
    this.notificationsButton = page.getByTestId("toolbar-notifications");
    this.messagesButton = page.getByTestId("toolbar-messages");
    this.sendMessageButton = page.getByTestId("toolbar-send-message");
    this.calendarButton = page.getByTestId("toolbar-calendar");
    this.layoutMenuButton = page.getByTestId("toolbar-views");
    this.scopeMenuButton = page.getByTestId("toolbar-views-2");
  }

  async create(item: CreateMenuItem): Promise<void> {
    await this.createButton.click();
    await this.page.getByTestId(CREATE_TEST_ID[item]).click();
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
