import { useState } from "react";
import { useTranslation } from "react-i18next";
import type { TableListSelectionContext } from "@/modules/common/table-list/TableList";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/modules/common/ui/alert-dialog";
import { Button } from "@/modules/common/ui/button";
import type { BookableItemsBulkAction } from "./bookableItemLifecycleHelpers";

export function BookableItemsBulkActions({
  selection,
  disabled,
  activeAction,
  failedAction,
  offerEnable = true,
  onAction,
}: {
  selection: TableListSelectionContext;
  disabled: boolean;
  activeAction: BookableItemsBulkAction | null;
  failedAction: BookableItemsBulkAction | null;
  /** False where every listed item is already enabled, as on All items. */
  offerEnable?: boolean;
  onAction: (action: BookableItemsBulkAction, selectedRowIds: ReadonlySet<string>) => Promise<void>;
}) {
  const { t } = useTranslation(["booking", "common"]);
  const [confirming, setConfirming] = useState<"disable" | "archive" | null>(null);
  const errorKeys = {
    enable: "allBookableItems.bulk.errors.enable",
    disable: "allBookableItems.bulk.errors.disable",
    archive: "allBookableItems.bulk.errors.archive",
  } as const;
  const error = failedAction === null ? null : t(errorKeys[failedAction]);

  const runAction = async (action: BookableItemsBulkAction) => {
    try {
      await onAction(action, selection.selectedRowIds);
      if (action !== "enable") setConfirming(null);
    } catch {
      // The mutation renders the action-specific error and keeps the selected IDs.
    }
  };

  return (
    <>
      {offerEnable ? (
        <Button
          type="button"
          variant="secondary"
          size="sm"
          disabled={disabled}
          aria-busy={activeAction === "enable"}
          onClick={() => void runAction("enable")}
        >
          {t("allBookableItems.bulk.actions.enable")}
        </Button>
      ) : null}
      <Button
        type="button"
        variant="secondary"
        size="sm"
        disabled={disabled}
        aria-busy={activeAction === "disable"}
        onClick={() => setConfirming("disable")}
      >
        {t("allBookableItems.bulk.actions.disable")}
      </Button>
      <Button
        type="button"
        variant="destructive"
        size="sm"
        disabled={disabled}
        aria-busy={activeAction === "archive"}
        onClick={() => setConfirming("archive")}
      >
        {t("allBookableItems.bulk.actions.archive")}
      </Button>
      {error && failedAction !== confirming ? (
        <p role="alert" className="basis-full text-sm text-destructive">
          {error}
        </p>
      ) : null}
      <AlertDialog
        open={confirming !== null}
        onOpenChange={(open) => {
          if (!open) setConfirming(null);
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>
              {confirming === "disable"
                ? t("bookableItems.bulk.disableDialog.title", {
                    count: selection.selectedRowIds.size,
                  })
                : t("allBookableItems.bulk.archiveDialog.title", { count: selection.selectedRowIds.size })}
            </AlertDialogTitle>
            <AlertDialogDescription>
              {confirming === "disable"
                ? t("bookableItems.bulk.disableDialog.description", {})
                : t("allBookableItems.bulk.archiveDialog.description")}
            </AlertDialogDescription>
          </AlertDialogHeader>
          {error && failedAction === confirming ? (
            <p role="alert" className="text-sm text-destructive">
              {error}
            </p>
          ) : null}
          <AlertDialogFooter>
            <AlertDialogCancel disabled={disabled}>{t("common:actions.cancel")}</AlertDialogCancel>
            <AlertDialogAction
              variant={confirming === "archive" ? "destructive" : "default"}
              disabled={disabled}
              aria-busy={activeAction === confirming}
              onClick={(event) => {
                event.preventDefault();
                if (confirming) void runAction(confirming);
              }}
            >
              {confirming === "disable"
                ? t("allBookableItems.bulk.actions.disable")
                : t("bookableItems.actions.archive")}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
