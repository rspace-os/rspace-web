import { RECORD_LIST_SHOWN, workspaceSettings } from "./workspaceGlobals";
import { parseSearchQuery, toSearchQuery } from "./workspaceSearchUrl";

function syncAddressBar() {
  const settings = workspaceSettings();
  if (settings.searchMode) {
    window.history.replaceState(window.history.state, "", `/workspace?${toSearchQuery(settings)}`);
  } else if (parseSearchQuery(window.location.search).kind !== "none") {
    window.history.replaceState(window.history.state, "", "/workspace");
  }
}

/**
 * Keeps the address bar in step with the Workspace search, so that a search
 * can be bookmarked or shared. Returns a function that removes the listener.
 */
export function installAddressBarSync(): () => void {
  window.addEventListener(RECORD_LIST_SHOWN, syncAddressBar);
  return () => window.removeEventListener(RECORD_LIST_SHOWN, syncAddressBar);
}
