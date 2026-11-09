import { useMutation, useQueryClient } from "@tanstack/react-query";
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
import { bookingProblemKey, isBookingOutcomeUncertain } from "@/modules/booking/creation/useCreateBooking";
import { type BookingListDocument, isBookingOverlapError, updateBooking } from "@/modules/booking/domain/booking";
import { wallClockDraftFromInstants } from "@/modules/booking/domain/bookingTime";
import { ApiV2ProblemError } from "@/modules/common/api/v2/ApiV2ProblemError";
import { invalidateListBookingConfigurations } from "@/modules/common/api/v2/generated/booking-configurations/booking-configurations";
import {
  getUpdateBookingMutationOptions,
  invalidateGetBooking,
  invalidateListBookings,
} from "@/modules/common/api/v2/generated/bookings/bookings";
import type { BookingsUpdate } from "@/modules/common/api/v2/generated/models/bookingsUpdate";
import { Button } from "@/modules/common/ui/button";
import { Skeleton } from "@/modules/common/ui/skeleton";
import { calendarAvailabilityRow, useCalendarAvailability } from "./calendarAvailability";

export const BookingEditorsContext = React.createContext<
  | {
      setEditors: React.Dispatch<React.SetStateAction<Map<string, BookingListDocument>>>;
      positions: ReadonlyMap<number, BookingListDocument>;
    }
  | undefined
>(undefined);

export function InlineBookingEditor({
  event: initialEvent,
  timezone,
  timelineDate,

  onClose,
}: {
  event: BookingListDocument & EditableBooking;
  timezone: string;
  timelineDate?: string;
  onClose: () => void;
}) {
  const { t } = useTranslation(["booking", "common"]);
  const queryClient = useQueryClient();
  const [event, setEvent] = React.useState(initialEvent);
  const [formEvent, setFormEvent] = React.useState(initialEvent);
  const anchorEvent = React.useRef(initialEvent).current;
  const editorId = React.useId();
  const setEditors = React.useContext(BookingEditorsContext)?.setEditors;
  React.useEffect(() => {
    setEditors?.((current) => new Map(current).set(editorId, anchorEvent));
    return () =>
      setEditors?.((current) => {
        const next = new Map(current);
        next.delete(editorId);
        return next;
      });
  }, [editorId, anchorEvent, setEditors]);
  const configuration = useBookableItemConfiguration(event.target.globalId);
  const [formState, setFormState] = React.useState<BookingFormState | null>(null);
  const [windowAdjustment, setWindowAdjustment] = React.useState<BookingFormState["draft"]>();
  const updateOptions = getUpdateBookingMutationOptions(queryClient);
  const mutation = useMutation({
    mutationFn: (submission: BookingFormSubmission) => {
      const patch: BookingsUpdate = {
        ...(submission.window.start !== event.start ? { start: submission.window.start } : {}),
        ...(submission.window.end !== event.end ? { end: submission.window.end } : {}),
        ...(submission.purpose !== event.purpose ? { purpose: submission.purpose } : {}),
      };
      return Object.keys(patch).length === 0 ? Promise.resolve(event) : updateBooking(event.id, event.version, patch);
    },
    onSuccess: async (updated, _variables, onMutateResult, mutationContext) => {
      flushSync(onClose);
      if (updated) {
        await updateOptions.onSuccess?.(
          updated,
          { id: event.id, data: {}, headers: { "If-Match": `"${event.version}"` } },
          onMutateResult,
          mutationContext,
        );
      }
      await Promise.all([
        invalidateListBookings(queryClient, undefined, { cancelRefetch: false }),
        invalidateGetBooking(queryClient, event.id, undefined, { cancelRefetch: false }),
      ]);
    },
    onError: async (error) => {
      if (
        isBookingOutcomeUncertain(error) ||
        (error instanceof ApiV2ProblemError &&
          (error.status === 412 ||
            error.code === "errors.api.v2.booking.concurrentModification" ||
            error.code === "errors.api.v2.forbidden" ||
            error.code === "errors.api.v2.booking.state.transition"))
      ) {
        await Promise.all([invalidateListBookings(queryClient), invalidateGetBooking(queryClient, event.id)]);
      }
      if (error instanceof ApiV2ProblemError && error.code === "errors.api.v2.booking.target.unavailable") {
        await invalidateListBookingConfigurations(queryClient);
      }
    },
  });
  if (event !== initialEvent && !mutation.isPending) {
    setEvent(initialEvent);
    if (!isBookingOutcomeUncertain(mutation.error)) setFormEvent(initialEvent);
  }
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
  const intervalChanged = Boolean(
    formState?.window && (formState.window.start !== event.start || formState.window.end !== event.end),
  );
  const availabilityRow = configuration.data
    ? calendarAvailabilityRow({
        ...configuration.data,
        openingHours:
          event.kind === "MAINTENANCE"
            ? [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "00:00", end: "24:00" }))
            : configuration.data.openingHours,
        allowDoubleBooking: event.kind === "MAINTENANCE" ? false : configuration.data.allowDoubleBooking,
      })
    : undefined;
  const availabilityInterval = formState?.window
    ? {
        ...formState.window,
        date: formState.draft.startDate,
        timeZone: configuration.data?.timezone ?? timezone,
        elapsedMinutes: (Date.parse(formState.window.end) - Date.parse(formState.window.start)) / 60_000,
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
  return (
    <>
      {timelineDate && timelineEventElement ? (
        <TimelineWindowEditor
          anchor={timelineEventElement}
          verticalAnchor={timelineEventElement}
          date={timelineDate}
          timezone={timezone}
          draft={timelineDraft}
          policy={timelinePolicy(configuration.data, event.kind === "MAINTENANCE" ? "MAINTENANCE" : "BOOKING")}
          onChange={setWindowAdjustment}
          tone={event.kind === "MAINTENANCE" ? "maintenance" : "booking"}
        />
      ) : null}
      <div className="flex min-h-0 max-h-[min(22rem,45vh)] max-sm:max-h-[60dvh] flex-col border-border border-t">
        <BookingForm
          key={formEvent.version}
          mode="edit"
          density="compact"
          displayTimezone={timezone}
          booking={formEvent}
          configuration={configuration.data}
          pending={mutation.isPending}
          error={
            availabilityViolation
              ? t("bookings.errors.overlap")
              : mutation.error
                ? t(
                    isBookingOutcomeUncertain(mutation.error)
                      ? "bookings.errors.outcomeUncertain"
                      : bookingProblemKey(mutation.error),
                  )
                : undefined
          }
          submissionBlocked={checkingAvailability || availabilityViolation || isBookingOverlapError(mutation.error)}
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
