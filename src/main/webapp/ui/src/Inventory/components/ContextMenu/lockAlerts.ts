import { useTranslation } from "react-i18next";
import { mkAlert } from "../../../stores/contexts/Alert";
import type { InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import { type LockOwner, RecordLockedError } from "../../../stores/models/InventoryBaseRecord";
import useStores from "../../../stores/use-stores";

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
 * Returns a function that, when an error says some records are locked by someone else, shows one
 * alert naming each record and who holds it, and reports whether it did.
 */
export function useLockAlert(): (error: unknown, text: { title: string; message: string }) => boolean {
  const { t } = useTranslation("inventory");
  const { uiStore } = useStores();
  return (error, { title, message }) => {
    const locked = lockedRecords(error);
    if (locked.length === 0) return false;
    uiStore.addAlert(
      mkAlert({
        title,
        message,
        variant: "error",
        isInfinite: true,
        details: locked.map(({ record, lockOwner }) => ({
          title: record.name,
          record,
          variant: "error",
          help: t("contextMenu.edit.beingEditedBy", { name: lockOwnerName(lockOwner) }),
        })),
      }),
    );
    return true;
  };
}
