import { useBlocker } from "@tanstack/react-router";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { BookingForm, type BookingFormState } from "@/modules/booking/creation/BookingForm";
import { useBookingCreationStore } from "@/modules/booking/creation/bookingCreationStore";
import { bookingProblemKey, useCreateBooking } from "@/modules/booking/creation/useCreateBooking";
import { isBookingOverlapError } from "@/modules/booking/domain/booking";
import { useBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import {
  calendarAvailabilityRow,
  useCalendarAvailability,
} from "@/modules/booking/pages/calendar/calendarAvailability";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
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
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/modules/common/ui/dialog";

export function CompactBookingCreationDialog() {
  const { t } = useTranslation("booking");
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const preferences = useBookingDisplayPreferences();
  const creation = useBookingCreationStore((state) => state.activeCreation);
  const endCreation = useBookingCreationStore((state) => state.endCreation);
  const mutation = useCreateBooking(token);
  const mutationHasError = React.useRef(false);
  mutationHasError.current = mutation.isError;
  const [dirty, setDirty] = React.useState(false);
  const [formState, setFormState] = React.useState<BookingFormState | null>(null);
  const [confirmClose, setConfirmClose] = React.useState(false);
  const [navigationPending, setNavigationPending] = React.useState(false);
  const blocker = useBlocker({
    shouldBlockFn: () => creation !== null,
    withResolver: true,
    enableBeforeUnload: creation !== null && dirty,
  });

  React.useEffect(() => {
    if (creation) {
      setDirty(false);
      setFormState(null);
      setConfirmClose(false);
      setNavigationPending(false);
      mutation.reset();
    }
  }, [creation?.ownerId]);

  const finish = React.useCallback(() => {
    if (!creation) return;
    const { ownerId, triggerId } = creation;
    endCreation(ownerId);
    window.setTimeout(() => document.getElementById(triggerId)?.focus(), 0);
  }, [creation, endCreation]);

  React.useEffect(() => {
    if (blocker.status !== "blocked") return;
    if (!dirty) {
      finish();
      blocker.proceed();
      return;
    }
    setNavigationPending(true);
    setConfirmClose(true);
  }, [blocker, dirty, finish]);

  React.useEffect(
    () => () => {
      if (creation) endCreation(creation.ownerId);
    },
    [creation, endCreation],
  );

  const requestClose = () => {
    if (dirty) setConfirmClose(true);
    else finish();
  };

  const discard = () => {
    setConfirmClose(false);
    finish();
    if (navigationPending && blocker.status === "blocked") blocker.proceed();
  };

  const keepEditing = () => {
    setConfirmClose(false);
    if (navigationPending && blocker.status === "blocked") blocker.reset();
    setNavigationPending(false);
  };

  const resetMutation = mutation.reset;
  const updateFormState = React.useCallback(
    (state: BookingFormState) => {
      setDirty(state.dirty);
      setFormState(state);
      if (!mutationHasError.current) return;
      mutationHasError.current = false;
      resetMutation();
    },
    [resetMutation],
  );

  const availabilityTarget = formState?.target ?? creation?.target;
  const availabilityRow = availabilityTarget ? calendarAvailabilityRow(availabilityTarget) : undefined;
  const availabilityInterval = formState?.window
    ? {
        ...formState.window,
        date: formState.draft.startDate,
        timeZone: availabilityTarget?.timezone ?? preferences.timeZone,
        elapsedMinutes: (Date.parse(formState.window.end) - Date.parse(formState.window.start)) / 60_000,
      }
    : { start: "", end: "", date: "", timeZone: preferences.timeZone, elapsedMinutes: 0 };
  useCalendarAvailability(availabilityRow && formState?.window ? [availabilityRow] : [], availabilityInterval, token);

  if (!creation) return null;
  const maintenance = creation.eventKind === "MAINTENANCE";

  return (
    <>
      <Dialog
        open
        onOpenChange={(open, eventDetails) => {
          if (open) return;
          if (dirty) eventDetails.cancel();
          requestClose();
        }}
      >
        <DialogContent
          className="max-h-[calc(100dvh-2rem)] overflow-y-auto sm:max-w-2xl"
          data-testid="compact-booking-dialog"
        >
          <DialogHeader>
            <DialogTitle>
              {t(maintenance ? "bookings.compact.maintenanceTitle" : "bookings.compact.bookingTitle")}
            </DialogTitle>
            <DialogDescription>
              {t(maintenance ? "bookings.compact.maintenanceDescription" : "bookings.compact.bookingDescription")}
            </DialogDescription>
          </DialogHeader>
          <BookingForm
            mode="add"
            density="compact"
            displayTimezone={preferences.timeZone}
            eventKind={creation.eventKind}
            initialTarget={creation.target}
            initialDate={creation.initialDate}
            initialWindow={creation.window}
            lockTarget={creation.lockTarget}
            token={token}
            pending={mutation.isPending}
            error={mutation.error ? t(bookingProblemKey(mutation.error)) : undefined}
            submissionBlocked={isBookingOverlapError(mutation.error)}
            onCancel={requestClose}
            onStateChange={updateFormState}
            onSubmit={async (submission) => {
              await mutation.mutateAsync(submission);
              finish();
            }}
          />
        </DialogContent>
      </Dialog>
      <AlertDialog open={confirmClose} onOpenChange={(open) => !open && keepEditing()}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t("bookings.compact.discardTitle")}</AlertDialogTitle>
            <AlertDialogDescription>{t("bookings.compact.discardDescription")}</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel onClick={keepEditing}>{t("bookings.compact.keepEditing")}</AlertDialogCancel>
            <AlertDialogAction variant="destructive" onClick={discard}>
              {t("bookings.compact.discard")}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
