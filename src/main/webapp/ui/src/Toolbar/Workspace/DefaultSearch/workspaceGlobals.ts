import type { WorkspaceSearchState } from "./workspaceSearchUrl";

/** Dispatched by the legacy workspace.js each time it shows a record list. */
export const RECORD_LIST_SHOWN = "workspaceRecordListShown";

/** The id of the Workspace simple-search text box. */
export const SEARCH_INPUT_ID = "workspaceSearchInput";

/** The id of the toolbar's "View mode" button, shown in both list and tree view. */
export const VIEW_MODE_BUTTON_ID = "workspaceViewModeButton";

export type WorkspaceSettings = WorkspaceSearchState & { searchMode: boolean };

/** The legacy page's `workspaceSettings` global, set by workspace.jsp. */
export function workspaceSettings(): WorkspaceSettings {
  return (window as unknown as { workspaceSettings: WorkspaceSettings }).workspaceSettings;
}
