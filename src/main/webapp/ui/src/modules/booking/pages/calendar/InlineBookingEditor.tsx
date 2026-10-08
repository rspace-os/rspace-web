import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useLocation } from "@tanstack/react-router";
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
import {
  bookingProblemFeedback,
  isBookingConflictError,
  withBookingProblemConflict,
} from "@/modules/booking/creation/useCreateBooking";
import { bookingConflicts } from "@/modules/booking/domain/availability";
import {
  ApiV2ProblemError,
  type BookingListDocument,
  type BookingUpdate,
  updateBooking,
} from "@/modules/booking/domain/booking";
import { ALWAYS_OPEN } from "@/modules/booking/domain/bookingOpeningHours";
import { wallClockDraftFromInstants } from "@/modules/booking/domain/bookingTime";
import { createBookingEventNotice, useBookingNotices } from "@/modules/booking/feedback/BookingNotices";
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

export function InlineBookingEditor({
  event,
  timezone,
  timelineDate,
  token,
  onClose,
  timelineEventElement,
}: {
  event: BookingListDocument & EditableBooking;
  timezone: string;
  timelineDate?: string;
  token: string;
  onClose: () => void;
  timelineEventElement?: HTMLElement | null;
}) {
  const { t } = useTranslation(["booking", "common"]);
  const { t: bookingT } = useTranslation("booking");
  const queryClient = useQueryClient();
  const searchStr = useLocation({ select: (location) => location.searchStr });
  const notices = useBookingNotices();
  const configuration = useBookableItemConfiguration(event.target.globalId, token);
  // The booking the draft started from: the form's defaults and the base of the patch. A refetched `event` (a newer
  // version) never replaces the draft; only discarding it does.
  const [base, setBase] = React.useState(event);
  const [formKey, setFormKey] = React.useState(0);
  // The version a save was rejected against because the booking had changed; set until the draft is saved or discarded.
  const [rejectedVersion, setRejectedVersion] = React.useState<number>();
  const staleEdit = rejectedVersion !== undefined;
  // The rejection refetches the calendar; once it brings a newer version the next save is sent against that version.
  const latestVersionLoaded = staleEdit && event.version !== rejectedVersion;
  const editorRef = React.useRef<HTMLDivElement>(null);
  const staleAlertRef = React.useRef<HTMLDivElement>(null);
  const [formState, setFormState] = React.useState<BookingFormState | null>(null);
  const [windowAdjustment, setWindowAdjustment] = React.useState<BookingFormState["draft"]>();
  React.useEffect(() => {
    editorRef.current?.querySelector<HTMLTextAreaElement>("textarea")?.focus();
  }, [configuration.isPending]);
  const mutation = useMutation({
    mutationFn: async ({ submission, version }: { submission: BookingFormSubmission; version: number }) => {
      // Only the fields changed from where the draft started, so saving over a newer version keeps that version's
      // other changes.
      const patch: BookingUpdate = {
        ...(submission.window.start !== base.start ? { start: submission.window.start } : {}),
        ...(submission.window.end !== base.end ? { end: submission.window.end } : {}),
        ...(submission.purpose !== base.purpose ? { purpose: submission.purpose } : {}),
      };
      return Object.keys(patch).length === 0 ? null : updateBooking(event.id, version, patch, token);
    },
    onSuccess: async (updated, { submission }) => {
      if (updated) {
        notices.notify(
          "calendar",
          createBookingEventNotice({
            event: updated,
            message:
              updated.kind === "MAINTENANCE"
                ? t("bookings.feedback.maintenanceUpdated", {
                    itemName: submission.target.name,
                  })
                : t("bookings.feedback.eventUpdated", {
                    itemName: submission.target.name,
                  }),
            timeZone: timezone,
            searchStr,
          }),
        );
      }
      await queryClient.invalidateQueries({ queryKey: ["api-v2", "bookings"] });
      onClose();
    },
    onError: async (error, { version }) => {
      if (isStaleEditError(error)) {
        setRejectedVersion(version);
        await queryClient.invalidateQueries({ queryKey: ["api-v2", "bookings"] });
        return;
      }
      if (
        error instanceof ApiV2ProblemError &&
        (error.code === "errors.api.v2.forbidden" || error.code === "errors.api.v2.booking.state.transition")
      ) {
        await queryClient.invalidateQueries({ queryKey: ["api-v2", "bookings"] });
      }
      if (error instanceof ApiV2ProblemError && error.code === "errors.api.v2.booking.target.unavailable") {
        await queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-configurations"] });
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
    // Commit the reloaded form now: the focused discard button leaves with the alert, and focus must move to the
    // new purpose field before the popover reclaims it.
    flushSync(() => {
      resetMutation();
      setBase(event);
      setRejectedVersion(undefined);
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
    token,
  );
  const checkingAvailability = Boolean(availabilityRow && intervalChanged && availability.isPending);
  const availabilityViolation = Boolean(
    availability.isSuccess &&
      availability.data.get(event.target.globalId)?.some((interval) => interval.source.id !== `booking:${event.id}`),
  );
  const conflicts = availabilityViolation
    ? bookingConflicts(availability.data?.get(event.target.globalId) ?? [], timezone, event.id)
    : [];
  const maintenanceConflict = conflicts.some(({ kind }) => kind === "MAINTENANCE");
  const conflictBlocksSubmission =
    availabilityViolation && (maintenanceConflict || configuration.data?.allowDoubleBooking === false);

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
          verticalAnchor={timelineEventElement}
          date={timelineDate}
          timezone={timezone}
          schedulingTimezone={configuration.data.timezone}
          draft={timelineDraft}
          snapIncrementMinutes={configuration.data.slotGranularityMinutes}
          onChange={setWindowAdjustment}
          tone={event.kind === "MAINTENANCE" ? "maintenance" : "booking"}
        />
      ) : null}
      <div ref={editorRef} className="flex max-h-[min(22rem,45vh)] flex-col border-border border-t">
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
            <Button
              type="button"
              variant="outline"
              size="xs"
              disabled={!latestVersionLoaded || mutation.isPending}
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
          token={token}
          pending={mutation.isPending}
          error={
            problem.message ??
            (!saveError && availabilityViolation && conflicts.length === 0 ? t("bookings.errors.overlap") : undefined)
          }
          conflicts={withBookingProblemConflict(conflicts, problem.conflict)}
          conflictSeverity={
            problem.conflict || !configuration.data.allowDoubleBooking || maintenanceConflict ? "error" : "warning"
          }
          submissionBlocked={
            checkingAvailability ||
            conflictBlocksSubmission ||
            isBookingConflictError(mutation.error) ||
            (staleEdit && !latestVersionLoaded)
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
              version: latestVersionLoaded ? event.version : base.version,
            })
          }
        />
      </div>
    </>
  );
}
