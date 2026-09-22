import { useBlocker, useLocation, useNavigate } from "@tanstack/react-router";
import { XIcon } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { BookingForm, type BookingFormState } from "@/modules/booking/creation/BookingForm";
import type { BookingCreationDraft } from "@/modules/booking/creation/bookingCreationDraft";
import { type BookingCreationContext, useBookingCreationStore } from "@/modules/booking/creation/bookingCreationStore";
import { DraftMarker } from "@/modules/booking/creation/DraftMarker";
import {
  bookingCreationProblemKey,
  isBookingCreationOutcomeUncertain,
  useCreateBooking,
} from "@/modules/booking/creation/useCreateBooking";
import { bookingConflicts } from "@/modules/booking/domain/availability";
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
import { Button } from "@/modules/common/ui/button";
import { InventoryItem } from "@/modules/common/ui/inventory-item";
import { Popover, PopoverContent, PopoverDescription, PopoverHeader, PopoverTitle } from "@/modules/common/ui/popover";

export function ActiveBookingCreationDialog({ creation }: { creation: BookingCreationContext }) {
  const { t } = useTranslation("booking");
  const { t: commonT } = useTranslation("common");
  const navigate = useNavigate();
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const preferences = useBookingDisplayPreferences();
  const pathname = useLocation({ select: (location) => location.pathname });
  const endCreation = useBookingCreationStore((state) => state.endCreation);
  const mutation = useCreateBooking(token);
  const [formState, setFormState] = React.useState<BookingFormState | null>(null);
  const [windowAdjustment, setWindowAdjustment] = React.useState<BookingFormState["draft"]>();
  const [confirmClose, setConfirmClose] = React.useState(false);
  const dirty = formState?.dirty ?? false;
  const closingRef = React.useRef(false);
  const previousPathname = React.useRef(pathname);
  const shouldBlock = React.useCallback(() => dirty && !closingRef.current, [dirty]);
  const enableBeforeUnload = React.useCallback(() => dirty && !closingRef.current, [dirty]);
  const blocker = useBlocker({
    shouldBlockFn: shouldBlock,
    withResolver: true,
    enableBeforeUnload,
  });

  const closeCreation = React.useCallback(() => {
    closingRef.current = true;
    endCreation(creation.ownerId);
  }, [creation.ownerId, endCreation]);

  const finish = React.useCallback(() => {
    closeCreation();
    window.setTimeout(() => document.getElementById(creation.triggerId)?.focus(), 0);
  }, [closeCreation, creation.triggerId]);

  React.useEffect(() => {
    const routeChanged = previousPathname.current !== pathname;
    previousPathname.current = pathname;
    if (routeChanged && !dirty) finish();
  }, [creation, dirty, finish, pathname]);

  React.useEffect(() => {
    if (blocker.status !== "blocked") return;
    if (!dirty) {
      finish();
      blocker.proceed();
      return;
    }
  }, [blocker, dirty, finish]);

  const requestClose = () => {
    if (dirty) setConfirmClose(true);
    else finish();
  };

  const openMoreOptions = () => {
    if (maintenance) return;
    const draft: BookingCreationDraft | undefined = formState
      ? {
          targetGlobalId: formState.target?.globalId,
          window: formState.draft,
          purpose: formState.purpose,
        }
      : undefined;
    const search = {
      date: formState?.draft.startDate ?? creation.initialDate,
      target: formState?.target?.globalId ?? creation.target?.globalId,
    };
    closeCreation();
    window.setTimeout(
      () =>
        void navigate({
          to: "/booking/calendar/bookings/add",
          search,
          ...(draft ? { state: (previous) => ({ ...previous, bookingCreationDraft: draft }) } : {}),
        }),
      0,
    );
  };

  const discard = () => {
    setConfirmClose(false);
    finish();
    if (blocker.status === "blocked") blocker.proceed();
  };

  const keepEditing = () => {
    setConfirmClose(false);
    if (blocker.status === "blocked") blocker.reset();
  };

  const resetMutation = mutation.reset;
  const updateFormState = React.useCallback(
    (state: BookingFormState) => {
      setFormState(state);
      setWindowAdjustment(undefined);
      if (!mutation.isError || isBookingCreationOutcomeUncertain(mutation.error)) return;
      resetMutation();
    },
    [mutation.error, mutation.isError, resetMutation],
  );

  const maintenance = creation.eventKind === "MAINTENANCE";
  const availabilityTarget = formState?.target ?? creation.target;
  const availabilityRow = availabilityTarget
    ? calendarAvailabilityRow({
        ...availabilityTarget,
        openingStart: maintenance ? "00:00" : availabilityTarget.openingStart,
        openingEnd: maintenance ? "24:00" : availabilityTarget.openingEnd,
        // Fetch overlaps even when the configuration permits double booking so the form can explain them.
        allowDoubleBooking: false,
      })
    : undefined;
  const availabilityInterval = formState?.window
    ? {
        ...formState.window,
        date: formState.draft.startDate,
        timeZone: availabilityTarget?.timezone ?? preferences.timeZone,
        elapsedMinutes: (Date.parse(formState.window.end) - Date.parse(formState.window.start)) / 60_000,
      }
    : { start: "", end: "", date: "", timeZone: preferences.timeZone, elapsedMinutes: 0 };
  const availability = useCalendarAvailability(
    availabilityRow && formState?.window ? [availabilityRow] : [],
    availabilityInterval,
    token,
  );
  const checkingAvailability = Boolean(availabilityRow && formState?.window && availability.isPending);
  const availabilityViolation = Boolean(
    availability.isSuccess &&
      availabilityTarget &&
      availability.data.get(availabilityTarget.globalId)?.some(() => true),
  );
  const conflicts = availabilityViolation
    ? bookingConflicts(availability.data?.get(availabilityTarget?.globalId ?? "") ?? [], preferences.timeZone)
    : [];
  const conflictBlocksSubmission = availabilityViolation && !availabilityTarget?.allowDoubleBooking;

  const anchor = document.getElementById(creation.triggerId);
  const markerDraft = windowAdjustment ?? formState?.draft ?? creation.window;

  return (
    <>
      {markerDraft ? (
        <DraftMarker
          creation={creation}
          draft={markerDraft}
          timeZone={preferences.timeZone}
          snapIncrementMinutes={availabilityTarget?.slotGranularityMinutes}
          onChange={creation.timelineAdjustable ? setWindowAdjustment : undefined}
        />
      ) : null}
      <Popover
        open
        modal={false}
        onOpenChange={(open, eventDetails) => {
          if (open) return;
          if (eventDetails.reason === "outside-press" || eventDetails.reason === "focus-out") {
            eventDetails.cancel();
            return;
          }
          if (dirty) eventDetails.cancel();
          requestClose();
        }}
      >
        <PopoverContent
          anchor={anchor}
          align="start"
          side="bottom"
          sideOffset={8}
          collisionPadding={8}
          sticky
          role="dialog"
          className="flex max-h-[calc(100dvh-2rem)] w-[min(42rem,calc(100vw-2rem))] max-w-none flex-col gap-0 overflow-hidden rounded-lg border border-primary p-0 ring-4 ring-ring/20"
          data-testid="compact-booking-dialog"
        >
          <PopoverHeader className="shrink-0 border-border border-b px-4 py-3 pr-12">
            <PopoverTitle>
              {t(maintenance ? "bookings.compact.maintenanceTitle" : "bookings.compact.bookingTitle")}
            </PopoverTitle>
            {maintenance ? (
              <PopoverDescription>{t("bookings.compact.maintenanceDescription")}</PopoverDescription>
            ) : null}
            {creation.target && (
              <InventoryItem name={creation.target.name} globalId={creation.target.globalId} compact size="xs" />
            )}
          </PopoverHeader>
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            className="absolute top-3 right-3"
            aria-label={commonT("actions.close")}
            onClick={requestClose}
          >
            <XIcon aria-hidden="true" />
          </Button>
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
            error={
              availabilityViolation && conflicts.length === 0
                ? t("bookings.errors.overlap")
                : mutation.error
                  ? t(bookingCreationProblemKey(mutation.error))
                  : undefined
            }
            conflicts={conflicts}
            conflictSeverity={availabilityTarget?.allowDoubleBooking ? "warning" : "error"}
            outcomeUncertain={isBookingCreationOutcomeUncertain(mutation.error)}
            submissionBlocked={
              checkingAvailability ||
              conflictBlocksSubmission ||
              isBookingOverlapError(mutation.error) ||
              isBookingCreationOutcomeUncertain(mutation.error)
            }
            windowAdjustment={windowAdjustment}
            onCancel={requestClose}
            onMoreOptions={maintenance ? undefined : openMoreOptions}
            onStateChange={updateFormState}
            onSubmit={async (submission) => {
              await mutation.mutateAsync(submission);
              finish();
            }}
          />
        </PopoverContent>
      </Popover>
      <AlertDialog open={confirmClose || blocker.status === "blocked"} onOpenChange={(open) => !open && keepEditing()}>
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
