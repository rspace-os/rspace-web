import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { BookingCalendarFileButton } from "@/modules/booking/components/BookingCalendarFileButton";
import { useBookableItemConfiguration } from "@/modules/booking/creation/BookableItemPicker";
import {
  BookingForm,
  type BookingFormState,
  type BookingFormSubmission,
  type EditableBooking,
} from "@/modules/booking/creation/BookingForm";
import { TimelineWindowEditor } from "@/modules/booking/creation/TimelineWindowEditor";
import { bookingProblemKey } from "@/modules/booking/creation/useCreateBooking";
import {
  ApiV2ProblemError,
  type BookingListDocument,
  type BookingUpdate,
  isBookingOverlapError,
  updateBooking,
} from "@/modules/booking/domain/booking";
import { formatAgendaPeriod, wallClockDraftFromInstants } from "@/modules/booking/domain/bookingTime";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { Button, buttonVariants } from "@/modules/common/ui/button";
import { Skeleton } from "@/modules/common/ui/skeleton";
import { cn } from "@/modules/common/utils/cn";
import { calendarAvailabilityRow, useCalendarAvailability } from "./calendarAvailability";

const ACTION_COLUMNS = ["grid-cols-1", "grid-cols-1", "grid-cols-2", "grid-cols-3"];

export function isEditableBooking(event: BookingListDocument): event is BookingListDocument & EditableBooking {
  return event.privacy === "full" && event.canEdit && event.state === "CONFIRMED";
}

function InlineBookingEditor({
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
  const intervalChanged = Boolean(
    formState?.window && (formState.window.start !== event.start || formState.window.end !== event.end),
  );
  const availabilityRow = configuration.data
    ? calendarAvailabilityRow({
        ...configuration.data,
        openingStart: event.kind === "MAINTENANCE" ? "00:00" : configuration.data.openingStart,
        openingEnd: event.kind === "MAINTENANCE" ? "24:00" : configuration.data.openingEnd,
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
    token,
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
            availabilityViolation
              ? t("bookings.errors.overlap")
              : mutation.error
                ? t(bookingProblemKey(mutation.error))
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

export function BookingActions({
  event,
  timezone,
  timelineDate,
}: {
  event: BookingListDocument;
  timezone: string;
  timelineDate?: string;
}) {
  const { t } = useTranslation("booking");
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const [editing, setEditing] = React.useState(false);
  const editable = isEditableBooking(event);
  if (editing && editable) {
    return (
      <InlineBookingEditor
        event={event}
        timezone={timezone}
        timelineDate={timelineDate}
        token={token}
        onClose={() => setEditing(false)}
      />
    );
  }
  // The same pair of conditions the download endpoint itself enforces, so the action never 404s.
  const canDownload = event.canViewConfiguration && event.state === "CONFIRMED";
  const canViewDetails = event.privacy === "full";
  const actionCount = (canViewDetails ? 1 : 0) + (editable ? 1 : 0) + (canDownload ? 1 : 0);
  if (actionCount === 0) return null;
  return (
    <div
      className={cn(
        "grid border-border border-t text-xs",
        ACTION_COLUMNS[actionCount],
        actionCount > 1 && "divide-x divide-border",
      )}
    >
      {canViewDetails ? (
        <Link
          className={cn(buttonVariants({ variant: "link", size: "xs" }), "h-auto rounded-none py-2")}
          to="/booking/calendar/bookings/$id"
          params={{ id: String(event.id) }}
        >
          {t("calendar.actions.viewDetails")}
        </Link>
      ) : null}
      {editable ? (
        <button
          type="button"
          className={cn(buttonVariants({ variant: "link", size: "xs" }), "h-auto rounded-none py-2")}
          onClick={() => setEditing(true)}
        >
          {t("calendar.actions.edit")}
        </button>
      ) : null}
      {canDownload ? (
        <BookingCalendarFileButton
          bookingId={event.id}
          itemName={event.target.value.name}
          period={formatAgendaPeriod(event.start, event.end, timezone)}
          token={token}
          size="xs"
          variant="link"
          className="h-auto rounded-none py-2"
        />
      ) : null}
    </div>
  );
}
