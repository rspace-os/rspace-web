import { useLocation, useNavigate, useSearch } from "@tanstack/react-router";
import { useCallback, useRef } from "react";
import { useTranslation } from "react-i18next";
import { useBookableItem } from "@/modules/booking/creation/BookableItemPicker";
import { BookingDayTimelineAside } from "@/modules/booking/creation/BookingDayTimelineAside";
import { BookingForm, type BookingFormState, type BookingFormSubmission } from "@/modules/booking/creation/BookingForm";
import { BookingItemInformationCard } from "@/modules/booking/creation/BookingItemInformation";
import { bookingCreationDraftFromHistoryState } from "@/modules/booking/creation/bookingCreationDraft";
import { useBookingDraftAvailability } from "@/modules/booking/creation/useBookingDraftAvailability";
import { useBookingTimelineDraft } from "@/modules/booking/creation/useBookingTimelineDraft";
import {
  bookingProblemFeedback,
  isBookingConflictError,
  isBookingCreationOutcomeUncertain,
  useCreateBooking,
  withBookingProblemConflict,
} from "@/modules/booking/creation/useCreateBooking";
import { todayInTimeZone, useBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import { createBookingEventNotice, useBookingNotices } from "@/modules/booking/feedback/BookingNotices";
import { calendarCreatedEventFocusHref } from "@/modules/booking/pages/calendar/calendarEventFocus";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { Heading } from "@/modules/common/ui/typography";

export function AddBookingContent() {
  const { t } = useTranslation("booking");
  const search = useSearch({ from: "/booking/calendar/bookings/add" });
  const transferredDraft = useLocation({
    select: (location) => bookingCreationDraftFromHistoryState(location.state, search.target),
  });
  const searchStr = useLocation({ select: (location) => location.searchStr });
  const navigate = useNavigate({ from: "/booking/calendar/bookings/add" });
  const notices = useBookingNotices();
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const preferences = useBookingDisplayPreferences();
  const initialTarget = useBookableItem(search.target, token);
  const draftBridge = useBookingTimelineDraft();
  const formContainerRef = useRef<HTMLDivElement>(null);
  // Before the form reports its first state, use the asynchronously loaded query target. Afterwards the bridge owns it.
  const selectedTarget = draftBridge.formState ? draftBridge.formState.target : initialTarget.data;
  const initialDate = transferredDraft?.window.startDate ?? search.date ?? todayInTimeZone(preferences.timeZone);
  const initialWindow = transferredDraft?.window ?? {
    startDate: initialDate,
    startTime: "",
    endDate: initialDate,
    endTime: "",
  };
  const mutation = useCreateBooking(token);
  const resetMutation = mutation.reset;
  const updatePageState = useCallback(
    (state: BookingFormState) => {
      draftBridge.onStateChange(state);
      if (!mutation.isError || isBookingCreationOutcomeUncertain(mutation.error)) return;
      resetMutation();
    },
    [draftBridge.onStateChange, mutation.error, mutation.isError, resetMutation],
  );
  const updateTarget = useCallback(() => {
    draftBridge.onTargetChange();
  }, [draftBridge.onTargetChange]);
  const availability = useBookingDraftAvailability({
    formState: draftBridge.formState,
    displayTimezone: preferences.timeZone,
    token,
    eventKind: "BOOKING",
  });
  const problem = bookingProblemFeedback(mutation.error, t, {
    displayTimezone: preferences.timeZone,
    target: selectedTarget,
    creation: true,
  });
  const submit = async (submission: BookingFormSubmission) => {
    const created = await mutation.mutateAsync(submission);
    notices.notify(
      "calendar",
      createBookingEventNotice({
        event: created,
        message: t("bookings.feedback.eventAdded", {
          itemName: submission.target.name,
        }),
        timeZone: preferences.timeZone,
        searchStr,
      }),
    );
    const destinationSearch = { date: submission.returnDate, target: submission.target.globalId };
    const focusHref = calendarCreatedEventFocusHref({
      id: created.id,
      start: created.start,
      timeZone: preferences.timeZone,
      searchStr: new URLSearchParams(destinationSearch).toString(),
    });
    await navigate(
      focusHref
        ? { to: focusHref }
        : {
            to: "/booking/calendar",
            search: destinationSearch,
          },
    );
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
        <div className="grid gap-6 @4xl:grid-cols-[minmax(0,1fr)_30rem]">
          <div ref={formContainerRef} className="min-w-0 space-y-6">
            <BookingForm
              mode="add"
              displayTimezone={preferences.timeZone}
              eventKind="BOOKING"
              initialTarget={initialTarget.data}
              initialDate={initialDate}
              initialWindow={initialWindow}
              initialPurpose={transferredDraft?.purpose}
              token={token}
              pending={mutation.isPending}
              error={
                problem.message ??
                (!mutation.error &&
                !draftBridge.interactionActive &&
                availability.violation &&
                availability.conflicts.length === 0
                  ? t("bookings.errors.overlap")
                  : undefined)
              }
              warning={
                !draftBridge.interactionActive && availability.failed
                  ? t("bookings.warnings.availabilityUnknown")
                  : undefined
              }
              conflicts={
                draftBridge.interactionActive
                  ? []
                  : withBookingProblemConflict(availability.conflicts, problem.conflict)
              }
              conflictSeverity={problem.conflict ? "error" : availability.conflictSeverity}
              outcomeUncertain={isBookingCreationOutcomeUncertain(mutation.error)}
              submissionBlocked={
                draftBridge.adjustmentPending ||
                draftBridge.interactionActive ||
                availability.checking ||
                availability.blocksSubmission ||
                isBookingConflictError(mutation.error) ||
                isBookingCreationOutcomeUncertain(mutation.error)
              }
              showMobileItemInformation
              showRulesSummary={false}
              onStateChange={updatePageState}
              onDraftChange={draftBridge.onDraftChange}
              onTargetChange={updateTarget}
              windowAdjustment={draftBridge.windowAdjustment}
              windowAdjustmentTarget={draftBridge.windowAdjustmentTarget}
              onSubmit={submit}
            />
          </div>
          {selectedTarget ? (
            <div className="min-w-0 space-y-6">
              <div className="hidden @4xl:block">
                <BookingItemInformationCard
                  item={selectedTarget}
                  displayTimezone={preferences.timeZone}
                  date={(draftBridge.draft ?? initialWindow).startDate}
                />
              </div>
              <BookingDayTimelineAside
                formContainerRef={formContainerRef}
                target={selectedTarget}
                draft={draftBridge.draft ?? initialWindow}
                timezone={preferences.timeZone}
                token={token}
                disabledReason={mutation.isPending ? t("dayTimeline.vertical.saving") : undefined}
                onInteractionChange={draftBridge.onInteractionChange}
                onChange={(draft) => draftBridge.onTimelineChange(draft, selectedTarget.globalId)}
              />
            </div>
          ) : null}
        </div>
      </div>
    </main>
  );
}
