import { useLocation, useNavigate, useSearch } from "@tanstack/react-router";
import { Suspense, useCallback, useState } from "react";
import { useTranslation } from "react-i18next";
import { useBookableItem } from "@/modules/booking/creation/BookableItemPicker";
import { BookingForm, type BookingFormState, type BookingFormSubmission } from "@/modules/booking/creation/BookingForm";
import { BookingItemInformationCard } from "@/modules/booking/creation/BookingItemInformation";
import type { BookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import { bookingCreationDraftFromHistoryState } from "@/modules/booking/creation/bookingCreationDraft";
import {
  bookingCreationProblemKey,
  isBookingCreationOutcomeUncertain,
  useCreateBooking,
} from "@/modules/booking/creation/useCreateBooking";
import { isBookingOverlapError } from "@/modules/booking/domain/booking";
import { todayInTimeZone, useBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { Skeleton } from "@/modules/common/ui/skeleton";
import { Heading } from "@/modules/common/ui/typography";

export default function AddBookingPage() {
  return (
    <Suspense fallback={<AddBookingSkeleton />}>
      <AddBookingContent />
    </Suspense>
  );
}

function AddBookingSkeleton() {
  const { t } = useTranslation("common");
  return (
    <main className="space-y-6 p-4 sm:p-8" aria-busy="true">
      <p role="status" className="sr-only">
        {t("loading")}
      </p>
      <div aria-hidden="true" className="space-y-6">
        <Skeleton className="h-9 w-56" />
        <div className="@container">
          <div className="grid gap-6 @2xl:grid-cols-[minmax(0,1fr)_24rem]">
            <div className="min-w-0 space-y-6">
              {[0, 1, 2].map((row) => (
                <Skeleton key={row} className="h-16 w-full" />
              ))}
              <Skeleton className="h-24 w-full" />
            </div>
          </div>
        </div>
      </div>
    </main>
  );
}

function AddBookingContent() {
  const { t } = useTranslation("booking");
  const search = useSearch({ from: "/booking/calendar/bookings/add" });
  const transferredDraft = useLocation({
    select: (location) => bookingCreationDraftFromHistoryState(location.state, search.target),
  });
  const navigate = useNavigate({ from: "/booking/calendar/bookings/add" });
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const preferences = useBookingDisplayPreferences();
  const initialTarget = useBookableItem(search.target, token);
  const [selectedTarget, setSelectedTarget] = useState<BookableItemOption>();
  const mutation = useCreateBooking(token);
  const resetMutation = mutation.reset;
  const updatePageState = useCallback(
    (state: BookingFormState) => {
      setSelectedTarget(state.target);
      if (!mutation.isError || isBookingCreationOutcomeUncertain(mutation.error)) return;
      resetMutation();
    },
    [mutation.error, mutation.isError, resetMutation],
  );
  const submit = async (submission: BookingFormSubmission) => {
    await mutation.mutateAsync(submission);
    await navigate({
      to: "/booking/calendar",
      search: { date: submission.returnDate, target: submission.target.globalId },
    });
  };
  return (
    <main className="space-y-6 p-4 sm:p-8">
      <Heading level={3} as="h1">
        {t("bookings.addTitle")}
      </Heading>
      {search.target && !initialTarget.isPending && (initialTarget.isError || !initialTarget.data) && (
        <p role="alert">{t("bookings.errors.targetUnavailable")}</p>
      )}
      <div className="@container">
        <div className="grid gap-6 @2xl:grid-cols-[minmax(0,1fr)_24rem]">
          <div className="min-w-0 space-y-6">
            <BookingForm
              mode="add"
              displayTimezone={preferences.timeZone}
              eventKind="BOOKING"
              initialTarget={initialTarget.data}
              initialDate={transferredDraft?.window.startDate ?? search.date ?? todayInTimeZone(preferences.timeZone)}
              initialWindow={transferredDraft?.window}
              initialPurpose={transferredDraft?.purpose}
              token={token}
              pending={mutation.isPending}
              error={mutation.error ? t(bookingCreationProblemKey(mutation.error)) : undefined}
              outcomeUncertain={isBookingCreationOutcomeUncertain(mutation.error)}
              submissionBlocked={
                isBookingOverlapError(mutation.error) || isBookingCreationOutcomeUncertain(mutation.error)
              }
              showMobileItemInformation
              showRulesSummary={false}
              onStateChange={updatePageState}
              onSubmit={submit}
            />
          </div>
          {selectedTarget ? (
            <div className="hidden @2xl:block">
              <BookingItemInformationCard item={selectedTarget} displayTimezone={preferences.timeZone} />
            </div>
          ) : null}
        </div>
      </div>
    </main>
  );
}
