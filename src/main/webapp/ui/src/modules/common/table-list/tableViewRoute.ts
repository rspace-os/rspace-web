import { defaultParseSearch, redirect } from "@tanstack/react-router";
import { parameterNames, type TableViewDescriptor, tableViewStorageKey } from "./persistence";
import { isRsqlSyntaxValid } from "./rsql";
import {
  type RawTableView,
  rawTableViewKeys,
  readRawTableView,
  readStoredTableView,
  removeStoredTableView,
  searchParamsFromRouterSearch,
  writeRawTableView,
} from "./utils/viewStorage";

type RouteLocation = { searchStr: string };
type RouteMatch = { search: unknown };

/** Stored parameters for a table whose URL names none of its own, or `null` when nothing is restored. */
function restoredParameters(table: TableViewDescriptor, present: URLSearchParams): Record<string, string> | null {
  const names = parameterNames(table);
  // A key with an empty value, such as `records.q=`, is present: the URL owns the view.
  if (Object.values(names).some((name) => present.has(name))) return null;
  const key = tableViewStorageKey(table);
  const stored = readRawTableView(readStoredTableView(key), table.defaultColumns);
  if (stored === null) return null;
  if (stored === "malformed") {
    // An "issue" table keeps it for RestoredViewIssue.
    if (table.malformed === "discard") removeStoredTableView(key);
    return null;
  }
  const entries = rawTableViewKeys.flatMap((member) => {
    const value = stored[member];
    return value === null ? [] : [[names[member], value] as const];
  });
  if (entries.length === 0) {
    removeStoredTableView(key);
    return null;
  }
  return Object.fromEntries(entries);
}

function save(table: TableViewDescriptor, search: unknown) {
  const names = parameterNames(table);
  const params = searchParamsFromRouterSearch(search);
  const raw: RawTableView = {
    search: params.get(names.search),
    where: params.get(names.where),
    columns: params.get(names.columns),
    sort: params.get(names.sort),
  };
  const key = tableViewStorageKey(table);
  if (rawTableViewKeys.some((member) => raw[member] !== null)) {
    if (raw.where !== null && !isRsqlSyntaxValid(raw.where)) removeStoredTableView(key);
    else writeRawTableView(key, raw, table.defaultColumns);
    return;
  }
  // Absent parameters: the user returned to the defaults, unless malformed storage waits for RestoredViewIssue.
  const stored = readRawTableView(readStoredTableView(key), table.defaultColumns);
  if (stored !== "malformed" || table.malformed === "discard") removeStoredTableView(key);
}

/**
 * Route options that restore each persisted table's stored view when the route is entered and save its URL view
 * on every navigation within the route. Spread into the route of every page that renders a persisted table.
 */
export function tableViewRouteOptions(tables: readonly TableViewDescriptor[]) {
  const saveAll = (match: RouteMatch) => {
    for (const table of tables) save(table, match.search);
  };
  return {
    beforeLoad: ({ cause, location }: { cause: "enter" | "stay" | "preload"; location: RouteLocation }) => {
      // Edits, Back and Forward within the route ("stay") and hover preloads never restore.
      if (cause !== "enter") return;
      const present = new URLSearchParams(location.searchStr);
      const collected = Object.assign({}, ...tables.map((table) => restoredParameters(table, present) ?? {}));
      if (Object.keys(collected).length === 0) return;
      // Parse as Router parses a URL that nuqs wrote, so the restored URL matches one an edit would write.
      const restored = defaultParseSearch(`?${new URLSearchParams(collected)}`);
      throw redirect({
        search: (previous: unknown) => Object.assign({}, previous, restored),
        hash: true,
        state: true,
        replace: true,
      });
    },
    onEnter: saveAll,
    onStay: saveAll,
  };
}
