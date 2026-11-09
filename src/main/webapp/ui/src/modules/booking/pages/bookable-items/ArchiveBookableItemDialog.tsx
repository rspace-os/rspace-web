import { useMutation } from "@tanstack/react-query";
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
import { requiredVersion } from "./bookableItemLifecycleHelpers";
import type { BookingConfigurationRow } from "./bookingConfiguration";

export function ArchiveBookableItemDialog({
  configuration,
  close,
  onArchive,
  onArchived,
}: {
  configuration: BookingConfigurationRow;
  close: () => void;
  onArchive: (id: number, version: number) => Promise<void>;
  onArchived: (configurationId: number) => Promise<void>;
}) {
  const { t } = useTranslation(["booking", "common"]);
  const archiveMutation = useMutation({
    mutationFn: async () => {
      await onArchive(configuration.id, requiredVersion(configuration));
      close();
      await onArchived(configuration.id);
    },
  });
  const itemName = configuration.target?.value.name ?? t("common:values.unknownItem");

  return (
    <AlertDialog open onOpenChange={(open) => !open && close()}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{t("bookableItems.archiveDialog.title")}</AlertDialogTitle>
          <AlertDialogDescription>
            {t("bookableItems.archiveDialog.description", { item: itemName })}
          </AlertDialogDescription>
        </AlertDialogHeader>
        {archiveMutation.isError ? (
          <p role="alert" className="text-sm text-destructive">
            {t("bookableItems.archiveDialog.error", { item: itemName })}
          </p>
        ) : null}
        <AlertDialogFooter>
          <AlertDialogCancel disabled={archiveMutation.isPending}>{t("common:actions.cancel")}</AlertDialogCancel>
          <AlertDialogAction
            variant="destructive"
            disabled={archiveMutation.isPending}
            aria-busy={archiveMutation.isPending}
            onClick={() => archiveMutation.mutate()}
          >
            {t("bookableItems.actions.archive")}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
