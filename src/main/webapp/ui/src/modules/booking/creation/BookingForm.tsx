import { Form, useField, useForm } from "@formisch/react";
import { Link } from "@tanstack/react-router";
import { CheckIcon } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import * as v from "valibot";
import { BookableItemPicker } from "@/modules/booking/creation/BookableItemPicker";
import type { BookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import type { Booking, BookingEventKind } from "@/modules/booking/domain/booking";
import {
  type BookingWindowDraft,
  currentWallClock,
  wallClockDraftFromInstants,
} from "@/modules/booking/domain/bookingTime";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import { RenderFields } from "@/modules/common/collection-form/RenderFields";
import { ActionBar } from "@/modules/common/ui/action-bar";
import { Button, buttonVariants } from "@/modules/common/ui/button";
import { FieldError } from "@/modules/common/ui/field";
import { InventoryItem } from "@/modules/common/ui/inventory-item";
import { cn } from "@/modules/common/utils/cn";
import { type ResolvedBookingWindow, ZonedBookingWindowFields } from "./ZonedBookingWindowFields";

type PurposeInput = { purpose: string };

function purposeFields(eventKind: BookingEventKind) {
  const labelKey = eventKind === "MAINTENANCE" ? "booking:bookings.form.notes" : "booking:bookings.form.purpose";
  return resolveCollectionConfig<PurposeInput>({
    slug: "booking-purpose",
    idField: "purpose",
    labels: { singularKey: labelKey, pluralKey: labelKey },
    useAsTitle: "purpose",
    defaultColumns: ["purpose"],
    fields: [
      {
        name: "purpose",
        type: "text",
        labelKey,
        maximumLength: 1000,
        form: { widget: "textarea" },
      },
    ],
  }).fields;
}

export type EditableBooking = Extract<Booking, { privacy: "full" }> & {
  canEdit: true;
  state: "CONFIRMED";
};

export type BookingFormSubmission = {
  target: BookableItemOption;
  window: ResolvedBookingWindow;
  purpose: string | null;
  eventKind: BookingEventKind;
  returnDate: string;
};

export type BookingFormState = {
  target: BookableItemOption | undefined;
  draft: BookingWindowDraft;
  window: ResolvedBookingWindow | undefined;
  purpose: string;
  eventKind: BookingEventKind;
  dirty: boolean;
};

type BookingFormCommonProps = {
  displayTimezone?: string;
  token: string;
  pending: boolean;
  error?: string;
  submissionBlocked?: boolean;
  density?: "comfortable" | "compact";
  onCancel?: () => void;
  onMoreOptions?: () => void;
  onStateChange?: (state: BookingFormState) => void;
  onSubmit: (submission: BookingFormSubmission) => Promise<unknown>;
};

type BookingFormProps = BookingFormCommonProps &
  (
    | {
        mode: "add";
        initialTarget?: BookableItemOption;
        initialDate?: string;
        initialWindow?: BookingWindowDraft;
        eventKind: BookingEventKind;
        lockTarget?: boolean;
      }
    | {
        mode: "edit";
        booking: EditableBooking;
        configuration: BookableItemOption;
      }
  );

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
  const fixedTarget = props.mode === "edit" ? props.configuration : props.lockTarget ? props.initialTarget : undefined;
  const initialTarget = props.mode === "add" ? props.initialTarget : undefined;
  const initialDate = props.mode === "add" ? props.initialDate : undefined;
  const initialWindow = props.mode === "add" ? props.initialWindow : undefined;
  const eventKind = props.mode === "add" ? props.eventKind : props.booking.kind;
  const textFields = useMemo(() => purposeFields(eventKind), [eventKind]);
  const displayTimezone = props.displayTimezone ?? (editing ? fixedTarget?.timezone : initialTarget?.timezone) ?? "UTC";
  const originalDraft =
    props.mode === "edit"
      ? wallClockDraftFromInstants(props.booking.start, props.booking.end, displayTimezone)
      : undefined;
  const addDraft =
    initialWindow ??
    emptyDraft(
      initialDate ?? (initialTarget ? currentWallClock(new Date().toISOString(), displayTimezone).date : undefined),
    );
  const [target, setTarget] = useState<BookableItemOption | undefined>(editing ? fixedTarget : initialTarget);
  const [draft, setDraft] = useState<BookingWindowDraft>(() => originalDraft ?? addDraft);
  const [window, setWindow] = useState<ResolvedBookingWindow>();
  const [attempted, setAttempted] = useState(false);
  const form = useForm({
    schema: v.object({ purpose: v.pipe(v.string(), v.maxLength(1000)) }),
    initialInput: { purpose: editing ? (props.booking.purpose ?? "") : "" },
  });
  const purpose = useField(form, { path: ["purpose"] }).input;
  const purposeValue = typeof purpose === "string" ? purpose : "";
  const [submitting, setSubmitting] = useState(false);
  const submittingRef = useRef(false);
  const initialState = useRef({
    targetGlobalId: (editing ? fixedTarget : initialTarget)?.globalId ?? "",
    draft: originalDraft ?? addDraft,
    purpose: editing ? (props.booking.purpose ?? "") : "",
    eventKind,
  });
  const busy = props.pending || submitting;
  useEffect(() => {
    if (!editing && !target && initialTarget) {
      setTarget(initialTarget);
      setDraft((current) => {
        if (current.startDate || initialDate) return current;
        const date = currentWallClock(new Date().toISOString(), displayTimezone).date;
        const next = { ...current, startDate: date, endDate: date };
        initialState.current = { ...initialState.current, targetGlobalId: initialTarget.globalId, draft: next };
        return next;
      });
      initialState.current = { ...initialState.current, targetGlobalId: initialTarget.globalId };
    }
  }, [displayTimezone, editing, initialDate, initialTarget, target]);
  const resolved = useCallback((value: ResolvedBookingWindow | undefined) => setWindow(value), []);
  const selectTarget = (next: BookableItemOption | undefined) => {
    setTarget(next);
    setDraft((current) => ({
      ...current,
      ...(next && !current.startDate
        ? {
            startDate: currentWallClock(new Date().toISOString(), displayTimezone).date,
            endDate: currentWallClock(new Date().toISOString(), displayTimezone).date,
          }
        : {}),
      startOccurrence: undefined,
      endOccurrence: undefined,
    }));
  };
  const submit = async (input: PurposeInput) => {
    setAttempted(true);
    if (!target || !window || submittingRef.current) {
      requestAnimationFrame(() => {
        const formElement = document.querySelector<HTMLElement>("form[aria-busy]");
        formElement?.querySelector<HTMLElement>("[aria-invalid='true'], #booking-item-search")?.focus();
      });
      return;
    }
    submittingRef.current = true;
    setSubmitting(true);
    try {
      await props.onSubmit({
        target,
        window,
        purpose: input.purpose.trim() || null,
        eventKind,
        returnDate: draft.startDate,
      });
    } catch {
      // The owning page exposes mutation failures through the error prop while this form keeps its draft.
    } finally {
      submittingRef.current = false;
      setSubmitting(false);
    }
  };
  const dirty =
    (target?.globalId ?? "") !== initialState.current.targetGlobalId ||
    !sameWindowDraft(draft, initialState.current.draft) ||
    purposeValue !== initialState.current.purpose;
  const bookingInPast = window !== undefined && Date.parse(window.end) <= Date.now();
  useEffect(() => {
    props.onStateChange?.({ target, draft, window, purpose: purposeValue, eventKind, dirty });
  }, [dirty, draft, eventKind, props.onStateChange, purposeValue, target, window]);
  const compact = props.density === "compact";

  return (
    <Form of={form} className={cn("max-w-2xl", compact && "flex min-h-0 flex-col")} aria-busy={busy} onSubmit={submit}>
      <div className={compact ? "min-h-0 flex-1 space-y-4 overflow-y-auto px-4 py-3" : "space-y-8"}>
        {fixedTarget ? (
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
            {eventKind === "BOOKING" ? (
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
              </>
            ) : null}
            <ZonedBookingWindowFields
              displayTimezone={displayTimezone}
              schedulingTimezone={target.timezone}
              slotGranularityMinutes={target.slotGranularityMinutes}
              maxBookingDurationMinutes={eventKind === "MAINTENANCE" ? 0 : target.maxBookingDurationMinutes}
              openingStart={eventKind === "MAINTENANCE" ? "00:00" : target.openingStart}
              openingEnd={eventKind === "MAINTENANCE" ? "24:00" : target.openingEnd}
              enforceOpeningHours={eventKind !== "MAINTENANCE"}
              value={draft}
              onChange={setDraft}
              onResolved={resolved}
              allowPolicyMismatch={Boolean(originalDraft && sameWindowDraft(draft, originalDraft))}
              disabled={busy}
              density={props.density}
              showErrors={attempted}
            />
            {bookingInPast && (
              <p role="status" className="text-sm text-amber-800 dark:text-amber-200">
                {t("bookings.warnings.past")}
              </p>
            )}
          </>
        )}
        {attempted && target && !window && <FieldError>{t("bookings.errors.windowRequired")}</FieldError>}
        {props.error && <FieldError>{props.error}</FieldError>}
        <RenderFields fields={textFields} form={form} disabled={busy} density={props.density} />
        <p className={cn("text-right text-xs text-muted-foreground", compact ? "-mt-2" : "-mt-6")} aria-live="polite">
          {t(eventKind === "MAINTENANCE" ? "bookings.form.notesCount" : "bookings.form.purposeCount", {
            count: purposeValue.length,
          })}
        </p>
      </div>
      {compact ? (
        <ActionBar
          className="[&>div>button:last-of-type]:!flex"
          actions={[
            ...(props.onMoreOptions ? [{ label: t("bookings.form.moreOptions"), onClick: props.onMoreOptions }] : []),
            {
              label: editing
                ? t("bookings.form.save")
                : eventKind === "MAINTENANCE"
                  ? t("bookings.form.submitMaintenance")
                  : t("bookings.form.submit"),
              icon: CheckIcon,
              preferred: true,
              disabled: busy || props.submissionBlocked,
              onClick: () => void submit({ purpose: purposeValue }),
            },
            { label: t("bookings.form.cancel"), onClick: props.onCancel, alwaysVisible: true },
          ]}
        />
      ) : (
        <div className="flex gap-3">
          <Button type="submit" disabled={busy || props.submissionBlocked} aria-busy={busy}>
            {editing
              ? t("bookings.form.save")
              : eventKind === "MAINTENANCE"
                ? t("bookings.form.submitMaintenance")
                : t("bookings.form.submit")}
          </Button>
          {props.onCancel ? (
            <Button type="button" variant="outline" disabled={busy} onClick={props.onCancel}>
              {t("bookings.form.cancel")}
            </Button>
          ) : (
            <Link
              className={buttonVariants({
                variant: "outline",
                className: busy ? "pointer-events-none opacity-50" : "",
              })}
              to="/booking/calendar"
              search={{ date: draft.startDate, target: target?.globalId }}
              aria-disabled={busy}
              tabIndex={busy ? -1 : undefined}
            >
              {t("bookings.form.cancel")}
            </Link>
          )}
        </div>
      )}
    </Form>
  );
}
