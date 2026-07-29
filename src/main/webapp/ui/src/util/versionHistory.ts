/**
 * Shared handling of audit revisions for version-history UI.
 *
 * An audit revision and a user-facing version are not the same thing: several
 * revisions can share one version, because not every recorded change bumps the
 * counter. Every version-history view therefore has to collapse revisions to
 * versions the same way, and this is the one place that rule lives.
 */

/** The minimum an audit revision must carry to be grouped by version. */
interface AuditRevision {
  revisionId: number;
  record: { version?: number | null };
}

/**
 * Collapses a record's audit revisions to one entry per user-facing version,
 * keeping the newest revision of each, and returns them newest version first.
 *
 * The newest revision of a version wins because that version's final state is
 * the one worth showing. Revisions are sorted by ascending revisionId first, so
 * "last write wins" holds regardless of the order they arrive in. Revisions
 * carrying no version are skipped: there is nothing to label them with.
 *
 * The whole winning revision is returned alongside its version, rather than a
 * fixed row shape, because callers need different fields from it: a date and a
 * user's name to display, and the revisionId itself to address that revision in
 * URLs that key on it.
 */
export function groupByVersion<T extends AuditRevision>(
  revisions: ReadonlyArray<T>,
): Array<{ version: number; revision: T }> {
  const byVersion = new Map<number, T>();
  const oldestFirst = [...revisions].sort((a, b) => a.revisionId - b.revisionId);
  for (const revision of oldestFirst) {
    const version = revision.record.version;
    if (version === null || typeof version === "undefined") continue;
    byVersion.set(version, revision);
  }
  return [...byVersion.entries()].sort(([a], [b]) => b - a).map(([version, revision]) => ({ version, revision }));
}
