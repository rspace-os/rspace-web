import { useMutation, useQueryClient } from "@tanstack/react-query";
import * as React from "react";
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
import { Button } from "@/modules/common/ui/button";
import { Skeleton } from "@/modules/common/ui/skeleton";
import { calendarAvailabilityRow, useCalendarAvailability } from "./calendarAvailability";

export function InlineBookingEditor({
  event,
  timezone,
  timelineDate,
  token,
  onClose,
}: {
  event: BookingListDocument & EditableBooking;
  timezone: string;
  timelineDate?: string;
  token: string;
  onClose: () => void;
}) {
  const { t } = useTranslation(["booking", "common"]);
  const { t: bookingT } = useTranslation("booking");
  const queryClient = useQueryClient();
  const configuration = useBookableItemConfiguration(event.target.globalId, token);
  const [formState, setFormState] = React.useState<BookingFormState | null>(null);
  const [windowAdjustment, setWindowAdjustment] = React.useState<BookingFormState["draft"]>();
  const mutation = useMutation({
    mutationFn: (submission: BookingFormSubmission) => {
      const patch: BookingUpdate = {
        ...(submission.window.start !== event.start ? { start: submission.window.start } : {}),
        ...(submission.window.end !== event.end ? { end: submission.window.end } : {}),
        ...(submission.purpose !== event.purpose ? { purpose: submission.purpose } : {}),
      };
      return Object.keys(patch).length === 0
        ? Promise.resolve(event)
        : updateBooking(event.id, event.version, patch, token);
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["api-v2", "bookings"] });
      onClose();
    },
    onError: async (error) => {
      if (
        error instanceof ApiV2ProblemError &&
        (error.status === 412 ||
          error.code === "errors.api.v2.booking.concurrentModification" ||
          error.code === "errors.api.v2.forbidden" ||
          error.code === "errors.api.v2.booking.state.transition")
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
  // Checked as soon as both endpoints are entered, before the item's rules are met.
  const enteredWindow = formState?.enteredWindow;
  const intervalChanged = Boolean(
    enteredWindow && (enteredWindow.start !== event.start || enteredWindow.end !== event.end),
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
  const timelineEventElement = timelineDate
    ? Array.from(document.querySelectorAll<HTMLElement>(`[data-event-id="${event.id}"]`)).find(
        (element) => element.closest<HTMLElement>("[data-timeline-date]")?.dataset.timelineDate === timelineDate,
      )
    : undefined;
  const timelineDraft =
    windowAdjustment ?? formState?.draft ?? wallClockDraftFromInstants(event.start, event.end, timezone);
  // A buffer rejection needs its own sentence; a server-named overlap is listed alongside the local conflicts.
  const problem = bookingProblemFeedback(mutation.error, bookingT, {
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
      <div className="flex max-h-[min(22rem,45vh)] flex-col border-border border-t">
        <BookingForm
          key={event.version}
          mode="edit"
          density="compact"
          displayTimezone={timezone}
          booking={event}
          configuration={configuration.data}
          token={token}
          pending={mutation.isPending}
          error={
            problem.message ??
            (!mutation.error && availabilityViolation && conflicts.length === 0
              ? t("bookings.errors.overlap")
              : undefined)
          }
          conflicts={withBookingProblemConflict(conflicts, problem.conflict)}
          conflictSeverity={
            problem.conflict || !configuration.data.allowDoubleBooking || maintenanceConflict ? "error" : "warning"
          }
          submissionBlocked={checkingAvailability || conflictBlocksSubmission || isBookingConflictError(mutation.error)}
          windowAdjustment={windowAdjustment}
          onStateChange={clearMutationErrorOnChange}
          onCancel={() => {
            mutation.reset();
            onClose();
          }}
          onSubmit={(submission) => mutation.mutateAsync(submission)}
        />
      </div>
    </>
  );
}
