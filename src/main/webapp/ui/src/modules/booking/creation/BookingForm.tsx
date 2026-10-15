import { Form, useField, useForm } from "@formisch/react";
import { Link } from "@tanstack/react-router";
import { CheckIcon } from "lucide-react";
import type { ReactNode } from "react";
import { useEffect, useEffectEvent, useId, useMemo, useRef, useState } from "react";
import { flushSync } from "react-dom";
import { useTranslation } from "react-i18next";
import * as v from "valibot";
import { openingHoursFacts } from "@/modules/booking/configuration/openingHoursFacts";
import { BookableItemPicker } from "@/modules/booking/creation/BookableItemPicker";
import { BookingFormAlerts } from "@/modules/booking/creation/BookingFormAlerts";
import { BookingItemInformationCard } from "@/modules/booking/creation/BookingItemInformation";
import type { BookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import type { BookingConflict } from "@/modules/booking/domain/availability";
import type { Booking, BookingEventKind } from "@/modules/booking/domain/booking";
import { ALL_ISO_WEEKDAYS } from "@/modules/booking/domain/bookingOpeningHours";
import {
  type BookingWindowDraft,
  currentWallClock,
  sameDraft,
  wallClockDraftFromInstants,
} from "@/modules/booking/domain/bookingTime";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import { RenderFields } from "@/modules/common/collection-form/RenderFields";
import {
  RESPONSIVE_INLINE_FIELD_CONTAINER_CLASS_NAME,
  RESPONSIVE_INLINE_FIELD_GRID_CLASS_NAME,
} from "@/modules/common/collection-form/responsiveFieldLayout";
import { ActionBar } from "@/modules/common/ui/action-bar";
import { Button, buttonVariants } from "@/modules/common/ui/button";
import { FieldError } from "@/modules/common/ui/field";
import { InventoryItem } from "@/modules/common/ui/inventory-item";
import { cn } from "@/modules/common/utils/cn";
import {
  type ResolvedBookingWindow,
  validateBookingWindow,
  ZonedBookingWindowFields,
} from "./ZonedBookingWindowFields";

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
  conflicts?: readonly BookingConflict[];
  conflictSeverity?: "warning" | "error";
  submissionBlocked?: boolean;
  outcomeUncertain?: boolean;
  density?: "comfortable" | "compact";
  layout?: "stacked" | "inline";
  formId?: string;
  windowAdjustment?: BookingWindowDraft;
  windowAdjustmentTarget?: string;
  showMobileItemInformation?: boolean;
  showRulesSummary?: boolean;
  onCancel?: () => void;
  onMoreOptions?: () => void;
  onStateChange?: (state: BookingFormState) => void;
  onWindowAdjustmentApplied?: (draft: BookingWindowDraft, targetGlobalId?: string) => void;
  onDraftChange?: (draft: BookingWindowDraft, targetGlobalId?: string) => void;
  onTargetChange?: (target: BookableItemOption | undefined) => void;
  warning?: ReactNode;
  /** Extra fields rendered directly after the date and time fields, for example a Repeat control. */
  afterWindowFields?: ReactNode;
  onSubmit: (submission: BookingFormSubmission) => Promise<unknown>;
};

