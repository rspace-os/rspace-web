import { Temporal } from "@js-temporal/polyfill";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { TriangleAlertIcon } from "lucide-react";
import * as React from "react";
import { flushSync } from "react-dom";
import { useTranslation } from "react-i18next";
import { useBookableItemConfiguration } from "@/modules/booking/creation/BookableItemPicker";
import {
  BookingForm,
  type BookingFormState,
  type BookingFormSubmission,
  type EditableBooking,
} from "@/modules/booking/creation/BookingForm";
import { TimelineWindowEditor } from "@/modules/booking/creation/TimelineWindowEditor";
import { timelinePolicy } from "@/modules/booking/creation/timelineRange";
import {
  bookingProblemFeedback,
  isBookingConflictError,
  isBookingOutcomeUncertain,
  withBookingProblemConflict,
} from "@/modules/booking/creation/useCreateBooking";
import { bookingConflicts } from "@/modules/booking/domain/availability";
import { type BookingListDocument, fetchBooking, updateBooking } from "@/modules/booking/domain/booking";
import { ALWAYS_OPEN } from "@/modules/booking/domain/bookingOpeningHours";
import { formatBookingDateTime, wallClockDraftFromInstants } from "@/modules/booking/domain/bookingTime";
import { ApiV2ProblemError } from "@/modules/common/api/v2/ApiV2ProblemError";
import { getSearchCalendarEventsQueryKey } from "@/modules/common/api/v2/generated/booking-calendar/booking-calendar";
import { getSearchCalendarCatalogueQueryKey } from "@/modules/common/api/v2/generated/booking-catalogue/booking-catalogue";
import { invalidateListBookingConfigurations } from "@/modules/common/api/v2/generated/booking-configurations/booking-configurations";
import {
  getUpdateBookingMutationOptions,
  invalidateGetBooking,
  invalidateListBookings,
} from "@/modules/common/api/v2/generated/bookings/bookings";
import type { BookingsUpdate } from "@/modules/common/api/v2/generated/models/bookingsUpdate";
import { Alert, AlertDescription } from "@/modules/common/ui/alert";

import { Button } from "@/modules/common/ui/button";
import { Skeleton } from "@/modules/common/ui/skeleton";
import { calendarAvailabilityRow, useCalendarAvailability } from "./calendarAvailability";

/** A save rejected because the booking changed after the version the draft was saved against. */
function isStaleEditError(error: unknown): boolean {
  return (
    error instanceof ApiV2ProblemError &&
    (error.status === 412 || error.code === "errors.api.v2.booking.concurrentModification")
  );
}

export const BookingEditorsContext = React.createContext<
  | {
      setEditors: React.Dispatch<React.SetStateAction<Map<string, BookingListDocument>>>;
      positions: ReadonlyMap<number, BookingListDocument>;
    }
  | undefined
>(undefined);

