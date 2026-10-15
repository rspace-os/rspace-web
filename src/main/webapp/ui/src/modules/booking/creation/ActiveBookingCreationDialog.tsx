import { useBlocker, useLocation, useNavigate } from "@tanstack/react-router";
import { XIcon } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { BookingForm, type BookingFormState } from "@/modules/booking/creation/BookingForm";
import type { BookingCreationDraft } from "@/modules/booking/creation/bookingCreationDraft";
import { type BookingCreationContext, useBookingCreationStore } from "@/modules/booking/creation/bookingCreationStore";
import { DraftMarker } from "@/modules/booking/creation/DraftMarker";
import { timelinePolicy } from "@/modules/booking/creation/timelineRange";
import { useBookingDraftAvailability } from "@/modules/booking/creation/useBookingDraftAvailability";
import {
  bookingProblemFeedback,
  isBookingConflictError,
  isBookingOutcomeUncertain,
  useCreateBooking,
  withBookingProblemConflict,
} from "@/modules/booking/creation/useCreateBooking";
import { useBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import { createBookingEventNotice, useBookingNotices } from "@/modules/booking/feedback/BookingNotices";
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
  const searchStr = useLocation({ select: (location) => location.searchStr });
  const notices = useBookingNotices();
  const endCreation = useBookingCreationStore((state) => state.endCreation);
  const mutation = useCreateBooking(token);
  const [formState, setFormState] = React.useState<BookingFormState | null>(null);
  const [windowAdjustment, setWindowAdjustment] = React.useState<BookingFormState["draft"]>();
  const [confirmClose, setConfirmClose] = React.useState(false);
  const dirty = formState?.dirty ?? false;
  const closingRef = React.useRef(false);
  const previousPathname = React.useRef(pathname);
  const shouldBlock = React.useCallback(
    () => (dirty || mutation.isPending) && !closingRef.current,
    [dirty, mutation.isPending],
  );
  const blocker = useBlocker({
    shouldBlockFn: shouldBlock,
    withResolver: true,
    enableBeforeUnload: shouldBlock,
  });

  const closeCreation = React.useCallback(() => {
    closingRef.current = true;
    return endCreation(creation);
  }, [creation, endCreation]);

  const finish = React.useCallback(() => {
    if (closeCreation()) window.setTimeout(() => document.getElementById(creation.triggerId)?.focus(), 0);
  }, [closeCreation, creation.triggerId]);

  React.useEffect(() => {
    const routeChanged = previousPathname.current !== pathname;
    previousPathname.current = pathname;
    if (routeChanged && !dirty) finish();
  }, [creation, dirty, finish, pathname]);

  React.useEffect(() => {
    if (blocker.status !== "blocked") return;
    if (!dirty && !mutation.isPending) {
      finish();
      blocker.proceed();
    }
  }, [blocker, dirty, finish, mutation.isPending]);

  const requestClose = () => {
    if (mutation.isPending) return;
    if (dirty) setConfirmClose(true);
    else finish();
  };

  const openMoreOptions = () => {
    if (maintenance || mutation.isPending) return;
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
    if (mutation.isPending) return;
    setConfirmClose(false);
    finish();
    if (blocker.status === "blocked") blocker.proceed();
  };

  const keepEditing = () => {
    setConfirmClose(false);
    if (blocker.status === "blocked") blocker.reset();
  };

  const resetMutation = mutation.reset;
  const updateFormState = (state: BookingFormState) => {
    setFormState(state);
    setWindowAdjustment(undefined);
    if (!mutation.isError || isBookingOutcomeUncertain(mutation.error)) return;
    resetMutation();
  };

  const maintenance = creation.eventKind === "MAINTENANCE";
  const availabilityTarget = formState?.target ?? creation.target;
  const availability = useBookingDraftAvailability({
    formState: formState ? { ...formState, target: formState.target ?? creation.target } : undefined,
    displayTimezone: preferences.timeZone,
    token,
    eventKind: creation.eventKind,
  });
  const checkingAvailability = availability.checking;
  const availabilityViolation = availability.violation;
  const conflicts = availability.conflicts;
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

  return (
    <>
      {markerDraft ? (
        <DraftMarker
          creation={creation}
          draft={markerDraft}
          timeZone={preferences.timeZone}
          policy={
            availabilityTarget ? timelinePolicy(availabilityTarget, maintenance ? "MAINTENANCE" : "BOOKING") : undefined
          }
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
              {maintenance ? t("bookings.compact.maintenanceTitle") : t("bookings.compact.bookingTitle")}
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
            disabled={mutation.isPending}
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
              const created = await mutation.mutateAsync(submission);
              notices.notify(
                creation.originHost ?? "calendar",
                createBookingEventNotice({
                  event: created,
                  message:
                    submission.eventKind === "MAINTENANCE"
                      ? t("bookings.feedback.maintenanceAdded", {
                          itemName: submission.target.name,
                        })
                      : t("bookings.feedback.eventAdded", {
                          itemName: submission.target.name,
                        }),
                  timeZone: preferences.timeZone,
                  searchStr,
                }),
              );
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
            <AlertDialogAction variant="destructive" disabled={mutation.isPending} onClick={discard}>
              {t("bookings.compact.discard")}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
