import { useQueryClient } from "@tanstack/react-query";
import { useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { ApiV2ProblemError, cancelBooking } from "@/modules/booking/domain/booking";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/modules/common/ui/alert-dialog";
import { Button } from "@/modules/common/ui/button";

type DeleteBookingDialogProps = {
  bookingId: number;
  itemName: string;
  period: string;
  token: string;
  disabled?: boolean;
  onDeleted: () => void | Promise<void>;
};

type DeleteErrorKey =
  | "bookings.errors.deleteGeneric"
  | "bookings.errors.deleteForbidden"
  | "bookings.errors.deleteStale";

function deleteErrorKey(error: unknown): DeleteErrorKey {
  if (!(error instanceof ApiV2ProblemError)) return "bookings.errors.deleteGeneric";
  if (error.status === 403 || error.code === "errors.api.v2.forbidden") {
    return "bookings.errors.deleteForbidden";
  }
  if (error.code === "errors.api.v2.booking.state.transition") return "bookings.errors.deleteStale";
  return "bookings.errors.deleteGeneric";
}

export function DeleteBookingDialog({
  bookingId,
  itemName,
  period,
  token,
  disabled = false,
  onDeleted,
}: DeleteBookingDialogProps) {
  const { t } = useTranslation(["booking", "common"]);
  const queryClient = useQueryClient();
  const activeRequest = useRef(false);
  const [open, setOpen] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [errorKey, setErrorKey] = useState<DeleteErrorKey | null>(null);

  const invalidateBookingQueries = async () => {
    await queryClient.invalidateQueries({ queryKey: ["api-v2", "bookings"] });
    await queryClient.invalidateQueries({ queryKey: ["api-v2", "bookings", bookingId] });
  };

  const handleDelete = async () => {
    if (activeRequest.current) return;
    activeRequest.current = true;
    setIsDeleting(true);
    setErrorKey(null);
    try {
      await cancelBooking(bookingId, token);
      await invalidateBookingQueries();
      await onDeleted();
      setOpen(false);
    } catch (error) {
      const nextErrorKey = deleteErrorKey(error);
      setErrorKey(nextErrorKey);
      if (nextErrorKey !== "bookings.errors.deleteGeneric") {
        try {
          await invalidateBookingQueries();
        } catch {
          setErrorKey("bookings.errors.deleteGeneric");
        }
      }
    } finally {
      activeRequest.current = false;
      setIsDeleting(false);
    }
  };

  return (
    <AlertDialog open={open} onOpenChange={(nextOpen) => !activeRequest.current && setOpen(nextOpen)}>
      <AlertDialogTrigger
        disabled={disabled || isDeleting}
        render={<Button type="button" size="sm" variant="destructive" />}
      >
        {t("bookings.actions.delete")}
      </AlertDialogTrigger>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{t("bookings.deleteDialog.title")}</AlertDialogTitle>
          <AlertDialogDescription>
            {t("bookings.deleteDialog.description", { itemName, period })}
          </AlertDialogDescription>
        </AlertDialogHeader>
        {errorKey && (
          <p role="alert" className="text-sm text-destructive">
            {t(errorKey)}
          </p>
        )}
        <AlertDialogFooter>
          <AlertDialogCancel disabled={isDeleting}>{t("common:actions.cancel")}</AlertDialogCancel>
          <AlertDialogAction
            type="button"
            variant="destructive"
            disabled={isDeleting}
            aria-busy={isDeleting}
            onClick={() => void handleDelete()}
          >
            {t("common:actions.delete")}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
