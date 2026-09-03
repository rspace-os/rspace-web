import { useBlocker, useLocation, useNavigate } from "@tanstack/react-router";
import { XIcon } from "lucide-react";
import * as React from "react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";
import { BookingForm, type BookingFormState } from "@/modules/booking/creation/BookingForm";
import type { BookingCreationDraft } from "@/modules/booking/creation/bookingCreationDraft";
import { type BookingCreationContext, useBookingCreationStore } from "@/modules/booking/creation/bookingCreationStore";
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
import { Button } from "@/modules/common/ui/button";
import { Popover, PopoverContent, PopoverDescription, PopoverHeader, PopoverTitle } from "@/modules/common/ui/popover";

function minuteOf(date: string, time: string, originDate: string): number {
  const dayOffset = (Date.parse(`${date}T00:00:00Z`) - Date.parse(`${originDate}T00:00:00Z`)) / 86_400_000;
  const [hours, minutes] = time.split(":").map(Number);
  return dayOffset * 24 * 60 + hours * 60 + minutes;
}

function DraftMarker({ creation, draft }: { creation: BookingCreationContext; draft: BookingFormState["draft"] }) {
  const trigger = document.getElementById(creation.triggerId);
  const canvas =
    trigger?.querySelector<HTMLElement>('[data-testid="day-timeline-canvas"]') ??
    trigger?.closest("section")?.querySelector<HTMLElement>('[data-testid="day-timeline-canvas"]');
  const originDate = creation.initialDate;
  if (!canvas || !originDate || !draft.startDate || !draft.startTime || !draft.endDate || !draft.endTime) return null;
  const startMinute = minuteOf(draft.startDate, draft.startTime, originDate);
  const endMinute = minuteOf(draft.endDate, draft.endTime, originDate);
  if (endMinute <= 0 || startMinute >= 24 * 60 || endMinute <= startMinute) return null;
  const visibleStart = Math.max(0, startMinute);
  const visibleEnd = Math.min(24 * 60, endMinute);
  return createPortal(
    <div
      aria-hidden="true"
      data-testid="compact-booking-draft-marker"
      className={
        creation.eventKind === "MAINTENANCE"
          ? "pointer-events-none absolute top-8 bottom-0 z-40 rounded-sm border-2 border-amber-600 bg-amber-200/60 ring-3 ring-ring/40"
          : "pointer-events-none absolute top-8 bottom-0 z-40 rounded-sm border-2 border-primary bg-primary/25 ring-3 ring-ring/40"
      }
      style={{
        left: `${(visibleStart / (24 * 60)) * 100}%`,
        width: `${((visibleEnd - visibleStart) / (24 * 60)) * 100}%`,
      }}
    />,
    canvas,
  );
}

export function CompactBookingCreationDialog() {
  const { t } = useTranslation("booking");
  const { t: commonT } = useTranslation("common");
  const navigate = useNavigate();
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const preferences = useBookingDisplayPreferences();
  const pathname = useLocation({ select: (location) => location.pathname });
  const creation = useBookingCreationStore((state) => state.activeCreation);
  const endCreation = useBookingCreationStore((state) => state.endCreation);
  const mutation = useCreateBooking(token);
  const mutationHasError = React.useRef(false);
  mutationHasError.current = mutation.isError;
  const [dirty, setDirty] = React.useState(false);
  const [formState, setFormState] = React.useState<BookingFormState | null>(null);
  const [confirmClose, setConfirmClose] = React.useState(false);
  const [navigationPending, setNavigationPending] = React.useState(false);
  const previousPathname = React.useRef(pathname);
  const blocker = useBlocker({
    shouldBlockFn: () => creation !== null && dirty,
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
    const routeChanged = previousPathname.current !== pathname;
    previousPathname.current = pathname;
    if (routeChanged && creation && !dirty) finish();
  }, [creation, dirty, finish, pathname]);

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

  const openMoreOptions = () => {
    if (!creation || maintenance) return;
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
    endCreation(creation.ownerId);
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
  const anchor = document.getElementById(creation.triggerId);
  const markerDraft = formState?.draft ?? creation.window;

  return (
    <>
      {markerDraft ? <DraftMarker creation={creation} draft={markerDraft} /> : null}
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
            <PopoverDescription>
              {t(maintenance ? "bookings.compact.maintenanceDescription" : "bookings.compact.bookingDescription")}
            </PopoverDescription>
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
            error={mutation.error ? t(bookingProblemKey(mutation.error)) : undefined}
            submissionBlocked={isBookingOverlapError(mutation.error)}
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
