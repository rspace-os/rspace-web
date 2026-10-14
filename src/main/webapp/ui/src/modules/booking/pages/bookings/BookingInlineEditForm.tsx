import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "@tanstack/react-router";
import { CheckIcon, XIcon } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useBookableItemConfiguration } from "@/modules/booking/creation/BookableItemPicker";
import { BookingDayTimelineAside } from "@/modules/booking/creation/BookingDayTimelineAside";
import { BookingForm, type BookingFormState, type BookingFormSubmission } from "@/modules/booking/creation/BookingForm";
import { useBookingDraftAvailability } from "@/modules/booking/creation/useBookingDraftAvailability";
import { useBookingTimelineDraft } from "@/modules/booking/creation/useBookingTimelineDraft";
import {
  bookingProblemFeedback,
  isBookingConflictError,
  withBookingProblemConflict,
} from "@/modules/booking/creation/useCreateBooking";
import {
  ApiV2ProblemError,
  type BookingDetails,
  type BookingUpdate,
  updateBooking,
} from "@/modules/booking/domain/booking";
import { wallClockDraftFromInstants } from "@/modules/booking/domain/bookingTime";
import { Button } from "@/modules/common/ui/button";
import { Skeleton } from "@/modules/common/ui/skeleton";
import { Panel, useBookingEvent } from "./BookingEventPage";

function editable(booking: BookingDetails): booking is BookingDetails & {
  canEdit: true;
  state: "CONFIRMED";
  target: NonNullable<BookingDetails["target"]>;
} {
  return booking.canEdit && booking.state === "CONFIRMED" && booking.target !== null;
}

