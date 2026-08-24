import type { ResolvedCollectionConfig } from "@/modules/common/collection/collectionConfig";
import { rsqlSelectors } from "./rsql/rsqlCodec";
import {
  parseTableViewState,
  type StaleViewFields,
  serializeTableViewState,
  type TableViewValues,
} from "./tableViewState";

export function tableViewStorageKey(tableId: string): string {
  return `rspace.tableList.${tableId}.view`;
}

export function storedTableViewSelectors(key: string): readonly string[] {
  try {
    const serialized = window.localStorage.getItem(key);
    if (serialized === null) return [];
    const state: unknown = JSON.parse(serialized);
    if (typeof state !== "object" || state === null) return [];
    const { where, columns } = state as { where?: unknown; columns?: unknown };
    return [
      ...(typeof where === "string" ? rsqlSelectors(where) : []),
      ...(typeof columns === "string" ? columnSelectors(columns) : []),
    ];
  } catch {
    return [];
  }
}

export function columnSelectors(serialized: string): readonly string[] {
  try {
    const value: unknown = JSON.parse(serialized);
    const raw =
      typeof value === "object" && value !== null && !Array.isArray(value)
        ? (value as { fields?: unknown }).fields
        : value;
    return Array.isArray(raw) ? raw.filter((name): name is string => typeof name === "string") : [];
  } catch {
    return [];
  }
}

export function loadStoredTableView<TDocument>(
  key: string,
  config: ResolvedCollectionConfig<TDocument>,
  stale?: StaleViewFields,
): TableViewValues<TDocument> | null {
  try {
    const serialized = window.localStorage.getItem(key);
    if (serialized === null) return null;
    const values = parseTableViewState(serialized, config, stale);
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
