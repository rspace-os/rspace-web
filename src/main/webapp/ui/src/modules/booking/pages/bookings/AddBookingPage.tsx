import { useNavigate, useSearch } from "@tanstack/react-router";
import { useCallback, useRef } from "react";
import { useTranslation } from "react-i18next";
import { useBookableItem } from "@/modules/booking/creation/BookableItemPicker";
import { BookingForm, type BookingFormSubmission } from "@/modules/booking/creation/BookingForm";
import { bookingProblemKey, useCreateBooking } from "@/modules/booking/creation/useCreateBooking";
import { isBookingOverlapError } from "@/modules/booking/domain/booking";
import { todayInTimeZone, useBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { Heading } from "@/modules/common/ui/typography";

export default function AddBookingPage() {
  const { t } = useTranslation("booking");
  const search = useSearch({ from: "/booking/calendar/bookings/add" });
  const navigate = useNavigate({ from: "/booking/calendar/bookings/add" });
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const preferences = useBookingDisplayPreferences();
  const initialTarget = useBookableItem(search.target, token);
  const mutation = useCreateBooking(token);
  const mutationHasError = useRef(false);
  mutationHasError.current = mutation.isError;
  const resetMutation = mutation.reset;
  const clearMutationErrorOnChange = useCallback(() => {
    if (!mutationHasError.current) return;
    mutationHasError.current = false;
    resetMutation();
  }, [resetMutation]);
  const submit = async (submission: BookingFormSubmission) => {
    await mutation.mutateAsync(submission);
    await navigate({
      to: "/booking/calendar",
      search: { date: search.date ?? submission.returnDate, target: submission.target.globalId },
    });
  };
  return (
    <main className="space-y-6 p-4 sm:p-8">
      <Heading level={2} as="h1">
        {t("bookings.addTitle")}
      </Heading>
      {search.target && !initialTarget.isPending && (initialTarget.isError || !initialTarget.data) && (
        <p role="alert">{t("bookings.errors.targetUnavailable")}</p>
      )}
      <BookingForm
        mode="add"
        displayTimezone={preferences.timeZone}
        eventKind="BOOKING"
        initialTarget={initialTarget.data}
        initialDate={search.date ?? todayInTimeZone(preferences.timeZone)}
        token={token}
        pending={mutation.isPending}
        error={mutation.error ? t(bookingProblemKey(mutation.error)) : undefined}
        submissionBlocked={isBookingOverlapError(mutation.error)}
        onStateChange={clearMutationErrorOnChange}
        onSubmit={submit}
      />
    </main>
  );
}
