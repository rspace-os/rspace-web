import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { BookingCalendarFileButton } from "@/modules/booking/components/BookingCalendarFileButton";
import { useBookableItemConfiguration } from "@/modules/booking/creation/BookableItemPicker";
import { BookingForm, type BookingFormSubmission, type EditableBooking } from "@/modules/booking/creation/BookingForm";
import { bookingProblemKey } from "@/modules/booking/creation/useCreateBooking";
import {
  ApiV2ProblemError,
  type BookingListDocument,
  type BookingUpdate,
  isBookingOverlapError,
  updateBooking,
} from "@/modules/booking/domain/booking";
import { formatAgendaPeriod } from "@/modules/booking/domain/bookingTime";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { Button, buttonVariants } from "@/modules/common/ui/button";
import { Skeleton } from "@/modules/common/ui/skeleton";
import { cn } from "@/modules/common/utils/cn";

const ACTION_COLUMNS = ["grid-cols-1", "grid-cols-1", "grid-cols-2", "grid-cols-3"];

export function isEditableBooking(event: BookingListDocument): event is BookingListDocument & EditableBooking {
  return event.privacy === "full" && event.canEdit && event.state === "CONFIRMED";
}

function InlineBookingEditor({
  event,
  timezone,
  token,
  onClose,
}: {
  event: BookingListDocument & EditableBooking;
  timezone: string;
  token: string;
  onClose: () => void;
}) {
  const { t } = useTranslation(["booking", "common"]);
  const queryClient = useQueryClient();
  const configuration = useBookableItemConfiguration(event.target.globalId, token);
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
  const clearMutationErrorOnChange = React.useCallback(() => {
    if (!mutation.isError) return;
    resetMutation();
  }, [mutation.isError, resetMutation]);

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
  return (
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
        error={mutation.error ? t(bookingProblemKey(mutation.error)) : undefined}
        submissionBlocked={isBookingOverlapError(mutation.error)}
        onStateChange={clearMutationErrorOnChange}
        onCancel={() => {
          mutation.reset();
          onClose();
        }}
        onSubmit={(submission) => mutation.mutateAsync(submission)}
      />
    </div>
  );
}

export function BookingActions({ event, timezone }: { event: BookingListDocument; timezone: string }) {
  const { t } = useTranslation("booking");
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const [editing, setEditing] = React.useState(false);
  const editable = isEditableBooking(event);
  if (editing && editable) {
    return <InlineBookingEditor event={event} timezone={timezone} token={token} onClose={() => setEditing(false)} />;
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
