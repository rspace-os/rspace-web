import type { Page } from "@playwright/test";
import { AppHeader } from "@/__tests__/e2e/components/shared/AppHeader";
import { WorkspacePagination } from "@/__tests__/e2e/components/workspace/WorkspacePagination";
import type { WorkspaceRecordInfoDialog } from "@/__tests__/e2e/components/workspace/WorkspaceRecordInfoDialog";
import { WorkspaceSearchBar } from "@/__tests__/e2e/components/workspace/WorkspaceSearchBar";
import { WorkspaceSelectionBar } from "@/__tests__/e2e/components/workspace/WorkspaceSelectionBar";
import { WorkspaceTable } from "@/__tests__/e2e/components/workspace/WorkspaceTable";
import { WorkspaceToolbar } from "@/__tests__/e2e/components/workspace/WorkspaceToolbar";
import { WorkspaceTree } from "@/__tests__/e2e/components/workspace/WorkspaceTree";
import { BasePage } from "../BasePage";
import { DocumentEditorPage } from "../document/DocumentEditorPage";

export class WorkspacePage extends BasePage {
  readonly path = "/workspace";

  readonly header: AppHeader;
  readonly toolbar: WorkspaceToolbar;
  readonly searchBar: WorkspaceSearchBar;
  readonly table: WorkspaceTable;
  readonly tree: WorkspaceTree;
  readonly selectionBar: WorkspaceSelectionBar;
  readonly pagination: WorkspacePagination;

  constructor(page: Page) {
    super(page);
    this.header = new AppHeader(page);
    this.toolbar = new WorkspaceToolbar(page);
    this.searchBar = new WorkspaceSearchBar(page);
    this.table = new WorkspaceTable(page);
    this.tree = new WorkspaceTree(page);
    this.selectionBar = new WorkspaceSelectionBar(page);
    this.pagination = new WorkspacePagination(page);
  }

  override async open(folderId?: number): Promise<void> {
    await this.page.goto(folderId !== undefined ? `${this.path}/${folderId}` : this.path);
  }

  async isLoaded(): Promise<boolean> {
    try {
      await this.toolbar.createMenu.createButton.waitFor({ state: "visible" });
      return true;
    } catch {
      return false;
    }
  }

  async isTreeView(): Promise<boolean> {
    return this.tree.root.isVisible();
  }

  async findRecord(name: string, { maxPages = 50 }: { maxPages?: number } = {}): Promise<void> {
    if (await this.isTreeView()) {
      throw new Error("findRecord: pagination is list-view only — switch to list view first.");
    }
    for (let i = 0; i < maxPages; i++) {
      if ((await this.table.row(name).count()) > 0) {
        return;
      }
      if (!(await this.pagination.goToNextPage())) {
        break;
      }
    }
    throw new Error(`findRecord: "${name}" not found within ${maxPages} pages.`);
  }

  async openInfoFor(name: string): Promise<WorkspaceRecordInfoDialog> {
    if (await this.isTreeView()) {
      throw new Error("openInfoFor: no Record Info action in tree view — switch to list view first.");
    }
    return this.table.openInfoFor(name);
  }

  async createBasicDocument(): Promise<DocumentEditorPage> {
    await this.toolbar.createMenu.createFromCustomForm("Basic Document");
    await this.page.waitForURL("**/workspace/editor/structuredDocument/**");
    const editor = new DocumentEditorPage(this.page);
    await editor.isLoaded();
    return editor;
  }
}