export default function BookingInlineEditForm() {
  const { t } = useTranslation(["booking", "common"]);
  const { t: bookingT } = useTranslation("booking");
  const { booking, token, displayTimeZone, formId, editButtonRef, announce, setDirty, refreshBooking } =
    useBookingEvent();
  const [base] = useState(booking);
  const [conflict, setConflict] = useState(false);
  const draftBridge = useBookingTimelineDraft();
  const navigate = useNavigate({ from: "/booking/calendar/bookings/$id/edit" });
  const queryClient = useQueryClient();
  const headingRef = useRef<HTMLHeadingElement>(null);
  const alertRef = useRef<HTMLParagraphElement>(null);
  const saveButtonRef = useRef<HTMLButtonElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  const configuration = useBookableItemConfiguration(editable(base) ? base.target.globalId : undefined, token);
  const toView = useCallback(async () => {
    await navigate({
      to: "/booking/calendar/bookings/$id",
      params: { id: String(base.id) },
      replace: true,
      resetScroll: false,
      ignoreBlocker: true,
    });
    requestAnimationFrame(() => editButtonRef.current?.focus());
  }, [base.id, editButtonRef, navigate]);
  const mutation = useMutation({
    mutationFn: async (submission: BookingFormSubmission) => {
      const patch: BookingUpdate = {
        ...(submission.window.start !== base.start ? { start: submission.window.start } : {}),
        ...(submission.window.end !== base.end ? { end: submission.window.end } : {}),
        ...(submission.purpose !== base.purpose ? { purpose: submission.purpose } : {}),
      };
      if (Object.keys(patch).length !== 0) await updateBooking(base.id, base.version, patch, token);
    },
    onSuccess: async () => {
      await refreshBooking();
      setDirty(false);
      announce(t("bookings.details.edit.saved"));
      await toView();
    },
    onError: async (error) => {
      if (error instanceof ApiV2ProblemError && error.status === 412) {
        setConflict(true);
        return;
      }
      if (
        error instanceof ApiV2ProblemError &&
        (error.code === "errors.api.v2.forbidden" || error.code === "errors.api.v2.booking.state.transition")
      ) {
        await refreshBooking();
      }
      if (error instanceof ApiV2ProblemError && error.code === "errors.api.v2.booking.target.unavailable") {
        await queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-configurations"] });
      }
    },
  });

  useEffect(() => {
    headingRef.current?.focus();
  }, []);
  useEffect(() => {
    if (!editable(booking)) void toView();
  }, [booking, toView]);
  useEffect(() => () => setDirty(false), [setDirty]);
  useEffect(() => {
    setDirty(Boolean(draftBridge.formState?.dirty || draftBridge.adjustmentPending || draftBridge.interactionActive));
  }, [draftBridge.adjustmentPending, draftBridge.formState?.dirty, draftBridge.interactionActive, setDirty]);
  useEffect(() => {
    if (conflict) {
      alertRef.current?.focus();
    } else if (mutation.isError && !mutation.isPending) {
      if (isBookingConflictError(mutation.error)) {
        const alert = panelRef.current?.querySelector<HTMLElement>("[role='alert']");
        alert?.setAttribute("tabindex", "-1");
        alert?.focus();
      } else saveButtonRef.current?.focus();
    }
  }, [conflict, mutation.error, mutation.isError, mutation.isPending]);

  const resetMutation = mutation.reset;
  const handleStateChange = useCallback(
    (state: BookingFormState) => {
      draftBridge.onStateChange(state);
      setDirty(state.dirty);
      if (mutation.isError && !conflict) {
        resetMutation();
      }
    },
    [draftBridge.onStateChange, setDirty, resetMutation, mutation.isError, conflict],
  );

  const originalDraft = wallClockDraftFromInstants(base.start, base.end, displayTimeZone);
  const availability = useBookingDraftAvailability({
    formState: draftBridge.formState,
    displayTimezone: displayTimeZone,
    token,
    eventKind: base.kind,
    originalWindow: { start: base.start, end: base.end },
    excludedBookingId: base.id,
  });
  const handleTimelineChange = useCallback(
    (draft: BookingFormState["draft"], targetGlobalId: string | undefined) => {
      draftBridge.onTimelineChange(draft, targetGlobalId);
    },
    [draftBridge.onTimelineChange],
  );

  if (!editable(booking) || !editable(base)) return null;

  const saveError = conflict ? undefined : mutation.error;
  const problem = bookingProblemFeedback(saveError, bookingT, {
    displayTimezone: displayTimeZone,
    target: configuration.data ?? undefined,
  });
  const blocked =
    conflict ||
    isBookingConflictError(mutation.error) ||
    draftBridge.adjustmentPending ||
    draftBridge.interactionActive ||
    availability.checking ||
    availability.blocksSubmission;

  return (
    <>
      <div ref={panelRef} className="@4xl:col-start-1 @4xl:row-span-2 @4xl:row-start-1">
        <Panel
          heading={t(
            base.kind === "MAINTENANCE" ? "bookings.details.edit.maintenanceTitle" : "bookings.details.edit.title",
          )}
          headingId="booking-details-heading"
          headingRef={headingRef}
          action={
            <span className="flex gap-3">
              <Button
                ref={saveButtonRef}
                type="submit"
                size="xs"
                form={formId}
                disabled={mutation.isPending || blocked || !configuration.isSuccess || !configuration.data}
                aria-busy={mutation.isPending}
              >
                <CheckIcon aria-hidden="true" />
                {t("bookings.form.save")}
              </Button>
              <Button
                type="button"
                size="xs"
                variant="ghost"
                disabled={mutation.isPending}
                onClick={() => {
                  mutation.reset();
                  setDirty(false);
                  void toView();
                }}
              >
                <XIcon aria-hidden="true" />
                {t("bookings.details.edit.discard")}
              </Button>
            </span>
          }
        >
          {configuration.isPending ? (
            <div aria-busy="true" className="space-y-4">
              <p role="status" className="sr-only">
                {t("bookings.loadingConfiguration")}
              </p>
              <div aria-hidden="true" className="space-y-4">
                {[0, 1, 2].map((row) => (
                  <Skeleton key={row} className="h-12 w-full" />
                ))}
                <Skeleton className="h-24 w-full" />
              </div>
            </div>
          ) : null}
          {configuration.isError || (!configuration.isPending && !configuration.data) ? (
            <div className="space-y-3">
              <p role="alert" className="text-destructive">
                {t("bookings.errors.targetUnavailable")}
              </p>
              <Button type="button" variant="outline" onClick={() => void configuration.refetch()}>
                {t("common:actions.retry")}
              </Button>
            </div>
          ) : null}
          {configuration.data ? (
            <BookingForm
              mode="edit"
              layout="inline"
              formId={formId}
              displayTimezone={displayTimeZone}
              booking={base}
              configuration={configuration.data}
              token={token}
              pending={mutation.isPending || conflict}
              error={
                problem.message ??
                (!saveError &&
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
              submissionBlocked={blocked}
              windowAdjustment={draftBridge.windowAdjustment}
              windowAdjustmentTarget={draftBridge.windowAdjustmentTarget}
              onWindowAdjustmentApplied={draftBridge.onWindowAdjustmentApplied}
              onDraftChange={draftBridge.onDraftChange}
              onStateChange={handleStateChange}
              onSubmit={(submission) => mutation.mutateAsync(submission)}
            />
          ) : null}
          {conflict ? (
            <p ref={alertRef} role="alert" tabIndex={-1} className="mt-4 text-sm text-destructive">
              {t("bookings.details.edit.conflict")}
            </p>
          ) : null}
        </Panel>
      </div>
      {configuration.data ? (
        <div className="@4xl:col-start-2 @4xl:row-start-2">
          <BookingDayTimelineAside
            target={configuration.data}
            draft={draftBridge.draft ?? originalDraft}
            timezone={displayTimeZone}
            token={token}
            disabledReason={
              mutation.isPending
                ? t("dayTimeline.vertical.saving")
                : conflict
                  ? t("bookings.details.edit.conflict")
                  : undefined
            }
            onInteractionChange={draftBridge.onInteractionChange}
            onChange={(draft) => handleTimelineChange(draft, configuration.data?.globalId)}
          />
        </div>
      ) : null}
    </>
  );
}
