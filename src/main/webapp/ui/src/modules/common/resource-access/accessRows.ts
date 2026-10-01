import type { ResourceAccessAssignment, ResourceAccessDocument } from "./schemas";

/** How a row differs from the saved access document. */
export type AccessRowStatus = "unchanged" | "added" | "changed" | "removed";

export type AccessRow = {
  key: string;
  assignment: ResourceAccessAssignment;
  status: AccessRowStatus;
  /** The saved role, when the draft changed or dropped it. */
  fromRole: string | null;
  /** This row is the caller's own direct assignment. */
  isSelf: boolean;
};

export type AccessMergeConflict = {
  key: string;
  base: ResourceAccessAssignment | null;
  local: ResourceAccessAssignment | null;
  latest: ResourceAccessAssignment | null;
};

function sameAssignment(
  left: ResourceAccessAssignment | null | undefined,
  right: ResourceAccessAssignment | null | undefined,
): boolean {
  return (left?.role ?? null) === (right?.role ?? null) && Boolean(left) === Boolean(right);
}

/** Pure, key-based three-way merge. Only independent or identical changes merge automatically. */
export function mergeAccessDraft(
  base: readonly ResourceAccessAssignment[],
  local: readonly ResourceAccessAssignment[],
  latest: readonly ResourceAccessAssignment[],
): { draft: ResourceAccessAssignment[]; conflicts: AccessMergeConflict[] } {
  const baseByKey = new Map(base.map((assignment) => [assignment.grantee.key, assignment]));
  const localByKey = new Map(local.map((assignment) => [assignment.grantee.key, assignment]));
  const latestByKey = new Map(latest.map((assignment) => [assignment.grantee.key, assignment]));
  const keys = new Set([...baseByKey.keys(), ...localByKey.keys(), ...latestByKey.keys()]);
  const draft: ResourceAccessAssignment[] = [];
  const conflicts: AccessMergeConflict[] = [];
  for (const key of keys) {
    const before = baseByKey.get(key) ?? null;
    const mine = localByKey.get(key) ?? null;
    const theirs = latestByKey.get(key) ?? null;
    let chosen: ResourceAccessAssignment | null;
    if (sameAssignment(mine, before)) chosen = theirs;
    else if (sameAssignment(theirs, before) || sameAssignment(mine, theirs)) chosen = mine;
    else {
      conflicts.push({ key, base: before, local: mine, latest: theirs });
      chosen = mine;
    }
    if (chosen) draft.push(chosen);
  }
  return { draft, conflicts };
}

/**
 * The rows to display, which is not the same as the draft: an assignment staged for removal stays
 * visible so it can be reviewed and restored before saving. The save payload is still the draft.
 */
export function buildAccessRows(
  saved: readonly ResourceAccessAssignment[],
  draft: readonly ResourceAccessAssignment[],
  callerKey: string | null | undefined,
  locale?: string,
): AccessRow[] {
  const draftByKey = new Map(draft.map((assignment) => [assignment.grantee.key, assignment]));
  const savedKeys = new Set(saved.map((assignment) => assignment.grantee.key));
  const row = (assignment: ResourceAccessAssignment, status: AccessRowStatus, fromRole: string | null): AccessRow => ({
    key: assignment.grantee.key,
    assignment,
    status,
    fromRole,
    isSelf: callerKey != null && assignment.grantee.key === callerKey,
  });

  const rows = [
    ...saved.map((savedAssignment) => {
      const drafted = draftByKey.get(savedAssignment.grantee.key);
      if (!drafted) return row(savedAssignment, "removed", savedAssignment.role);
      return drafted.role === savedAssignment.role
        ? row(drafted, "unchanged", null)
        : row(drafted, "changed", savedAssignment.role);
    }),
    ...draft.filter((assignment) => !savedKeys.has(assignment.grantee.key)).map((a) => row(a, "added", null)),
  ];
  const collator = new Intl.Collator(locale, { sensitivity: "base", numeric: true });
  return rows.toSorted(
    (left, right) =>
      collator.compare(left.assignment.grantee.name, right.assignment.grantee.name) ||
      left.assignment.grantee.kind.localeCompare(right.assignment.grantee.kind) ||
      left.key.localeCompare(right.key),
  );
}

/** The draft an editor sends: display-only removed rows are already absent from it. */
export function draftWithout(draft: readonly ResourceAccessAssignment[], key: string): ResourceAccessAssignment[] {
  return draft.filter((assignment) => assignment.grantee.key !== key);
}

export function draftWithRole(
  draft: readonly ResourceAccessAssignment[],
  key: string,
  role: string,
): ResourceAccessAssignment[] {
  return draft.map((assignment) => (assignment.grantee.key === key ? { ...assignment, role } : assignment));
}

/**
 * Puts a restored assignment back where it was saved, so restoring does not shuffle the list.
 * Additions keep their append order after the saved rows.
 */
export function draftWithRestored(
  saved: readonly ResourceAccessAssignment[],
  draft: readonly ResourceAccessAssignment[],
  restored: ResourceAccessAssignment,
): ResourceAccessAssignment[] {
  const draftByKey = new Map(draft.map((assignment) => [assignment.grantee.key, assignment]));
  draftByKey.set(restored.grantee.key, restored);
  const savedKeys = new Set(saved.map((assignment) => assignment.grantee.key));
  return [
    ...saved.flatMap((savedAssignment) => {
      const kept = draftByKey.get(savedAssignment.grantee.key);
      return kept ? [kept] : [];
    }),
    ...draft.filter((assignment) => !savedKeys.has(assignment.grantee.key)),
  ];
}

export function callerKeyOf(document: ResourceAccessDocument): string | null {
  return document.caller.granteeKey ?? null;
}
