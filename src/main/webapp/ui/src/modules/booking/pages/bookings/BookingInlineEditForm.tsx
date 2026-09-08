import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "@tanstack/react-router";
import { CheckIcon, XIcon } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useBookableItemConfiguration } from "@/modules/booking/creation/BookableItemPicker";
import { BookingForm, type BookingFormState, type BookingFormSubmission } from "@/modules/booking/creation/BookingForm";
import {
  ApiV2ProblemError,
  type BookingDetails,
  type BookingUpdate,
  isBookingOverlapError,
  updateBooking,
} from "@/modules/booking/domain/booking";
import { Button } from "@/modules/common/ui/button";
import { Skeleton } from "@/modules/common/ui/skeleton";
import { Panel, useBookingEvent } from "./BookingEventPage";

function editable(booking: BookingDetails): booking is BookingDetails & { canEdit: true; state: "CONFIRMED" } {
  return booking.canEdit && booking.state === "CONFIRMED";
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

export default function BookingInlineEditForm() {
  const { t } = useTranslation(["booking", "common"]);
  const { booking, token, displayTimeZone, formId, editButtonRef, announce, setDirty, refreshBooking } =
    useBookingEvent();
  const [base] = useState(booking);
  const [conflict, setConflict] = useState(false);
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
    mutationFn: (submission: BookingFormSubmission) => {
      const patch: BookingUpdate = {
        ...(submission.window.start !== base.start ? { start: submission.window.start } : {}),
        ...(submission.window.end !== base.end ? { end: submission.window.end } : {}),
        ...(submission.purpose !== base.purpose ? { purpose: submission.purpose } : {}),
      };
      return Object.keys(patch).length === 0
        ? Promise.resolve(base)
        : updateBooking(base.id, base.version, patch, token);
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
    if (conflict) {
      alertRef.current?.focus();
    } else if (mutation.isError && !mutation.isPending) {
      if (isBookingOverlapError(mutation.error)) {
        const alert = panelRef.current?.querySelector<HTMLElement>("[role='alert']");
        alert?.setAttribute("tabindex", "-1");
        alert?.focus();
      } else saveButtonRef.current?.focus();
    }
  }, [conflict, mutation.error, mutation.isError, mutation.isPending]);

  const resetMutation = mutation.reset;
  const handleStateChange = useCallback(
    (state: BookingFormState) => {
      setDirty(state.dirty);
      if (mutation.isError && !conflict) {
        resetMutation();
      }
    },
    [setDirty, resetMutation, mutation.isError, conflict],
  );

  if (!editable(booking) || !editable(base)) return null;

  const blocked = conflict || isBookingOverlapError(mutation.error);

  return (
    <div ref={panelRef}>
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
            error={mutation.error && !conflict ? t(errorKey(mutation.error)) : undefined}
            submissionBlocked={blocked}
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
  );
}
