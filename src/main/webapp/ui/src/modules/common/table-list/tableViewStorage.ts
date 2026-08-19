import type { ResolvedCollectionConfig } from "@/modules/common/collection/collectionConfig";
import { parseTableViewState, serializeTableViewState, type TableViewValues } from "./tableViewState";

export function tableViewStorageKey(tableId: string): string {
  return `rspace.tableList.${tableId}.view`;
}

export function loadStoredTableView<TDocument>(
  key: string,
  config: ResolvedCollectionConfig<TDocument>,
): TableViewValues<TDocument> | null {
  try {
    const serialized = window.localStorage.getItem(key);
    if (serialized === null) return null;
    const values = parseTableViewState(serialized, config);
    if (values === null) window.localStorage.removeItem(key);
    return values;
  } catch {
    // Browser privacy settings must not prevent the table from loading.
    return null;
  }
}

export function saveStoredTableView<TDocument>(
  key: string,
  values: TableViewValues<TDocument>,
  config: ResolvedCollectionConfig<TDocument>,
): void {
  try {
    const serialized = serializeTableViewState(values, config);
    if (serialized === null) window.localStorage.removeItem(key);
    else window.localStorage.setItem(key, serialized);
  } catch {
    // Browser privacy settings and storage quotas must not affect the table.
  }
}
