import { useMutation } from "@tanstack/react-query";
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
  const deleteMutation = useMutation({
    mutationFn: async () => {
      await onDelete(configuration.id, requiredVersion(configuration));
      close();
      await onDeleted();
    },
  });
  const itemName = configuration.target?.value.name ?? t("common:values.unknownItem");

  return (
    <AlertDialog open onOpenChange={(open) => !open && !deleteMutation.isPending && close()}>
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
            autoComplete="off"
            value={confirmation}
            onChange={(event) => setConfirmation(event.currentTarget.value)}
          />
        </label>
        {deleteMutation.isError ? (
          <p role="alert" className="text-sm text-destructive">
            {t(
              lifecycleErrorKey(
                deleteMutation.error,
                "bookableItems.permanentDeleteDialog.error",
                "bookableItems.lifecycleErrors.stale",
                "bookableItems.lifecycleErrors.stateChanged",
              ),
            )}
          </p>
        ) : null}
        <AlertDialogFooter>
          <AlertDialogCancel disabled={deleteMutation.isPending}>{t("common:actions.cancel")}</AlertDialogCancel>
          <AlertDialogAction
            variant="destructive"
            disabled={deleteMutation.isPending || confirmation !== itemName}
            aria-busy={deleteMutation.isPending}
            onClick={() => deleteMutation.mutate()}
          >
            {t("bookableItems.actions.deletePermanently")}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
