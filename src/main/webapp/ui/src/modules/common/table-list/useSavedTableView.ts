import { parseAsString, useQueryStates } from "nuqs";
import { useCallback, useEffect, useMemo, useState } from "react";
import { columnSelectors } from "./queryStringState";
import { rsqlSelectors } from "./rsql/rsqlCodec";
import type { TableListQueryStringOptions } from "./tableListState";
import { readStoredTableView, removeStoredTableView, tableViewStorageKey } from "./tableViewStorage";
import { parameterNames, tableIdFor } from "./useTableListQueryString";

type RawView = {
  search: string | null;
  where: string | null;
  columns: string | null;
  sort: string | null;
};
const emptyView: RawView = { search: null, where: null, columns: null, sort: null };

function storedView(key: string): RawView & { malformed?: boolean } {
  const serialized = readStoredTableView(key);
  if (serialized === null) return emptyView;
  try {
    const value: unknown = JSON.parse(serialized);
    if (typeof value !== "object" || value === null || !("v" in value) || value.v !== 1) {
      return { ...emptyView, malformed: true };
    }
    const raw: RawView = { ...emptyView };
    for (const name of ["search", "where", "columns", "sort"] as const) {
      if (!(name in value)) {
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

/** Read encoded state before parsing so unresolved fields never silently erase saved rules. */
export function useSavedTableView(slug: string, options: boolean | TableListQueryStringOptions = true) {
  const names = parameterNames(slug, options === false ? true : options);
  const [url, setUrl] = useQueryStates(
    {
      search: parseAsString,
      where: parseAsString,
      columns: parseAsString,
      sort: parseAsString,
    },
    { urlKeys: names },
  );
  const storageKey = tableViewStorageKey(tableIdFor(slug, options));
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