type BookingFormProps = BookingFormCommonProps &
  (
    | {
        mode: "add";
        initialTarget?: BookableItemOption;
        initialDate?: string;
        initialWindow?: BookingWindowDraft;
        initialPurpose?: string;
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

export function BookingForm(props: BookingFormProps) {
  const { t } = useTranslation("booking");
  const editing = props.mode === "edit";
  const fixedTarget = props.mode === "edit" ? props.configuration : props.lockTarget ? props.initialTarget : undefined;
  const initialTarget = props.mode === "add" ? props.initialTarget : undefined;
  const initialDate = props.mode === "add" ? props.initialDate : undefined;
  const initialWindow = props.mode === "add" ? props.initialWindow : undefined;
  const initialPurpose = props.mode === "add" ? (props.initialPurpose ?? "") : (props.booking.purpose ?? "");
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
  const appliedWindowAdjustment = useRef<BookingWindowDraft | undefined>(undefined);
  const [attemptedAt, setAttemptedAt] = useState<number>();
  const attempted = attemptedAt !== undefined;
  const form = useForm({
    schema: v.object({ purpose: v.pipe(v.string(), v.maxLength(1000)) }),
    initialInput: { purpose: initialPurpose },
  });
  const purpose = useField(form, { path: ["purpose"] }).input;
  const purposeValue = typeof purpose === "string" ? purpose : "";
  const [submitting, setSubmitting] = useState(false);
  const submittingRef = useRef(false);
  const generatedFormId = useId();
  const formId = props.formId ?? generatedFormId;
  const pastStartErrorId = `${formId}-past-start`;
  const [initialState, setInitialState] = useState({
    targetGlobalId: (editing ? fixedTarget : initialTarget)?.globalId ?? "",
    draft: originalDraft ?? addDraft,
    purpose: initialPurpose,
    eventKind,
  });
  const busy = props.pending || submitting;
  const [previousInitialTarget, setPreviousInitialTarget] = useState(initialTarget);
  useEffect(() => {
    const adjustment = props.windowAdjustment;
    if (!adjustment || adjustment === appliedWindowAdjustment.current) return;
    const adjustmentTarget = props.windowAdjustmentTarget ?? target?.globalId;
    if (adjustmentTarget !== target?.globalId) return;
    appliedWindowAdjustment.current = adjustment;
    setDraft((current) => (sameDraft(adjustment, current) ? current : adjustment));
  }, [props.windowAdjustment, props.windowAdjustmentTarget, target?.globalId]);
  useEffect(() => {
    const adjustment = props.windowAdjustment;
    const adjustmentTarget = props.windowAdjustmentTarget ?? target?.globalId;
    if (
      adjustment &&
      adjustment === appliedWindowAdjustment.current &&
      adjustmentTarget === target?.globalId &&
      sameDraft(adjustment, draft)
    ) {
      props.onWindowAdjustmentApplied?.(adjustment, adjustmentTarget);
    }
  }, [draft, props.windowAdjustment, props.windowAdjustmentTarget, props.onWindowAdjustmentApplied, target?.globalId]);
  if (initialTarget !== previousInitialTarget) {
    setPreviousInitialTarget(initialTarget);
    if (!editing && !target && initialTarget) {
      const date = currentWallClock(new Date().toISOString(), displayTimezone).date;
      const seedDates = !draft.startDate && !initialDate;
      const nextDraft = seedDates ? { ...draft, startDate: date, endDate: date } : draft;
      setTarget(initialTarget);
      setDraft(nextDraft);
      setInitialState({
        ...initialState,
        targetGlobalId: initialTarget.globalId,
        draft: seedDates ? nextDraft : initialState.draft,
      });
    }
  }
  const allowPolicyMismatch = Boolean(originalDraft && sameDraft(draft, originalDraft));
  const window = useMemo(
    () =>
      target
        ? validateBookingWindow(draft, displayTimezone, {
            schedulingTimezone: target.timezone,
            slotGranularityMinutes: target.slotGranularityMinutes,
            maxBookingDurationMinutes: eventKind === "MAINTENANCE" ? 0 : target.maxBookingDurationMinutes,
            openingHours: target.openingHours,
            enforceOpeningHours: eventKind !== "MAINTENANCE",
            allowPolicyMismatch,
          }).window
        : undefined,
    [draft, displayTimezone, target, eventKind, allowPolicyMismatch],
  );
  const selectTarget = (next: BookableItemOption | undefined) => {
    props.onTargetChange?.(next);
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
  const hasPastStart = () =>
    window !== undefined &&
    Date.parse(window.start) <= Date.now() &&
    (props.mode === "add" ||
      (Date.parse(props.booking.start) > Date.now() &&
        (Date.parse(window.start) !== Date.parse(props.booking.start) ||
          Date.parse(window.end) !== Date.parse(props.booking.end))));
  const submit = async (input: PurposeInput) => {
    // Commit validation attributes before looking up the first invalid field.
    flushSync(() => setAttemptedAt(Date.now()));
    if (!target || !window || hasPastStart() || submittingRef.current || busy || props.submissionBlocked) {
      const formElement = document.getElementById(formId);
      (
        formElement?.querySelector<HTMLElement>("[aria-invalid='true']") ??
        formElement?.querySelector<HTMLElement>("#booking-item-search")
      )?.focus();
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
    (target?.globalId ?? "") !== initialState.targetGlobalId ||
    !sameDraft(draft, initialState.draft) ||
    purposeValue !== initialState.purpose;
  const bookingInPast = hasPastStart();
  const notifyStateChange = useEffectEvent((state: BookingFormState) => props.onStateChange?.(state));
  useEffect(() => {
    notifyStateChange({ target, draft, window, purpose: purposeValue, eventKind, dirty });
  }, [dirty, draft, eventKind, purposeValue, target, window]);
  const compact = props.density === "compact";
  const inline = props.layout === "inline";
  const submitLabel = editing
    ? t("bookings.form.save")
    : eventKind === "MAINTENANCE"
      ? t("bookings.form.submitMaintenance")
      : t("bookings.form.submit");
  const firstHours = target?.openingHours[0];
  const uniformHours =
    firstHours &&
    target?.openingHours.length === ALL_ISO_WEEKDAYS.length &&
    target.openingHours.every((entry) => entry.start === firstHours.start && entry.end === firstHours.end)
      ? firstHours
      : undefined;
  const windowFields = (
    <ZonedBookingWindowFields
      displayTimezone={displayTimezone}
      schedulingTimezone={target?.timezone ?? displayTimezone}
      slotGranularityMinutes={target?.slotGranularityMinutes ?? 1}
      maxBookingDurationMinutes={eventKind === "MAINTENANCE" ? 0 : (target?.maxBookingDurationMinutes ?? 0)}
      openingHours={target?.openingHours ?? []}
      enforceOpeningHours={Boolean(target) && eventKind !== "MAINTENANCE"}
      value={draft}
      onChange={(next) => {
        props.onDraftChange?.(next, target?.globalId);
        setDraft(next);
      }}
      allowPolicyMismatch={allowPolicyMismatch}
      originalValue={originalDraft}
      disabled={busy}
      density={props.density}
      showErrors={
        attempted || (compact && Boolean(draft.startDate && draft.startTime && draft.endDate && draft.endTime))
      }
      startErrorId={bookingInPast ? pastStartErrorId : undefined}
    />
  );

  return (
    <Form
      id={formId}
      of={form}
      className={cn(!inline && "max-w-2xl", compact && "flex min-h-0 flex-col")}
      aria-busy={busy}
      onSubmit={submit}
    >
      <div className={compact ? "min-h-0 flex-1 space-y-4 overflow-y-auto px-4 py-3" : "space-y-8"}>
        {fixedTarget && !compact && !inline ? (
          <div className="space-y-1">
            <p className="text-sm font-medium">{t("bookings.form.item")}</p>
            <InventoryItem
              name={fixedTarget.name}
              globalId={fixedTarget.globalId}
              href={`/globalId/${fixedTarget.globalId}`}
              idLinkLabel={t("bookings.form.openItem", { globalId: fixedTarget.globalId })}
              compact
              size="xs"
            />
          </div>
        ) : fixedTarget ? null : (
          <div className="space-y-2">
            <BookableItemPicker
              value={target}
              onChange={selectTarget}
              token={props.token}
              disabled={busy}
              eventKind={eventKind}
            />
            {attempted && !target && <FieldError>{t("bookings.errors.itemRequired")}</FieldError>}
          </div>
        )}
        {target && props.showMobileItemInformation && !compact && !inline ? (
          <div className="@2xl:hidden">
            <BookingItemInformationCard as="section" item={target} displayTimezone={displayTimezone} />
          </div>
        ) : null}
        {target && eventKind === "BOOKING" && props.showRulesSummary !== false && !inline ? (
          <>
            {uniformHours ? (
              <p className="text-sm text-muted-foreground">
                {target.timezone === displayTimezone
                  ? t("bookings.form.openingHours", { start: uniformHours.start, end: uniformHours.end })
                  : t("bookings.form.openingHoursDifferentTimezone", {
                      start: uniformHours.start,
                      end: uniformHours.end,
                      timezone: target.timezone,
                    })}
              </p>
            ) : (
              <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-muted-foreground text-sm">
                {[
                  ...openingHoursFacts(target, t),
                  [t("bookableItemDetails.fields.timezone"), target.timezone] as const,
                ].map(([label, value]) => (
                  <div key={label} className="contents">
                    <dt>{label}</dt>
                    <dd>{value}</dd>
                  </div>
                ))}
              </dl>
            )}
            {target.maxBookingDurationMinutes > 0 ? (
              <p className="text-sm text-muted-foreground">
                {t("bookings.form.maximumDuration", {
                  count: target.maxBookingDurationMinutes,
                })}
              </p>
            ) : null}
          </>
        ) : null}
        {inline ? (
          <div className={RESPONSIVE_INLINE_FIELD_CONTAINER_CLASS_NAME}>
            <div className={`${RESPONSIVE_INLINE_FIELD_GRID_CLASS_NAME} gap-y-4`}>
              <div className="@md:col-span-2">{windowFields}</div>
            </div>
          </div>
        ) : (
          windowFields
        )}
        {bookingInPast && <FieldError id={pastStartErrorId}>{t("bookings.warnings.past")}</FieldError>}
        {props.afterWindowFields}
        <BookingFormAlerts
          warning={props.warning}
          error={props.error}
          conflicts={props.conflicts}
          displayTimezone={displayTimezone}
          conflictSeverity={props.conflictSeverity}
          outcomeUncertain={props.outcomeUncertain}
        />
        <RenderFields fields={textFields} form={form} disabled={busy} density={props.density} layout="stacked" />
        <p className={cn("text-right text-xs text-muted-foreground", compact ? "-mt-2" : "-mt-6")} aria-live="polite">
          {t(eventKind === "MAINTENANCE" ? "bookings.form.notesCount" : "bookings.form.purposeCount", {
            count: purposeValue.length,
          })}
        </p>
      </div>
      {inline ? null : compact ? (
        <ActionBar
          actions={[
            ...(props.onMoreOptions
              ? [
                  {
                    label: t("bookings.form.moreOptions"),
                    onClick: props.onMoreOptions,
                    disabled: busy || props.outcomeUncertain,
                  },
                ]
              : []),
            {
              label: submitLabel,
              icon: CheckIcon,
              preferred: true,
              alwaysVisible: true,
              disabled: busy || props.submissionBlocked,
              onClick: () => void submit({ purpose: purposeValue }),
            },
            { label: t("bookings.form.cancel"), onClick: props.onCancel, alwaysVisible: true },
          ]}
        />
      ) : (
        <div className="flex gap-3">
          <Button type="submit" disabled={busy || props.submissionBlocked} aria-busy={busy}>
            {submitLabel}
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
