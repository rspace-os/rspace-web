import type { WorkspaceSearchState } from "./workspaceSearchUrl";

/** Dispatched by the legacy workspace.js each time it shows a record list. */
export const RECORD_LIST_SHOWN = "workspaceRecordListShown";

export type WorkspaceSettings = WorkspaceSearchState & { searchMode: boolean };

/** The legacy page's `workspaceSettings` global, set by workspace.jsp. */
export function workspaceSettings(): WorkspaceSettings {
  return (window as unknown as { workspaceSettings: WorkspaceSettings }).workspaceSettings;
}
