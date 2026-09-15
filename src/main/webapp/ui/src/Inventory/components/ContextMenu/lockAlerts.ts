import { type Alert, mkAlert } from "../../../stores/contexts/Alert";
import type { InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import { type LockOwner, RecordLockedError } from "../../../stores/models/InventoryBaseRecord";

/**
 * Every record in this failure that another user's edit session holds. An acquisition over several
 * records rejects as an AggregateError; a single one rejects on its own.
 */
function lockedRecords(error: unknown): Array<{ record: InventoryRecord; lockOwner: LockOwner }> {
  if (error instanceof RecordLockedError) return [{ record: error.record, lockOwner: error.lockOwner }];
  if (error instanceof AggregateError)
    return error.errors
      .filter((e: unknown): e is RecordLockedError => e instanceof RecordLockedError)
      .map((e) => ({ record: e.record, lockOwner: e.lockOwner }));
  return [];
}

export function lockOwnerName(lockOwner: LockOwner): string {
  return [lockOwner.firstName, lockOwner.lastName].filter(Boolean).join(" ") || lockOwner.username;
}

/**
 * Shows the "being edited by {name}" alert when the failure is that someone else holds the lock,
 * and reports whether it did. Shared by the edit action and the operation wizard so a user who
 * cannot take a record is told the same thing either way (RSDEV-1231).
 */
export function displayErrorIfAllLocksCouldNotBeAcquired({
  error,
  title,
  message,
  beingEditedBy,
  addAlert,
}: {
  error: unknown;
  title: string;
  message: string;
  beingEditedBy: (name: string) => string;
  addAlert: (alert: Alert) => void;
}): boolean {
  const locked = lockedRecords(error);
  if (locked.length === 0) return false;
  addAlert(
    mkAlert({
      title,
      message,
      variant: "error",
      isInfinite: true,
      details: locked.map(({ record, lockOwner }) => ({
        title: record.name,
        record,
        variant: "error",
        help: beingEditedBy(lockOwnerName(lockOwner)),
      })),
    }),
  );
  return true;
}
