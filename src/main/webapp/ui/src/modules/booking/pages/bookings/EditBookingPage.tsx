import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate, useParams, useSearch } from "@tanstack/react-router";
import { useCallback, useRef } from "react";
import { useTranslation } from "react-i18next";
import { useBookableItemConfiguration } from "@/modules/booking/creation/BookableItemPicker";
import { BookingForm, type BookingFormSubmission, type EditableBooking } from "@/modules/booking/creation/BookingForm";
import {
  ApiV2ProblemError,
  type Booking,
  type BookingUpdate,
  fetchBooking,
  isBookingOverlapError,
  updateBooking,
} from "@/modules/booking/domain/booking";
import { todayInTimeZone, useBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import { currentWallClock, formatAgendaPeriod } from "@/modules/booking/domain/bookingTime";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { Button, buttonVariants } from "@/modules/common/ui/button";
import { Heading } from "@/modules/common/ui/typography";
import { DeleteBookingDialog } from "./DeleteBookingDialog";

function isEditableBooking(value: Booking): value is EditableBooking {
  return value.privacy === "full" && value.canEdit === true && value.state === "CONFIRMED";
}

function errorKey(
  error: unknown,
):
  | "bookings.errors.generic"
  | "bookings.errors.endAfterStart"
  | "bookings.errors.duration"
  | "bookings.errors.maximumDuration"
  | "bookings.errors.overlap"
  | "bookings.errors.granularity"
  | "bookings.errors.openingHours"
  | "bookings.errors.targetUnavailable"
  | "bookings.errors.forbidden"
  | "bookings.errors.noLongerEditable" {
  if (!(error instanceof ApiV2ProblemError)) return "bookings.errors.generic";
  if (error.code === "errors.api.v2.booking.window") return "bookings.errors.endAfterStart";
  if (error.code === "errors.api.v2.booking.duration") return "bookings.errors.duration";
  if (error.code === "errors.api.v2.booking.maximumDuration") return "bookings.errors.maximumDuration";
  if (error.code === "errors.api.v2.booking.overlap") return "bookings.errors.overlap";
  if (error.code === "errors.api.v2.booking.granularity") return "bookings.errors.granularity";
  if (error.code === "errors.api.v2.booking.openingHours") return "bookings.errors.openingHours";
  if (error.code === "errors.api.v2.booking.target.unavailable") return "bookings.errors.targetUnavailable";
  if (error.code === "errors.api.v2.forbidden") return "bookings.errors.forbidden";
  if (error.code === "errors.api.v2.booking.state.transition") return "bookings.errors.noLongerEditable";
  return "bookings.errors.generic";
}

export default function EditBookingPage() {
  const { t } = useTranslation(["booking", "common"]);
  const { id } = useParams({ from: "/booking/calendar/bookings/$id" });
  const search = useSearch({ from: "/booking/calendar/bookings/$id" });
  const navigate = useNavigate({ from: "/booking/calendar/bookings/$id" });
  const bookingId = Number(id);
  const validBookingId = Number.isSafeInteger(bookingId) && bookingId > 0;
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const queryClient = useQueryClient();
  const preferences = useBookingDisplayPreferences();
  const booking = useQuery({
    queryKey: ["api-v2", "bookings", bookingId],
    enabled: validBookingId && Boolean(token),
    queryFn: ({ signal }) => fetchBooking(bookingId, token, signal),
    retry: (failureCount, error) => {
      if (error instanceof ApiV2ProblemError && error.status >= 400 && error.status < 500) return false;
      return failureCount < 2;
    },
  });
  const configuration = useBookableItemConfiguration(booking.data?.target.globalId, token);
  const mutation = useMutation({
    mutationFn: (submission: BookingFormSubmission) => {
      if (!booking.data) throw new Error("Booking is not loaded");
      const patch: BookingUpdate = {
        ...(submission.window.start !== booking.data.start ? { start: submission.window.start } : {}),
        ...(submission.window.end !== booking.data.end ? { end: submission.window.end } : {}),
        ...(submission.purpose !== booking.data.purpose ? { purpose: submission.purpose } : {}),
      };
      return Object.keys(patch).length === 0 ? Promise.resolve(booking.data) : updateBooking(bookingId, patch, token);
    },
    onSuccess: async (updated, submission) => {
      await queryClient.invalidateQueries({ queryKey: ["api-v2", "bookings"] });
      await queryClient.invalidateQueries({ queryKey: ["api-v2", "bookings", bookingId] });
      await navigate({
        to: "/booking/calendar",
        search: {
          date: search.date ?? submission.returnDate,
          target: updated.target.globalId,
        },
      });
    },
    onError: async (error) => {
      if (
        error instanceof ApiV2ProblemError &&
        (error.code === "errors.api.v2.forbidden" || error.code === "errors.api.v2.booking.state.transition")
      ) {
        await queryClient.invalidateQueries({ queryKey: ["api-v2", "bookings", bookingId] });
      }
      if (error instanceof ApiV2ProblemError && error.code === "errors.api.v2.booking.target.unavailable") {
        await queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-configurations"] });
      }
    },
  });
  const mutationHasError = useRef(false);
  mutationHasError.current = mutation.isError;
  const resetMutation = mutation.reset;
  const clearMutationErrorOnChange = useCallback(() => {
    if (!mutationHasError.current) return;
    mutationHasError.current = false;
    resetMutation();
  }, [resetMutation]);
  const returnLink = (
    returnDate = search.date ?? todayInTimeZone(preferences.timeZone),
    returnTarget = search.target,
  ) => (
    <Link
      className={buttonVariants({ variant: "outline" })}
      to="/booking/calendar"
      search={{ date: returnDate, target: returnTarget }}
    >
      {t("bookings.form.returnToCalendar")}
    </Link>
  );
  if (!validBookingId)
    return (
      <main className="space-y-4 p-8">
        <p role="alert">{t("bookings.errors.notFound")}</p>
        {returnLink()}
      </main>
    );
  if (booking.isPending)
    return (
      <main className="p-8">
        <p>{t("bookings.loading")}</p>
      </main>
    );
  if (booking.isError || !booking.data)
    return (
      <main className="space-y-4 p-8">
        <p role="alert">
          {booking.error instanceof ApiV2ProblemError && booking.error.status >= 400 && booking.error.status < 500
            ? t("bookings.errors.notFound")
            : t("bookings.errors.load")}
        </p>
        {!(booking.error instanceof ApiV2ProblemError) || booking.error.status >= 500 ? (
          <Button type="button" variant="outline" onClick={() => void booking.refetch()}>
            {t("common:actions.retry")}
          </Button>
        ) : null}
        {returnLink()}
      </main>
    );
  if (booking.data.privacy !== "full" || !booking.data.canEdit) {
    return (
      <main className="space-y-4 p-8">
        <p role="alert">{t("bookings.errors.forbidden")}</p>
        {returnLink(
          search.date ?? currentWallClock(booking.data.start, preferences.timeZone).date,
          booking.data.target.globalId,
        )}
      </main>
    );
  }
  if (booking.data.state !== "CONFIRMED") {
    return (
      <main className="space-y-4 p-8">
        <p role="alert">{t("bookings.errors.noLongerEditable")}</p>
        {returnLink(
          search.date ?? currentWallClock(booking.data.start, preferences.timeZone).date,
          booking.data.target.globalId,
        )}
      </main>
    );
  }
  if (!isEditableBooking(booking.data)) return null;
  if (configuration.isPending)
    return (
      <main className="p-8">
        <p>{t("bookings.loadingConfiguration")}</p>
      </main>
    );
  if (configuration.isError || !configuration.data)
    return (
      <main className="space-y-4 p-8">
        <p role="alert">{t("bookings.errors.targetUnavailable")}</p>
        <Button type="button" variant="outline" onClick={() => void configuration.refetch()}>
          {t("common:actions.retry")}
        </Button>
        {returnLink(
          search.date ?? currentWallClock(booking.data.start, preferences.timeZone).date,
          booking.data.target.globalId,
        )}
      </main>
    );
  const returnDate = search.date ?? currentWallClock(booking.data.start, preferences.timeZone).date;
  return (
    <main className="space-y-6 p-4 sm:p-8">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Heading level={2} as="h1">
          {t("bookings.editTitle")}
        </Heading>
        <DeleteBookingDialog
          bookingId={booking.data.id}
          itemName={booking.data.target.value.name}
          period={formatAgendaPeriod(booking.data.start, booking.data.end, preferences.timeZone)}
          token={token}
          disabled={mutation.isPending}
          onDeleted={() =>
            navigate({
              to: "/booking/calendar",
              search: { date: returnDate, target: booking.data.target.globalId },
            })
          }
        />
      </div>
      <BookingForm
        mode="edit"
        displayTimezone={preferences.timeZone}
        booking={booking.data}
        configuration={configuration.data}
        token={token}
        pending={mutation.isPending}
        error={mutation.error ? t(errorKey(mutation.error)) : undefined}
        submissionBlocked={isBookingOverlapError(mutation.error)}
        onStateChange={clearMutationErrorOnChange}
        onSubmit={(submission) => mutation.mutateAsync(submission)}
      />
    </main>
  );
}
