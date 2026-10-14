import { useBlocker, useLocation, useNavigate } from "@tanstack/react-router";
import { XIcon } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { BookingForm, type BookingFormState } from "@/modules/booking/creation/BookingForm";
import type { BookingCreationDraft } from "@/modules/booking/creation/bookingCreationDraft";
import { type BookingCreationContext, useBookingCreationStore } from "@/modules/booking/creation/bookingCreationStore";
import { DraftMarker } from "@/modules/booking/creation/DraftMarker";
import type { TimelineMovePolicy } from "@/modules/booking/creation/timelineMove";
import {
  bookingProblemFeedback,
  isBookingConflictError,
  isBookingOutcomeUncertain,
  useCreateBooking,
  withBookingProblemConflict,
} from "@/modules/booking/creation/useCreateBooking";
import { bookingConflicts, calendarAvailabilityRow } from "@/modules/booking/domain/availability";
import { useBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import { ALWAYS_OPEN } from "@/modules/booking/domain/bookingOpeningHours";
import { useCalendarAvailability } from "@/modules/booking/domain/useCalendarAvailability";
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
  const blocker = useBlocker({
    shouldBlockFn: shouldBlock,
    withResolver: true,
    enableBeforeUnload: shouldBlock,
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
      if (!mutation.isError || isBookingOutcomeUncertain(mutation.error)) return;
      resetMutation();
    },
    [mutation.error, mutation.isError, resetMutation],
  );

  const maintenance = creation.eventKind === "MAINTENANCE";
  const availabilityTarget = formState?.target ?? creation.target;
  const availabilityRow = availabilityTarget
    ? calendarAvailabilityRow({
        ...availabilityTarget,
        // Opening hours are the form's own check, so closed time must not read as an overlap here.
        ...ALWAYS_OPEN,
        // Fetch overlaps even when the configuration permits double booking so the form can explain them.
        allowDoubleBooking: false,
      })
    : undefined;
  // Checked as soon as both endpoints are entered, before the item's rules are met.
  const enteredWindow = formState?.enteredWindow;
  const availabilityInterval = enteredWindow
    ? {
        ...enteredWindow,
        date: formState.draft.startDate,
        timeZone: availabilityTarget?.timezone ?? preferences.timeZone,
        elapsedMinutes: (Date.parse(enteredWindow.end) - Date.parse(enteredWindow.start)) / 60_000,
      }
    : { start: "", end: "", date: "", timeZone: preferences.timeZone, elapsedMinutes: 0 };
  const availability = useCalendarAvailability(
    availabilityRow && enteredWindow ? [availabilityRow] : [],
    availabilityInterval,
    token,
  );
  const checkingAvailability = Boolean(availabilityRow && enteredWindow && availability.isPending);
  const availabilityViolation = Boolean(
    availability.isSuccess &&
      availabilityTarget &&
      (availability.data.get(availabilityTarget.globalId)?.length ?? 0) > 0,
  );
  const conflicts = availabilityViolation
    ? bookingConflicts(availability.data?.get(availabilityTarget?.globalId ?? "") ?? [], preferences.timeZone)
    : [];
  const canOverlapBookings =
    creation.eventKind === "BOOKING" &&
    availabilityTarget?.allowDoubleBooking === true &&
    conflicts.length > 0 &&
    conflicts.every(({ kind }) => kind === "BOOKING");
  const conflictBlocksSubmission = availabilityViolation && !canOverlapBookings;
  const problem = bookingProblemFeedback(mutation.error, t, {
    displayTimezone: preferences.timeZone,
    target: availabilityTarget && {
      timezone: availabilityTarget.timezone,
      maxBookingDurationMinutes: maintenance ? 0 : availabilityTarget.maxBookingDurationMinutes,
    },
    creation: true,
  });

  const anchor = document.getElementById(creation.triggerId);
  const markerDraft = windowAdjustment ?? formState?.draft ?? creation.window;
  const movementPolicy: TimelineMovePolicy = {
    maxBookingDurationMinutes: maintenance ? 0 : (availabilityTarget?.maxBookingDurationMinutes ?? 0),
    openingHours: availabilityTarget?.openingHours ?? [],
    enforceOpeningHours: Boolean(availabilityTarget) && !maintenance,
  };

  return (
    <>
      {markerDraft ? (
        <DraftMarker
          creation={creation}
          draft={markerDraft}
          timeZone={preferences.timeZone}
          schedulingTimezone={availabilityTarget?.timezone ?? preferences.timeZone}
          snapIncrementMinutes={availabilityTarget?.slotGranularityMinutes}
          movementPolicy={movementPolicy}
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
            fixedTimezone
            displayTimezone={preferences.timeZone}
            eventKind={creation.eventKind}
            initialTarget={creation.target}
            initialDate={creation.initialDate}
            initialWindow={creation.window}
            lockTarget={creation.lockTarget}
            token={token}
            pending={mutation.isPending}
            error={
              availabilityViolation && conflicts.length === 0 && !problem.conflict
                ? t("bookings.errors.overlap")
                : problem.message
            }
            conflicts={withBookingProblemConflict(conflicts, problem.conflict)}
            conflictSeverity={canOverlapBookings && !isBookingConflictError(mutation.error) ? "warning" : "error"}
            outcomeUncertain={isBookingOutcomeUncertain(mutation.error)}
            submissionBlocked={
              checkingAvailability ||
              conflictBlocksSubmission ||
              isBookingConflictError(mutation.error) ||
              isBookingOutcomeUncertain(mutation.error)
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
