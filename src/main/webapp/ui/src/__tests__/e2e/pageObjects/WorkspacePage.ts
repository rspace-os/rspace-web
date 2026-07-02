import type { Locator, Page } from "@playwright/test";
import type { WorkspaceRecordInfoDialog } from "../components/WorkspaceRecordInfoDialog";
import { WorkspaceSearchBar } from "../components/WorkspaceSearchBar";
import { WorkspaceSelectionBar } from "../components/WorkspaceSelectionBar";
import { WorkspaceTable } from "../components/WorkspaceTable";
import { WorkspaceToolbar } from "../components/WorkspaceToolbar";
import { WorkspaceTree } from "../components/WorkspaceTree";
import { BasePage } from "./BasePage";
import { DocumentEditorPage } from "./DocumentEditorPage";

export class WorkspacePage extends BasePage {
  readonly path = "/workspace";
  readonly createButton: Locator;

  readonly toolbar: WorkspaceToolbar;
  readonly searchBar: WorkspaceSearchBar;
  readonly table: WorkspaceTable;
  readonly tree: WorkspaceTree;
  readonly selectionBar: WorkspaceSelectionBar;

  constructor(page: Page) {
    super(page);
    this.createButton = page.getByTestId("create-btn");
    this.toolbar = new WorkspaceToolbar(page);
    this.searchBar = new WorkspaceSearchBar(page);
    this.table = new WorkspaceTable(page);
    this.tree = new WorkspaceTree(page);
    this.selectionBar = new WorkspaceSelectionBar(page);
  }

  async isLoaded(): Promise<boolean> {
    try {
      await this.createButton.waitFor({ state: "visible" });
      return true;
    } catch {
      return false;
    }
  }

  async isTreeView(): Promise<boolean> {
    return this.tree.root.isVisible();
  }

  /** Delegates to `table` or `tree` depending on the active layout. */
  async openInfoFor(name: string): Promise<WorkspaceRecordInfoDialog> {
    if (await this.isTreeView()) {
      throw new Error("openInfoFor: no Record Info action in tree view — switch to list view first.");
    }
    return this.table.openInfoFor(name);
  }

  /**
   * Creates a new basic document via the Create menu and waits until the
   * editor is ready. Always returns in edit mode (`#editingStatus` visible).
   */
  async createBasicDocument(): Promise<DocumentEditorPage> {
    await this.createButton.click();
    await this.page.getByTestId("create-btn-basic-document").click();
    await this.page.waitForURL(/\/workspace\/editor\/structuredDocument\//);
    const editor = new DocumentEditorPage(this.page);
    await editor.isLoaded();
    return editor;
  }
}
