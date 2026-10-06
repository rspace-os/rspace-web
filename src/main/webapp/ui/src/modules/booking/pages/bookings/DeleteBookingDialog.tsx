import { useQueryClient } from "@tanstack/react-query";
import { CalendarX2Icon } from "lucide-react";
import { type ComponentProps, useEffect, useId, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  ApiV2ProblemError,
  type BookingEventKind,
  type BookingMutation,
  cancelBooking,
} from "@/modules/booking/domain/booking";
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
import { Textarea } from "@/modules/common/ui/textarea";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/modules/common/ui/tooltip";

const CANCELLATION_REASON_MAX_LENGTH = 500;

type DeleteBookingDialogProps = {
  bookingId: number;
  bookingVersion: number;
  /** Named in the booking confirmation together with `period`; generic text is used when either is empty. */
  itemName?: string;
  period?: string;
  token: string;
  eventKind?: BookingEventKind;
  disabled?: boolean;
  iconOnly?: boolean;
  triggerVariant?: "outline" | "destructive";
  /**
   * Opens the dialog from outside, for example from a menu item that unmounts when its menu
   * closes. No trigger is rendered in this mode, so pass `finalFocus` to say where focus returns.
   */
  open?: boolean;
  onOpenChange?: (open: boolean) => void;
  finalFocus?: ComponentProps<typeof AlertDialogContent>["finalFocus"];
  /** Receives the cancelled booking, whose version a later restore must send. */
  onDeleted: (cancelled: BookingMutation) => void | Promise<void>;
};

type DeleteErrorKey =
  | "bookings.errors.cancellationReasonLength"
  | "bookings.errors.cancellationReasonRequiresCancel"
  | "bookings.errors.deleteGeneric"
  | "bookings.errors.deleteForbidden"
  | "bookings.errors.deleteStale";

function deleteErrorKey(error: unknown): DeleteErrorKey {
  if (!(error instanceof ApiV2ProblemError)) return "bookings.errors.deleteGeneric";
  if (error.status === 403 || error.code === "errors.api.v2.forbidden") {
    return "bookings.errors.deleteForbidden";
  }
  if (
    error.status === 412 ||
    error.code === "errors.api.v2.booking.concurrentModification" ||
    error.code === "errors.api.v2.booking.state.transition"
  )
    return "bookings.errors.deleteStale";
  if (error.code === "errors.api.v2.booking.cancellationReason.length") {
    return "bookings.errors.cancellationReasonLength";
  }
  if (error.code === "errors.api.v2.booking.cancellationReason.requiresCancel") {
    return "bookings.errors.cancellationReasonRequiresCancel";
  }
  return "bookings.errors.deleteGeneric";
}

export function DeleteBookingDialog({
  bookingId,
  bookingVersion,
  itemName,
  period,
  token,
  eventKind = "BOOKING",
  disabled = false,
  iconOnly = false,
  triggerVariant = "destructive",
  open: controlledOpen,
  onOpenChange,
  finalFocus,
  onDeleted,
}: DeleteBookingDialogProps) {
  const { t } = useTranslation(["booking", "common"]);
  const queryClient = useQueryClient();
  const activeRequest = useRef(false);
  const controlled = controlledOpen !== undefined;
  const [uncontrolledOpen, setUncontrolledOpen] = useState(false);
  const open = controlledOpen ?? uncontrolledOpen;
  const setOpen = (nextOpen: boolean) => {
    if (!controlled) setUncontrolledOpen(nextOpen);
    onOpenChange?.(nextOpen);
  };
  const [isDeleting, setIsDeleting] = useState(false);
  const [errorKey, setErrorKey] = useState<DeleteErrorKey | null>(null);
  const [reason, setReason] = useState("");
  const reasonId = useId();
  const reasonHintId = `${reasonId}-hint`;
  const reasonCountId = `${reasonId}-count`;
  const wasOpen = useRef(false);
  const maintenance = eventKind === "MAINTENANCE";
  const cancelLabel = maintenance ? t("bookings.details.cancelMaintenance") : t("bookings.actions.cancel");

  useEffect(() => {
    if (open && !wasOpen.current) {
      setReason("");
      setErrorKey(null);
    }
    wasOpen.current = open;
  }, [open]);

  const invalidateBookingQueries = async () => {
    await queryClient.invalidateQueries({ queryKey: ["api-v2", "bookings"] });
  };

  const handleDelete = async () => {
    if (activeRequest.current) return;
    activeRequest.current = true;
    setIsDeleting(true);
    setErrorKey(null);
    try {
      const cancelled = await cancelBooking(bookingId, bookingVersion, token, reason);
      await onDeleted(cancelled);
      await invalidateBookingQueries();
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
      {controlled ? null : iconOnly ? (
        <Tooltip>
          <TooltipTrigger render={<span className="inline-flex" tabIndex={-1} />}>
            <AlertDialogTrigger
              disabled={disabled || isDeleting}
              render={<Button type="button" size="icon-lg" variant={triggerVariant} aria-label={cancelLabel} />}
            >
              <CalendarX2Icon aria-hidden="true" />
            </AlertDialogTrigger>
          </TooltipTrigger>
          <TooltipContent role="tooltip">{cancelLabel}</TooltipContent>
        </Tooltip>
      ) : (
        <AlertDialogTrigger
          disabled={disabled || isDeleting}
          render={<Button type="button" size="sm" variant={triggerVariant} />}
        >
          {cancelLabel}
        </AlertDialogTrigger>
      )}
      <AlertDialogContent finalFocus={finalFocus}>
        <AlertDialogHeader>
          <AlertDialogTitle>
            {maintenance ? t("bookings.details.cancelMaintenanceTitle") : t("bookings.cancelDialog.title")}
          </AlertDialogTitle>
          <AlertDialogDescription>
            {maintenance
              ? t("bookings.details.cancelMaintenanceDescription")
              : itemName && period
                ? t("bookings.cancelDialog.description", { itemName, period })
                : t("bookings.details.cancelDescription")}
          </AlertDialogDescription>
        </AlertDialogHeader>
        <div className="space-y-2">
          <label className="text-sm font-medium" htmlFor={reasonId}>
            {t("bookings.cancelDialog.reasonLabel")}
          </label>
          <Textarea
            id={reasonId}
            value={reason}
            maxLength={CANCELLATION_REASON_MAX_LENGTH}
            rows={3}
            aria-describedby={`${reasonHintId} ${reasonCountId}`}
            onChange={(event) => setReason(event.currentTarget.value)}
          />
          <div className="flex items-start justify-between gap-3 text-xs text-muted-foreground">
            <p id={reasonHintId}>
              {t(maintenance ? "bookings.cancelDialog.maintenanceReasonHint" : "bookings.cancelDialog.reasonHint")}
            </p>
            <span id={reasonCountId} className="shrink-0" aria-live="polite">
              {t("bookings.cancelDialog.reasonCount", { count: reason.length })}
            </span>
          </div>
        </div>
        {errorKey && (
          <p role="alert" className="text-sm text-destructive">
            {t(errorKey)}
          </p>
        )}
        <AlertDialogFooter>
          <AlertDialogCancel disabled={isDeleting}>
            {maintenance ? t("bookings.cancelDialog.keepMaintenance") : t("bookings.cancelDialog.keep")}
          </AlertDialogCancel>
          <AlertDialogAction
            type="button"
            variant="destructive"
            disabled={isDeleting}
            aria-busy={isDeleting}
            onClick={() => void handleDelete()}
          >
            {cancelLabel}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
