import * as v from "valibot";
import type { ResolvedCollectionConfig } from "@/modules/common/collection/collectionConfig";
import { rsqlSelectors } from "../rsql/rsqlCodec";
import { columnSelectors } from "./queryStringState";
import {
  parseTableViewState,
  type StaleViewFields,
  serializeTableViewState,
  type TableViewValues,
} from "./tableViewState";

const storedSelectorsSchema = v.object({
  where: v.fallback(v.string(), ""),
  columns: v.fallback(v.string(), ""),
});

export function tableViewStorageKey(tableId: string): string {
  return `rspace.tableList.${tableId}.view`;
}

export function readStoredTableView(key: string): string | null {
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

export function removeStoredTableView(key: string): void {
  try {
    window.localStorage.removeItem(key);
  } catch {
    // Browser privacy settings must not prevent the table from loading.
  }
}

export function storedTableViewSelectors(key: string): readonly string[] {
  try {
    const serialized = readStoredTableView(key);
    if (serialized === null) return [];
    const result = v.safeParse(storedSelectorsSchema, JSON.parse(serialized));
    if (!result.success) return [];
    const { where, columns } = result.output;
    return [...rsqlSelectors(where), ...columnSelectors(columns)];
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
    const serialized = readStoredTableView(key);
    if (serialized === null) return null;
    const values = parseTableViewState(serialized, config, stale);
    if (values === null) removeStoredTableView(key);
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
    if (serialized === null) removeStoredTableView(key);
    else window.localStorage.setItem(key, serialized);
  } catch {
    // Browser privacy settings and storage quotas must not affect the table.
  }
}
