import { parseAsString, useQueryStates } from "nuqs";
import { useCallback, useEffect, useMemo, useState } from "react";
import { rsqlSelectors } from "./rsql/rsqlCodec";
import type { TableListQueryStringOptions } from "./tableListState";
import { columnSelectors, readStoredTableView, removeStoredTableView, tableViewStorageKey } from "./tableViewStorage";
import { parameterNames } from "./useTableListQueryString";

type RawView = {
  search: string | null;
  where: string | null;
  legacyFilters: string | null;
  columns: string | null;
  sort: string | null;
};
const emptyView: RawView = { search: null, where: null, legacyFilters: null, columns: null, sort: null };

function storedView(key: string): RawView & { malformed?: boolean } {
  const serialized = readStoredTableView(key);
  if (serialized === null) return emptyView;
  try {
    const value: unknown = JSON.parse(serialized);
    if (typeof value !== "object" || value === null || !("v" in value) || value.v !== 1) {
      return { ...emptyView, malformed: true };
    }
    const raw: RawView = { ...emptyView };
    for (const name of ["search", "where", "legacyFilters", "columns", "sort"] as const) {
      // Legacy saved views predate the RSQL migration and do not have this member.
      if (!(name in value)) {
        if (name === "legacyFilters") continue;
        return { ...emptyView, malformed: true };
      }
      const entry: unknown = Reflect.get(value, name);
      if (entry !== null && typeof entry !== "string") return { ...emptyView, malformed: true };
      raw[name] = entry;
    }
    return raw;
  } catch {
    return { ...emptyView, malformed: true };
  }
}

function legacySelectors(serialized: string | null): string[] {
  if (serialized === null) return [];
  const fields: string[] = [];
  const visit = (node: unknown) => {
    if (typeof node !== "object" || node === null) return;
    if ("field" in node && typeof node.field === "string") fields.push(node.field);
    if ("children" in node && Array.isArray(node.children)) node.children.forEach(visit);
    if ("expression" in node) visit(node.expression);
  };
  try {
    visit(JSON.parse(serialized));
  } catch {
    /* Validation reports malformed filters. */
  }
  return fields;
}

/** Read encoded state before parsing so unresolved fields never silently erase saved rules. */
export function useSavedTableView(slug: string, options: boolean | TableListQueryStringOptions = true) {
  const names = parameterNames(slug, options === false ? true : options);
  const [url, setUrl] = useQueryStates(
    {
      search: parseAsString,
      where: parseAsString,
      legacyFilters: parseAsString,
      columns: parseAsString,
      sort: parseAsString,
    },
    { urlKeys: names },
  );
  const tableId = typeof options === "object" ? options.tableId?.trim() || slug : slug;
  const storageKey = tableViewStorageKey(tableId);
  const stored = useMemo(() => storedView(storageKey), [storageKey]);
  const hasUrl = Object.values(url).some((value) => value !== null);
  const [storageAllowed, setStorageAllowed] = useState(!hasUrl);
  useEffect(() => {
    if (hasUrl) setStorageAllowed(false);
  }, [hasUrl]);
  const raw = options === false ? emptyView : hasUrl || !storageAllowed ? url : stored;
  const selectors = [
    ...(raw.where === null ? [] : rsqlSelectors(raw.where)),
    ...(raw.columns === null ? [] : columnSelectors(raw.columns)),
    ...legacySelectors(raw.legacyFilters),
  ];
  const remove = useCallback(() => {
    setStorageAllowed(false);
    removeStoredTableView(storageKey);
    void setUrl(emptyView);
  }, [setUrl, storageKey]);
  return {
    raw,
    selectors,
    signature: JSON.stringify(raw),
    active: options !== false && Object.values(raw).some((value) => value !== null),
    malformed: "malformed" in raw && raw.malformed === true,
    remove,
  };
}

export type SavedTableView = ReturnType<typeof useSavedTableView>;
export const emptySavedTableView: SavedTableView = {
  raw: emptyView,
  selectors: [],
  signature: JSON.stringify(emptyView),
  active: false,
  malformed: false,
  remove: () => undefined,
};

/** Rendered inside TableList only when persistence is enabled; no adapter is needed otherwise. */
export function SavedTableViewReader({
  slug,
  options,
  onChange,
}: {
  slug: string;
  options: true | TableListQueryStringOptions;
  onChange: (saved: SavedTableView) => void;
}) {
  const saved = useSavedTableView(slug, options);
  const { signature, remove } = saved;
  useEffect(() => {
    onChange(saved);
  }, [signature, remove, onChange]);
  return null;
}
