import { Form, useField, useForm } from "@formisch/react";
import { Link } from "@tanstack/react-router";
import { useCallback, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import * as v from "valibot";
import { type BookableItemOption, BookableItemPicker } from "@/modules/booking/components/BookableItemPicker";
import type { Booking } from "@/modules/booking/domain/booking";
import {
  type BookingWindowDraft,
  currentWallClock,
  wallClockDraftFromInstants,
} from "@/modules/booking/domain/bookingTime";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import { RenderFields } from "@/modules/common/collection-form/RenderFields";
import { Button, buttonVariants } from "@/modules/common/ui/button";
import { FieldError } from "@/modules/common/ui/field";
import { InventoryItem } from "@/modules/common/ui/inventory-item";
import { type ResolvedBookingWindow, ZonedBookingWindowFields } from "./ZonedBookingWindowFields";

type PurposeInput = { purpose: string };

const purposeFields = resolveCollectionConfig<PurposeInput>({
  slug: "booking-purpose",
  idField: "purpose",
  labels: { singularKey: "booking:bookings.form.purpose", pluralKey: "booking:bookings.form.purpose" },
  useAsTitle: "purpose",
  defaultColumns: ["purpose"],
  fields: [
    {
      name: "purpose",
      type: "text",
      labelKey: "booking:bookings.form.purpose",
      maximumLength: 1000,
      form: { widget: "textarea" },
    },
  ],
}).fields;

export type EditableBooking = Extract<Booking, { privacy: "full" }> & {
  canEdit: true;
  state: "CONFIRMED";
};

export type BookingFormSubmission = {
  target: BookableItemOption;
  window: ResolvedBookingWindow;
  purpose: string | null;
  returnDate: string;
};

type BookingFormProps =
  | {
      mode: "add";
      initialTarget?: BookableItemOption;
      initialDate?: string;
      token: string;
      pending: boolean;
      error?: string;
      onSubmit: (submission: BookingFormSubmission) => Promise<unknown>;
    }
  | {
      mode: "edit";
      booking: EditableBooking;
      configuration: BookableItemOption;
      token: string;
      pending: boolean;
      error?: string;
      onSubmit: (submission: BookingFormSubmission) => Promise<unknown>;
    };

function emptyDraft(date = ""): BookingWindowDraft {
  return { startDate: date, startTime: "", endDate: date, endTime: "" };
}

function sameWindowDraft(left: BookingWindowDraft, right: BookingWindowDraft): boolean {
  return (
    left.startDate === right.startDate &&
    left.startTime === right.startTime &&
    left.startOccurrence === right.startOccurrence &&
    left.endDate === right.endDate &&
    left.endTime === right.endTime &&
    left.endOccurrence === right.endOccurrence
  );
}

export function BookingForm(props: BookingFormProps) {
  const { t } = useTranslation("booking");
  const editing = props.mode === "edit";
  const fixedTarget = props.mode === "edit" ? props.configuration : undefined;
  const initialTarget = props.mode === "add" ? props.initialTarget : undefined;
  const initialDate = props.mode === "add" ? props.initialDate : undefined;
  const originalDraft =
    props.mode === "edit"
      ? wallClockDraftFromInstants(props.booking.start, props.booking.end, props.booking.timezone)
      : undefined;
  const [target, setTarget] = useState<BookableItemOption | undefined>(editing ? fixedTarget : initialTarget);
  const [draft, setDraft] = useState<BookingWindowDraft>(() =>
    originalDraft
      ? originalDraft
      : emptyDraft(
          initialDate ??
            (initialTarget ? currentWallClock(new Date().toISOString(), initialTarget.timezone).date : undefined),
        ),
  );
  const [window, setWindow] = useState<ResolvedBookingWindow>();
  const [attempted, setAttempted] = useState(false);
  const form = useForm({
    schema: v.object({ purpose: v.pipe(v.string(), v.maxLength(1000)) }),
    initialInput: { purpose: editing ? (props.booking.purpose ?? "") : "" },
  });
  const purpose = useField(form, { path: ["purpose"] }).input;
  const [submitting, setSubmitting] = useState(false);
  const submittingRef = useRef(false);
  const busy = props.pending || submitting;
  useEffect(() => {
    if (!editing && !target && initialTarget) {
      setTarget(initialTarget);
      setDraft((current) => {
        if (current.startDate || initialDate) return current;
        const date = currentWallClock(new Date().toISOString(), initialTarget.timezone).date;
        return { ...current, startDate: date, endDate: date };
      });
    }
  }, [editing, initialDate, initialTarget, target]);
  const resolved = useCallback((value: ResolvedBookingWindow | undefined) => setWindow(value), []);
  const selectTarget = (next: BookableItemOption | undefined) => {
    setTarget(next);
    setDraft((current) => ({
      ...current,
      ...(next && !current.startDate
        ? {
            startDate: currentWallClock(new Date().toISOString(), next.timezone).date,
            endDate: currentWallClock(new Date().toISOString(), next.timezone).date,
          }
        : {}),
      startOccurrence: undefined,
      endOccurrence: undefined,
    }));
  };
  const submit = async (input: PurposeInput) => {
    setAttempted(true);
    if (!target || !window || submittingRef.current) return;
    submittingRef.current = true;
    setSubmitting(true);
    try {
      await props.onSubmit({
        target,
        window,
        purpose: input.purpose.trim() || null,
        returnDate: draft.startDate,
      });
    } finally {
      submittingRef.current = false;
      setSubmitting(false);
    }
  };

  return (
    <Form of={form} className="max-w-2xl space-y-8" aria-busy={busy} onSubmit={submit}>
      {editing ? (
        <div className="space-y-2">
          <p className="text-sm font-medium">{t("bookings.form.item")}</p>
          <InventoryItem
            name={fixedTarget?.name ?? ""}
            globalId={fixedTarget?.globalId ?? ""}
            href={`/globalId/${fixedTarget?.globalId ?? ""}`}
            idLinkLabel={t("bookings.form.openItem", { globalId: fixedTarget?.globalId ?? "" })}
            compact
          />
        </div>
      ) : (
        <BookableItemPicker value={target} onChange={selectTarget} token={props.token} disabled={busy} />
      )}
      {attempted && !target && <FieldError>{t("bookings.errors.itemRequired")}</FieldError>}
      {target && (
        <>
          <p className="text-sm text-muted-foreground">
            {t("bookings.form.openingHours", { start: target.openingStart, end: target.openingEnd })}
          </p>
          {target.maxBookingDurationMinutes > 0 ? (
            <p className="text-sm text-muted-foreground">
              {t("bookings.form.maximumDuration", {
                count: target.maxBookingDurationMinutes,
              })}
            </p>
          ) : null}
          <ZonedBookingWindowFields
            timezone={target.timezone}
            slotGranularityMinutes={target.slotGranularityMinutes}
            maxBookingDurationMinutes={target.maxBookingDurationMinutes}
            openingStart={target.openingStart}
            openingEnd={target.openingEnd}
            value={draft}
            onChange={setDraft}
            onResolved={resolved}
            allowPolicyMismatch={Boolean(originalDraft && sameWindowDraft(draft, originalDraft))}
            disabled={busy}
          />
        </>
      )}
      {attempted && target && !window && <FieldError>{t("bookings.errors.windowRequired")}</FieldError>}
      {props.error && <FieldError>{props.error}</FieldError>}
      <RenderFields fields={purposeFields} form={form} disabled={busy} />
      <p className="-mt-6 text-right text-xs text-muted-foreground" aria-live="polite">
        {t("bookings.form.purposeCount", { count: typeof purpose === "string" ? purpose.length : 0 })}
      </p>
      <div className="flex gap-3">
        <Button type="submit" disabled={busy} aria-busy={busy}>
          {editing ? t("bookings.form.save") : t("bookings.form.submit")}
        </Button>
        <Link
          className={buttonVariants({ variant: "outline", className: busy ? "pointer-events-none opacity-50" : "" })}
          to="/booking/calendar"
          search={{ date: draft.startDate, target: target?.globalId }}
          aria-disabled={busy}
          tabIndex={busy ? -1 : undefined}
        >
          {t("bookings.form.cancel")}
        </Link>
      </div>
    </Form>
  );
}
