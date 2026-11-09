import { useQueryStates } from "nuqs";
import { useCallback, useEffect, useMemo, useState } from "react";
import * as v from "valibot";
import { rsqlSelectors } from "../rsql";
import type { TableListQueryStringOptions } from "../types";
import { columnSelectors } from "../utils/queryString";
import {
  readStoredTableView,
  removeStoredTableView,
  tableViewStateSchema,
  tableViewStorageKey,
} from "../utils/viewStorage";
import { parameterNames, rawViewParsers, tableIdFor } from "./urlState";

type RawView = {
  search: string | null;
  where: string | null;
  columns: string | null;
  sort: string | null;
};
const rawViewSchema = v.omit(tableViewStateSchema, ["defaults"]);
const emptyView: RawView = { search: null, where: null, columns: null, sort: null };

function storedView(key: string): RawView & { malformed?: boolean } {
  const serialized = readStoredTableView(key);
  if (serialized === null) return emptyView;
  try {
    const result = v.safeParse(rawViewSchema, JSON.parse(serialized));
    if (!result.success) return { ...emptyView, malformed: true };
    const { search, where, columns, sort } = result.output;
    return { search, where, columns, sort };
  } catch {
    return { ...emptyView, malformed: true };
  }
}

/** Read encoded state before parsing so unresolved fields never silently erase saved rules. */
export function useSavedTableView(slug: string, options: boolean | TableListQueryStringOptions = true) {
  const names = parameterNames(slug, options === false ? true : options);
  const [url, setUrl] = useQueryStates(rawViewParsers, { urlKeys: names });
  const storageKey = tableViewStorageKey(tableIdFor(slug, options));
  const stored = useMemo(() => storedView(storageKey), [storageKey]);
  const hasUrl = Object.values(url).some((value) => value !== null);
  const [storageAllowed, setStorageAllowed] = useState(!hasUrl);
  if (hasUrl && storageAllowed) setStorageAllowed(false);
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
