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
  onAction,
}: {
  selection: TableListSelectionContext;
  disabled: boolean;
  activeAction: BookableItemsBulkAction | null;
  failedAction: BookableItemsBulkAction | null;
  onAction: (action: BookableItemsBulkAction, selectedRowIds: ReadonlySet<string>) => Promise<void>;
}) {
  const { t } = useTranslation(["booking", "common"]);
  const [archiveOpen, setArchiveOpen] = useState(false);
  const error =
    failedAction === "enable"
      ? t("bookableItems.bulk.errors.enable")
      : failedAction === "disable"
        ? t("bookableItems.bulk.errors.disable")
        : failedAction === "archive"
          ? t("bookableItems.bulk.errors.archive")
          : null;

  const runAction = async (action: BookableItemsBulkAction) => {
    try {
      await onAction(action, selection.selectedRowIds);
      if (action === "archive") setArchiveOpen(false);
    } catch {
      // The mutation renders the action-specific error and keeps the selected IDs.
    }
  };

  return (
    <>
      <Button
        type="button"
        variant="secondary"
        size="sm"
        disabled={disabled}
        aria-busy={activeAction === "enable"}
        onClick={() => void runAction("enable")}
      >
        {t("bookableItems.bulk.actions.enable")}
      </Button>
      <Button
        type="button"
        variant="secondary"
        size="sm"
        disabled={disabled}
        aria-busy={activeAction === "disable"}
        onClick={() => void runAction("disable")}
      >
        {t("bookableItems.bulk.actions.disable")}
      </Button>
      <Button
        type="button"
        variant="destructive"
        size="sm"
        disabled={disabled}
        aria-busy={activeAction === "archive"}
        onClick={() => setArchiveOpen(true)}
      >
        {t("bookableItems.bulk.actions.archive")}
      </Button>
      {error && (!archiveOpen || failedAction !== "archive") ? (
        <p role="alert" className="basis-full text-sm text-destructive">
          {error}
        </p>
      ) : null}
      <AlertDialog open={archiveOpen} onOpenChange={setArchiveOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>
              {t("bookableItems.bulk.archiveDialog.title", { count: selection.selectedRowIds.size })}
            </AlertDialogTitle>
            <AlertDialogDescription>{t("bookableItems.bulk.archiveDialog.description")}</AlertDialogDescription>
          </AlertDialogHeader>
          {error && failedAction === "archive" ? (
            <p role="alert" className="text-sm text-destructive">
              {error}
            </p>
          ) : null}
          <AlertDialogFooter>
            <AlertDialogCancel disabled={disabled}>{t("common:actions.cancel")}</AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              disabled={disabled}
              aria-busy={activeAction === "archive"}
              onClick={() => void runAction("archive")}
            >
              {t("bookableItems.actions.archive")}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