export function InlineBookingEditor({
  event,
  timezone,
  timelineDate,

  onClose,
  timelineEventElement,
}: {
  event: BookingListDocument & EditableBooking;
  timezone: string;
  timelineDate?: string;
  onClose: () => void;
  timelineEventElement?: HTMLElement | null;
}) {
  const { t } = useTranslation(["booking", "common"]);
  const { t: bookingT } = useTranslation("booking");
  const queryClient = useQueryClient();
  const configuration = useBookableItemConfiguration(event.target.globalId);
  // The booking the draft started from: the form's defaults and the base of the patch. A refetched `event` (a newer
  // version) never replaces the draft; only discarding it does.
  const [base, setBase] = React.useState(event);
  const editorId = React.useId();
  const setEditors = React.useContext(BookingEditorsContext)?.setEditors;
  React.useEffect(() => {
    setEditors?.((current) => new Map(current).set(editorId, base));
    return () =>
      setEditors?.((current) => {
        const next = new Map(current);
        next.delete(editorId);
        return next;
      });
  }, [editorId, base, setEditors]);
  const [formKey, setFormKey] = React.useState(0);
  // The version a save was rejected against because the booking had changed; set until the draft is saved or discarded.
  const [rejectedVersion, setRejectedVersion] = React.useState<number>();
  const staleEdit = rejectedVersion !== undefined;
  const [latest, setLatest] = React.useState<BookingListDocument & EditableBooking>();
  const [confirmedVersion, setConfirmedVersion] = React.useState<number>();
  const [checkingOutcome, setCheckingOutcome] = React.useState(false);
  const editorRef = React.useRef<HTMLDivElement>(null);
  const staleAlertRef = React.useRef<HTMLDivElement>(null);
  const [formState, setFormState] = React.useState<BookingFormState | null>(null);
  const [windowAdjustment, setWindowAdjustment] = React.useState<BookingFormState["draft"]>();
  const updateOptions = getUpdateBookingMutationOptions(queryClient);
  const mutation = useMutation({
    mutationFn: ({ submission, version }: { submission: BookingFormSubmission; version: number }) => {
      // Only the fields changed from where the draft started, so saving over a newer version keeps that version's
      // other changes.
      const patch: BookingsUpdate = {
        ...(staleEdit || submission.window.start !== base.start || submission.window.end !== base.end
          ? { start: submission.window.start, end: submission.window.end }
          : {}),
        ...(submission.purpose !== base.purpose ? { purpose: submission.purpose } : {}),
      };
      return Object.keys(patch).length === 0 ? Promise.resolve(event) : updateBooking(event.id, version, patch);
    },
    onSuccess: async (updated, _variables, onMutateResult, mutationContext) => {
      flushSync(onClose);
      if (updated) {
        await updateOptions.onSuccess?.(
          { id: updated.id },
          { id: event.id, data: {}, headers: { "If-Match": `"${event.version}"` } },
          onMutateResult,
          mutationContext,
        );
      }
      await Promise.all([
        invalidateListBookings(queryClient, undefined, { cancelRefetch: false }),
        invalidateGetBooking(queryClient, event.id, undefined, { cancelRefetch: false }),
        queryClient.invalidateQueries({ queryKey: getSearchCalendarEventsQueryKey() }, { cancelRefetch: false }),
        queryClient.invalidateQueries({ queryKey: getSearchCalendarCatalogueQueryKey() }, { cancelRefetch: false }),
      ]);
    },
    onError: async (error, { version, submission }) => {
      if (isStaleEditError(error) || isBookingOutcomeUncertain(error)) {
        setConfirmedVersion(undefined);
        setLatest(undefined);
        setCheckingOutcome(true);
        try {
          const server = await fetchBooking(event.id);
          if (
            isBookingOutcomeUncertain(error) &&
            Temporal.Instant.compare(server.start, submission.window.start) === 0 &&
            Temporal.Instant.compare(server.end, submission.window.end) === 0 &&
            server.purpose === submission.purpose &&
            server.state === base.state
          ) {
            onClose();
          } else {
            setRejectedVersion(version);
            if (server.target && server.canEdit && server.state === "CONFIRMED" && server.privacy === "full") {
              setLatest({
                ...event,
                ...server,
                target: server.target,
                canEdit: true,
                state: "CONFIRMED",
                privacy: "full",
              });
            }
          }
          await Promise.all([
            invalidateListBookings(queryClient),
            invalidateGetBooking(queryClient, event.id),
            queryClient.invalidateQueries({ queryKey: getSearchCalendarEventsQueryKey() }),
            queryClient.invalidateQueries({ queryKey: getSearchCalendarCatalogueQueryKey() }),
          ]);
        } catch {
          // Keep the original precondition until the server can be checked.
          setRejectedVersion(version);
        } finally {
          setCheckingOutcome(false);
        }
        return;
      }
      if (
        error instanceof ApiV2ProblemError &&
        (error.code === "errors.api.v2.forbidden" || error.code === "errors.api.v2.booking.state.transition")
      ) {
        await Promise.all([
          invalidateListBookings(queryClient),
          invalidateGetBooking(queryClient, event.id),
          queryClient.invalidateQueries({ queryKey: getSearchCalendarEventsQueryKey() }),
          queryClient.invalidateQueries({ queryKey: getSearchCalendarCatalogueQueryKey() }),
        ]);
      }
      if (error instanceof ApiV2ProblemError && error.code === "errors.api.v2.booking.target.unavailable") {
        await invalidateListBookingConfigurations(queryClient);
      }
    },
  });
  const resetMutation = mutation.reset;
  const clearMutationErrorOnChange = React.useCallback(
    (state: BookingFormState) => {
      setFormState(state);
      setWindowAdjustment(undefined);
      if (!mutation.isError) return;
      resetMutation();
    },
    [mutation.isError, resetMutation],
  );
  React.useEffect(() => {
    if (rejectedVersion !== undefined) staleAlertRef.current?.focus();
  }, [rejectedVersion]);
  const discardDraft = () => {
    if (!latest) {
      onClose();
      return;
    }
    // Commit the reloaded form now: the focused discard button leaves with the alert, and focus must move to the
    // new purpose field before the popover reclaims it.
    flushSync(() => {
      resetMutation();
      setBase(latest ?? event);
      setRejectedVersion(undefined);
      setLatest(undefined);
      setConfirmedVersion(undefined);
      setFormState(null);
      setWindowAdjustment(undefined);
      setFormKey((key) => key + 1);
    });
    editorRef.current?.querySelector<HTMLElement>("textarea")?.focus();
  };
  // Checked as soon as both endpoints are entered, before the item's rules are met.
  const enteredWindow = formState?.enteredWindow;
  const intervalChanged = Boolean(
    enteredWindow && (enteredWindow.start !== base.start || enteredWindow.end !== base.end),
  );
  const availabilityRow = configuration.data
    ? calendarAvailabilityRow({
        ...configuration.data,
        // Opening hours are the form's own check, so closed time must not read as an overlap here.
        ...ALWAYS_OPEN,
        // Fetch overlaps even when the configuration permits double booking so the form can explain them.
        allowDoubleBooking: false,
      })
    : undefined;
  const availabilityInterval = enteredWindow
    ? {
        ...enteredWindow,
        date: formState.draft.startDate,
        timeZone: configuration.data?.timezone ?? timezone,
        elapsedMinutes: (Date.parse(enteredWindow.end) - Date.parse(enteredWindow.start)) / 60_000,
      }
    : { start: "", end: "", date: "", timeZone: timezone, elapsedMinutes: 0 };
  const availability = useCalendarAvailability(
    availabilityRow && intervalChanged ? [availabilityRow] : [],
    availabilityInterval,
  );
  const checkingAvailability = Boolean(availabilityRow && intervalChanged && availability.isPending);
  const availabilityViolation = Boolean(
    availability.isSuccess &&
      availability.data.get(event.target.globalId)?.some((interval) => interval.source.id !== `booking:${event.id}`),
  );
  const conflicts = availabilityViolation
    ? bookingConflicts(availability.data?.get(event.target.globalId) ?? [], timezone, event.id)
    : [];
  const canOverlapBookings =
    event.kind === "BOOKING" &&
    configuration.data?.allowDoubleBooking === true &&
    conflicts.length > 0 &&
    conflicts.every(({ kind }) => kind === "BOOKING");
  const conflictBlocksSubmission = availabilityViolation && !canOverlapBookings;

  if (configuration.isPending) {
    return (
      <div className="space-y-4 border-border border-t p-4" aria-busy="true">
        <p role="status" className="sr-only">
          {t("bookings.loadingConfiguration")}
        </p>
        <Skeleton aria-hidden="true" className="h-12 w-full" />
        <Skeleton aria-hidden="true" className="h-24 w-full" />
      </div>
    );
  }
  if (configuration.isError || !configuration.data) {
    return (
      <div className="space-y-3 border-border border-t p-4">
        <p role="alert">{t("bookings.errors.targetUnavailable")}</p>
        <div className="flex gap-2">
          <Button type="button" variant="outline" size="sm" onClick={() => void configuration.refetch()}>
            {t("common:actions.retry")}
          </Button>
          <Button type="button" variant="ghost" size="sm" onClick={onClose}>
            {t("bookings.form.cancel")}
          </Button>
        </div>
      </div>
    );
  }
  const timelineDraft =
    windowAdjustment ?? formState?.draft ?? wallClockDraftFromInstants(base.start, base.end, timezone);
  // The stale-edit alert below explains a 412 for as long as the draft is kept, so the form does not repeat it.
  const saveError = isStaleEditError(mutation.error) ? null : mutation.error;
  // A buffer rejection needs its own sentence; a server-named overlap is listed alongside the local conflicts.
  const problem = bookingProblemFeedback(saveError, bookingT, {
    displayTimezone: timezone,
    target: configuration.data,
  });
  return (
    <>
      {timelineDate && timelineEventElement ? (
        <TimelineWindowEditor
          anchor={timelineEventElement}
          verticalAnchor={timelineEventElement.closest<HTMLElement>("[data-event-id]") ?? timelineEventElement}
          date={timelineDate}
          timezone={timezone}
          draft={timelineDraft}
          policy={timelinePolicy(configuration.data, event.kind === "MAINTENANCE" ? "MAINTENANCE" : "BOOKING")}
          onChange={setWindowAdjustment}
          tone={event.kind === "MAINTENANCE" ? "maintenance" : "booking"}
        />
      ) : null}
      <div
        ref={editorRef}
        className="flex min-h-0 max-h-[min(22rem,45vh)] max-sm:max-h-[60dvh] flex-col border-border border-t"
      >
        {staleEdit ? (
          <div className="shrink-0 space-y-2 border-border border-b px-4 py-3">
            <Alert
              ref={staleAlertRef}
              tabIndex={-1}
              variant="destructive"
              className="border-red-700 bg-red-100 text-red-950 outline-none focus-visible:ring-3 focus-visible:ring-ring/40 *:data-[slot=alert-description]:text-red-950 dark:border-red-400 dark:bg-red-950 dark:text-red-200 dark:*:data-[slot=alert-description]:text-red-200"
            >
              <TriangleAlertIcon aria-hidden="true" />
              <AlertDescription>{t("calendar.inlineEditor.staleEdit")}</AlertDescription>
            </Alert>
            {latest ? (
              <>
                <p>
                  {t("calendar.inlineEditor.serverInterval", {
                    start: formatBookingDateTime(latest.start, timezone),
                    end: formatBookingDateTime(latest.end, timezone),
                  })}
                </p>
                <p>
                  {t("calendar.inlineEditor.draftInterval", {
                    start: formatBookingDateTime(enteredWindow?.start ?? base.start, timezone),
                    end: formatBookingDateTime(enteredWindow?.end ?? base.end, timezone),
                  })}
                </p>
                <Button
                  type="button"
                  size="xs"
                  disabled={mutation.isPending || checkingOutcome}
                  onClick={() => setConfirmedVersion(latest.version)}
                >
                  {t("calendar.inlineEditor.confirmInterval", {})}
                </Button>
              </>
            ) : null}
            {!latest ? (
              <Button
                type="button"
                size="xs"
                disabled={checkingOutcome || mutation.isPending}
                onClick={async () => {
                  setCheckingOutcome(true);
                  try {
                    const server = await fetchBooking(event.id);
                    if (server.target && server.canEdit && server.state === "CONFIRMED" && server.privacy === "full") {
                      setLatest({
                        ...event,
                        ...server,
                        target: server.target,
                        canEdit: true,
                        state: "CONFIRMED",
                        privacy: "full",
                      });
                    }
                  } catch {
                    // Leave retry and discard available after an unsuccessful re-check.
                  } finally {
                    setCheckingOutcome(false);
                  }
                }}
              >
                {t("common:actions.retry")}
              </Button>
            ) : null}
            <Button
              type="button"
              variant="outline"
              size="xs"
              disabled={mutation.isPending || checkingOutcome}
              onClick={discardDraft}
            >
              {t("calendar.inlineEditor.discardStaleEdit")}
            </Button>
          </div>
        ) : null}
        <BookingForm
          key={formKey}
          mode="edit"
          density="compact"
          displayTimezone={timezone}
          booking={base}
          configuration={configuration.data}
          pending={mutation.isPending || checkingOutcome}
          error={
            isBookingOutcomeUncertain(saveError)
              ? t("bookings.errors.outcomeUncertain")
              : (problem.message ??
                (!saveError && availabilityViolation && conflicts.length === 0
                  ? t("bookings.errors.overlap")
                  : undefined))
          }
          conflicts={withBookingProblemConflict(conflicts, problem.conflict)}
          conflictSeverity={canOverlapBookings && !isBookingConflictError(mutation.error) ? "warning" : "error"}
          submissionBlocked={
            checkingAvailability ||
            conflictBlocksSubmission ||
            isBookingConflictError(mutation.error) ||
            (staleEdit && (!latest || confirmedVersion !== latest.version)) ||
            checkingOutcome
          }
          windowAdjustment={windowAdjustment}
          onStateChange={clearMutationErrorOnChange}
          onCancel={() => {
            mutation.reset();
            onClose();
          }}
          onSubmit={(submission) =>
            mutation.mutateAsync({
              submission,
              version: staleEdit && latest && confirmedVersion === latest.version ? latest.version : base.version,
            })
          }
        />
      </div>
    </>
  );
}
