import { useLocation, useNavigate, useSearch } from "@tanstack/react-router";
import { useCallback, useState } from "react";
import { useTranslation } from "react-i18next";
import { useBookableItem } from "@/modules/booking/creation/BookableItemPicker";
import { BookingDayTimelineAside } from "@/modules/booking/creation/BookingDayTimelineAside";
import { BookingForm, type BookingFormState, type BookingFormSubmission } from "@/modules/booking/creation/BookingForm";
import { BookingItemInformationCard } from "@/modules/booking/creation/BookingItemInformation";
import type { BookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import { bookingCreationDraftFromHistoryState } from "@/modules/booking/creation/bookingCreationDraft";
import { useBookingDraftAvailability } from "@/modules/booking/creation/useBookingDraftAvailability";
import { useBookingTimelineDraft } from "@/modules/booking/creation/useBookingTimelineDraft";
import {
  bookingCreationProblemKey,
  isBookingCreationOutcomeUncertain,
  useCreateBooking,
} from "@/modules/booking/creation/useCreateBooking";
import { isBookingOverlapError } from "@/modules/booking/domain/booking";
import { todayInTimeZone, useBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { Heading } from "@/modules/common/ui/typography";

export function AddBookingContent() {
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
  const draftBridge = useBookingTimelineDraft();
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
      setSelectedTarget(state.target);
      if (!mutation.isError || isBookingCreationOutcomeUncertain(mutation.error)) return;
      resetMutation();
    },
    [draftBridge.onStateChange, mutation.error, mutation.isError, resetMutation],
  );
  const updateTarget = useCallback(
    (target: BookableItemOption | undefined) => {
      draftBridge.onTargetChange();
      setSelectedTarget(target);
    },
    [draftBridge.onTargetChange],
  );
  const availability = useBookingDraftAvailability({
    formState: draftBridge.formState,
    displayTimezone: preferences.timeZone,
    token,
    eventKind: "BOOKING",
  });
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
        <div className="grid gap-6 @4xl:grid-cols-[minmax(0,1fr)_30rem]">
          <div className="min-w-0 space-y-6">
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
                mutation.error
                  ? t(bookingCreationProblemKey(mutation.error))
                  : !draftBridge.interactionActive && availability.violation && availability.conflicts.length === 0
                    ? t("bookings.errors.overlap")
                    : undefined
              }
              warning={
                !draftBridge.interactionActive && availability.failed
                  ? t("bookings.warnings.availabilityUnknown")
                  : undefined
              }
              conflicts={draftBridge.interactionActive ? [] : availability.conflicts}
              conflictSeverity={availability.conflictSeverity}
              outcomeUncertain={isBookingCreationOutcomeUncertain(mutation.error)}
              submissionBlocked={
                draftBridge.adjustmentPending ||
                draftBridge.interactionActive ||
                availability.checking ||
                availability.blocksSubmission ||
                isBookingOverlapError(mutation.error) ||
                isBookingCreationOutcomeUncertain(mutation.error)
              }
              showMobileItemInformation
              showRulesSummary={false}
              onStateChange={updatePageState}
              onWindowAdjustmentApplied={draftBridge.onWindowAdjustmentApplied}
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
                <BookingItemInformationCard item={selectedTarget} displayTimezone={preferences.timeZone} />
              </div>
              <BookingDayTimelineAside
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
