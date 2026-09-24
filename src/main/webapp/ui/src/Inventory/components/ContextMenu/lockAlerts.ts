import { type Alert, mkAlert } from "../../../stores/contexts/Alert";
import type { InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import { type LockOwner, RecordLockedError } from "../../../stores/models/InventoryBaseRecord";

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
