import { useLocation, useNavigate, useSearch } from "@tanstack/react-router";
import { ChevronDownIcon } from "lucide-react";
import { Suspense, useCallback, useId, useState } from "react";
import { useTranslation } from "react-i18next";
import { useBookableItem } from "@/modules/booking/creation/BookableItemPicker";
import { BookingForm, type BookingFormState, type BookingFormSubmission } from "@/modules/booking/creation/BookingForm";
import type { BookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import { bookingCreationDraftFromHistoryState } from "@/modules/booking/creation/bookingCreationDraft";
import { bookingProblemKey, useCreateBooking } from "@/modules/booking/creation/useCreateBooking";
import { isBookingOverlapError } from "@/modules/booking/domain/booking";
import { todayInTimeZone, useBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { Card, CardContent, CardHeader, CardTitle } from "@/modules/common/ui/card";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/modules/common/ui/collapsible";
import { Skeleton } from "@/modules/common/ui/skeleton";
import { Heading } from "@/modules/common/ui/typography";

function BookingRulesList({ item }: { item: BookableItemOption }) {
  const { t } = useTranslation("booking");
  const rules = [
    [t("bookableItemDetails.fields.openingHours"), `${item.openingStart}–${item.openingEnd}`],
    [t("bookableItemDetails.fields.timezone"), item.timezone],
    [
      t("bookableItemDetails.fields.granularity"),
      t("bookableItemDetails.minutes", { count: item.slotGranularityMinutes }),
    ],
    [
      t("bookableItemDetails.fields.maximumDuration"),
      item.maxBookingDurationMinutes === 0
        ? t("bookableItemDetails.unlimited")
        : t("bookableItemDetails.minutes", { count: item.maxBookingDurationMinutes }),
    ],
    [
      t("bookableItemDetails.fields.bufferBefore"),
      t("bookableItemDetails.minutes", { count: item.bufferBeforeMinutes }),
    ],
    [t("bookableItemDetails.fields.bufferAfter"), t("bookableItemDetails.minutes", { count: item.bufferAfterMinutes })],
    [
      t("bookableItemDetails.fields.doubleBooking"),
      item.allowDoubleBooking ? t("bookableItemDetails.yes") : t("bookableItemDetails.no"),
    ],
  ];

  return (
    <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
      {rules.map(([label, value]) => (
        <div className="contents" key={label}>
          <dt className="text-muted-foreground">{label}</dt>
          <dd className="min-w-0 break-words text-right font-medium">{value}</dd>
        </div>
      ))}
    </dl>
  );
}

function CollapsedBookingRules({ item }: { item: BookableItemOption }) {
  const { t } = useTranslation("booking");
  return (
    <Collapsible className="group/collapsible rounded-sm border bg-card px-4 shadow-sm">
      <CollapsibleTrigger className="flex min-h-12 w-full items-center justify-between text-left font-medium">
        {t("bookableItemDetails.rules")}
        <ChevronDownIcon
          aria-hidden="true"
          className="size-4 transition-transform group-data-open/collapsible:rotate-180"
        />
      </CollapsibleTrigger>
      <CollapsibleContent className="border-t py-4">
        <BookingRulesList item={item} />
      </CollapsibleContent>
    </Collapsible>
  );
}

function ExpandedBookingRules({ item }: { item: BookableItemOption }) {
  const { t } = useTranslation("booking");
  const headingId = useId();
  return (
    <aside aria-labelledby={headingId} className="min-w-0 @2xl:sticky @2xl:top-4 @2xl:self-start">
      <Card size="sm">
        <CardHeader>
          <CardTitle id={headingId}>{t("bookableItemDetails.rules")}</CardTitle>
        </CardHeader>
        <CardContent>
          <BookingRulesList item={item} />
        </CardContent>
      </Card>
    </aside>
  );
}

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
          <div className="grid gap-6 @2xl:grid-cols-[minmax(0,1fr)_20rem]">
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
      if (!mutation.isError) return;
      resetMutation();
    },
    [mutation.isError, resetMutation],
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
      <Heading level={2} as="h1">
        {t("bookings.addTitle")}
      </Heading>
      {search.target && !initialTarget.isPending && (initialTarget.isError || !initialTarget.data) && (
        <p role="alert">{t("bookings.errors.targetUnavailable")}</p>
      )}
      <div className="@container">
        <div className="grid gap-6 @2xl:grid-cols-[minmax(0,1fr)_20rem]">
          <div className="min-w-0 space-y-6">
            {selectedTarget ? (
              <div className="@2xl:hidden">
                <CollapsedBookingRules item={selectedTarget} />
              </div>
            ) : null}
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
              error={mutation.error ? t(bookingProblemKey(mutation.error)) : undefined}
              submissionBlocked={isBookingOverlapError(mutation.error)}
              showRulesSummary={false}
              onStateChange={updatePageState}
              onSubmit={submit}
            />
          </div>
          {selectedTarget ? (
            <div className="hidden @2xl:block">
              <ExpandedBookingRules item={selectedTarget} />
            </div>
          ) : null}
        </div>
      </div>
    </main>
  );
}
