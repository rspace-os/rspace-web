import { useId, useState } from "react";
import { useTranslation } from "react-i18next";
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
import { Input } from "@/modules/common/ui/input";
import { lifecycleErrorKey, requiredVersion } from "./bookableItemLifecycleHelpers";
import type { BookingConfigurationRow } from "./bookingConfiguration";

export function PermanentDeleteBookableItemDialog({
  configuration,
  close,
  onDelete,
  onDeleted,
}: {
  configuration: BookingConfigurationRow;
  close: () => void;
  onDelete: (id: number, version: number) => Promise<void>;
  onDeleted: () => Promise<void>;
}) {
  const { t } = useTranslation(["booking", "common"]);
  const [confirmation, setConfirmation] = useState("");
  const confirmationId = useId();
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<unknown>(null);
  const itemName = configuration.target?.value.name ?? t("common:values.unknownItem");

  const handleDelete = async () => {
    setIsDeleting(true);
    setDeleteError(null);
    try {
      await onDelete(configuration.id, requiredVersion(configuration));
      close();
      await onDeleted();
    } catch (error) {
      setDeleteError(error);
    } finally {
      setIsDeleting(false);
    }
  };

  return (
    <AlertDialog open onOpenChange={(open) => !open && !isDeleting && close()}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{t("bookableItems.permanentDeleteDialog.title")}</AlertDialogTitle>
          <AlertDialogDescription>
            {t("bookableItems.permanentDeleteDialog.description", { item: itemName })}
          </AlertDialogDescription>
        </AlertDialogHeader>
        <label htmlFor={confirmationId} className="space-y-2 text-sm">
          <span>{t("bookableItems.permanentDeleteDialog.confirmationLabel")}</span>
          <Input
            id={confirmationId}
            value={confirmation}
            onChange={(event) => setConfirmation(event.currentTarget.value)}
          />
        </label>
        {deleteError ? (
          <p role="alert" className="text-sm text-destructive">
            {t(lifecycleErrorKey(deleteError, "bookableItems.permanentDeleteDialog.error"))}
          </p>
        ) : null}
        <AlertDialogFooter>
          <AlertDialogCancel disabled={isDeleting}>{t("common:actions.cancel")}</AlertDialogCancel>
          <AlertDialogAction
            variant="destructive"
            disabled={isDeleting || confirmation !== itemName}
            aria-busy={isDeleting}
            onClick={() => void handleDelete()}
          >
            {t("bookableItems.actions.deletePermanently")}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
