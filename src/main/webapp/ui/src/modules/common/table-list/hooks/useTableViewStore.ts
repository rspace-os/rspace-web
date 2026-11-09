import { parseAsString, useQueryStates } from "nuqs";
import { useState } from "react";
import { parameterNames, type TableViewDescriptor, tableViewStorageKey } from "../persistence";
import {
  emptyRawTableView,
  type RawTableView,
  readRawTableView,
  readStoredTableView,
  removeStoredTableView,
} from "../utils/viewStorage";

/** Where a table's raw view lives: component state, or the URL (and, through the route, storage). */
export type TableViewStore = {
  raw: RawTableView;
  /** Changes with the raw view and with the store's identity. */
  rawSignature: string;
  signatureOf: (raw: RawTableView) => string;
  write: (next: RawTableView) => void;
  remove: () => void;
  /** Stored view that cannot be restored, kept for RestoredViewIssue. */
  malformed: boolean;
};

const rawViewParsers = {
  search: parseAsString,
  where: parseAsString,
  columns: parseAsString,
  sort: parseAsString,
};

const localSignature = (raw: RawTableView) => JSON.stringify(["local", raw]);

export function useLocalTableView(): TableViewStore {
  const [raw, setRaw] = useState(emptyRawTableView);
  return {
    raw,
    rawSignature: localSignature(raw),
    signatureOf: localSignature,
    write: setRaw,
    remove: () => setRaw(emptyRawTableView),
    malformed: false,
  };
}

/** Reads and writes the table's URL parameters. Restoring and saving the stored view is the route's job. */
export function useUrlTableView(descriptor: TableViewDescriptor): TableViewStore {
  const names = parameterNames(descriptor);
  const storageKey = tableViewStorageKey(descriptor);
  // All readers of these nuqs keys use strings. Its shared state broadcasts parsed values,
  // so mixing a raw reader and typed parsers would pass arrays/ASTs into the raw reader.
  const [url, setUrl] = useQueryStates(rawViewParsers, { history: "replace", urlKeys: names });
  const identity = JSON.stringify([names, storageKey]);
  const hasUrl = Object.values(url).some((value) => value !== null);
  const readMalformed = () =>
    descriptor.malformed === "issue" &&
    !hasUrl &&
    readRawTableView(readStoredTableView(storageKey), descriptor.defaultColumns) === "malformed";
  const [malformed, setMalformed] = useState(() => ({ identity, value: readMalformed() }));
  let current = malformed;
  if (current.identity !== identity) current = { identity, value: readMalformed() };
  // A URL view replaces the stored one, so a later return to the defaults is not reported as malformed.
  if (current.value && hasUrl) current = { identity, value: false };
  if (current !== malformed) setMalformed(current);
  const signatureOf = (raw: RawTableView) => JSON.stringify([identity, raw]);
  return {
    raw: url,
    rawSignature: signatureOf(url),
    signatureOf,
    write: (next) => {
      void setUrl(next);
    },
    remove: () => {
      setMalformed({ identity, value: false });
      removeStoredTableView(storageKey);
      void setUrl(emptyRawTableView);
    },
    malformed: current.value,
  };
}
