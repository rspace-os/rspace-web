export type TableViewPersistenceOptions = {
  /** Prefix for the table's owned query parameters. Defaults to the collection slug; use a distinct prefix for tables on the same page. */
  parameterPrefix?: string;
  /** Stable browser-profile-scoped persistence identity. Defaults to the collection slug; set it when independent views reuse a config. */
  tableId?: string;
};

/**
 * What a route does with a stored view that does not parse: `"issue"` keeps it and the table shows
 * `RestoredViewIssue`; `"discard"` removes it and the table shows its defaults.
 */
export type MalformedTableViewPolicy = "issue" | "discard";

/** One persisted table, shared by its route (restore and save) and its hook (URL state), so the two cannot drift. */
export type TableViewDescriptor<TMalformed extends MalformedTableViewPolicy = MalformedTableViewPolicy> = {
  slug: string;
  persistence: TableViewPersistenceOptions;
  defaultColumns: readonly string[];
  malformed: TMalformed;
};

export function tableViewDescriptor<const TMalformed extends MalformedTableViewPolicy>(
  config: { slug: string; defaultColumns: readonly string[] },
  malformed: TMalformed,
  persistence: TableViewPersistenceOptions = {},
): TableViewDescriptor<TMalformed> {
  return { slug: config.slug, persistence, defaultColumns: config.defaultColumns, malformed };
}

export function parameterPrefix({ slug, persistence }: Pick<TableViewDescriptor, "slug" | "persistence">): string {
  return persistence.parameterPrefix?.trim() || slug;
}

export function parameterNames(descriptor: Pick<TableViewDescriptor, "slug" | "persistence">) {
  const prefix = parameterPrefix(descriptor);
  return {
    search: `${prefix}.q`,
    where: `${prefix}.where`,
    columns: `${prefix}.columns`,
    sort: `${prefix}.sort`,
  };
}

export function tableViewStorageKey({ slug, persistence }: Pick<TableViewDescriptor, "slug" | "persistence">): string {
  return `rspace.tableList.${persistence.tableId?.trim() || slug}.view`;
}

/** The URL prefix and storage key a persisted table owns; no two tables on a page may share either. */
export type TableListPersistenceIdentity = {
  parameterPrefix: string;
  storageKey: string;
};

export function persistenceIdentity(descriptor: TableViewDescriptor): TableListPersistenceIdentity {
  return { parameterPrefix: parameterPrefix(descriptor), storageKey: tableViewStorageKey(descriptor) };
}
