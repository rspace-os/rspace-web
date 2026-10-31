import { Form, useField, useForm } from "@formisch/react";
import { Link } from "@tanstack/react-router";
import type { TFunction } from "i18next";
import { CheckIcon } from "lucide-react";
import type { ReactNode } from "react";
import { useEffect, useEffectEvent, useId, useMemo, useRef, useState } from "react";
import { flushSync } from "react-dom";
import { useTranslation } from "react-i18next";
import * as v from "valibot";
import { openingWindowsOnDate } from "@/modules/booking/components/DayTimelineEvent";
import { BookableItemPicker } from "@/modules/booking/creation/BookableItemPicker";
import { BookingFormAlerts } from "@/modules/booking/creation/BookingFormAlerts";
import { BookingItemInformationCard } from "@/modules/booking/creation/BookingItemInformation";
import type { BookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import type { BookingConflict } from "@/modules/booking/domain/availability";
import type { Booking, BookingEventKind } from "@/modules/booking/domain/booking";
import { todayInTimeZone, useBookingTimeFormat } from "@/modules/booking/domain/bookingDisplayPreferences";
import {
  ALL_ISO_WEEKDAYS,
  ALWAYS_OPEN,
  coversInterval,
  type OpeningSchedule,
} from "@/modules/booking/domain/bookingOpeningHours";
import {
  type BookingTimeFormat,
  type BookingWindowDraft,
  bookingDateTimeLocale,
  bookingLocale,
  formatWallClockTime,
  isPlainDate,
  sameDraft,
  sameTimeZone,
  wallClockDraftFromInstants,
  wallClockInstant,
  zonedDayBounds,
} from "@/modules/booking/domain/bookingTime";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveConfig";
import { RenderFields } from "@/modules/common/collection-form/RenderFields";
import {
  RESPONSIVE_INLINE_FIELD_CONTAINER_CLASS_NAME,
  RESPONSIVE_INLINE_FIELD_GRID_CLASS_NAME,
} from "@/modules/common/collection-form/responsiveFieldLayout";
import { formatList } from "@/modules/common/i18n/listFormat";
import { ActionBar } from "@/modules/common/ui/action-bar";
import { Button, buttonVariants } from "@/modules/common/ui/button";
import { FieldError } from "@/modules/common/ui/field";
import { InventoryItem } from "@/modules/common/ui/inventory-item";
import { cn } from "@/modules/common/utils/cn";
import {
  type EndpointTimezones,
  endpointTimezones,
  type ResolvedBookingWindow,
  resolveBookingWindow,
  validateBookingWindow,
  ZonedBookingWindowFields,
} from "./ZonedBookingWindowFields";

type PurposeInput = { purpose: string };

/**
 * Opening hours in the viewer's timezone, as the date and time fields use: "open all day" or one range when every day
 * shares the item's hours in the viewer's own zone; otherwise open all day on, closed on, or the windows intersecting
 * the viewer's date. The item's own timezone is named only in the item information panel.
 */
function openingHoursSummary(
  target: BookableItemOption,
  date: string,
  displayTimezone: string,
  timeFormat: BookingTimeFormat,
  t: TFunction<"booking">,
): string {
  const first = target.openingHours[0];
  const uniform =
    target.openingHours.length === ALL_ISO_WEEKDAYS.length &&
    first &&
    target.openingHours.every((entry) => entry.start === first.start && entry.end === first.end);
  if (uniform && first.start === "00:00" && first.end === "24:00") return t("bookings.form.openAllDay");
  if (uniform && sameTimeZone(target.timezone, displayTimezone)) {
    const locale = bookingDateTimeLocale(timeFormat);
    return t("bookings.form.openingHours", {
      start: formatWallClockTime(first.start, locale),
      end: formatWallClockTime(first.end, locale),
    });
  }
  if (coversInterval(target, target.timezone, zonedDayBounds(date, displayTimezone))) {
    return t("bookings.form.openAllDayOnDate");
  }
  const windows = openingWindowsOnDate(date, displayTimezone, target, timeFormat);
  if (windows.length === 0) return t("bookings.form.closedOnDate");
  return t("bookings.form.openingWindowsOnDate", {
    hours: formatList(
      windows.map((window) => `${window.start} - ${window.end}`),
      bookingLocale(),
      { type: "unit" },
    ),
  });
}

function openingSchedule({ openingHours }: OpeningSchedule): OpeningSchedule {
  return { openingHours };
}

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
  /** The window once it passes the item's rules; submission uses it. */
  window: ResolvedBookingWindow | undefined;
  /**
   * The window as soon as both endpoints resolve with the end after the start, even when it breaks the item's rules,
   * so conflicts can be checked straight away.
   */
  enteredWindow: ResolvedBookingWindow | undefined;
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
  // Hides the time zone fields; the form stays in the display timezone.
  fixedTimezone?: boolean;
  formId?: string;
  windowAdjustment?: BookingWindowDraft;
  windowAdjustmentTarget?: string;
  showMobileItemInformation?: boolean;
  showRulesSummary?: boolean;
  onCancel?: () => void;
  onMoreOptions?: () => void;
  onStateChange?: (state: BookingFormState) => void;
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

function wallClockEndpoint(instant: string, timezone: string) {
  const {
    startDate: date,
    startTime: time,
    startOccurrence: occurrence,
  } = wallClockDraftFromInstants(instant, instant, timezone);
  return { date, time, occurrence };
}

function draftFromInstants(start: string, end: string, timezones: EndpointTimezones): BookingWindowDraft {
  const zones = endpointTimezones(timezones);
  const startValue = wallClockEndpoint(start, zones.start);
  const endValue = wallClockEndpoint(end, zones.end);
  return {
    startDate: startValue.date,
    startTime: startValue.time,
    startOccurrence: startValue.occurrence,
    endDate: endValue.date,
    endTime: endValue.time,
    endOccurrence: endValue.occurrence,
  };
}

/**
 * The same instants as `draft` read in `from`, written as wall clocks in `to`, each endpoint in its own zone. Endpoints
 * convert separately, so an out-of-order draft still converts; one without a resolvable date and time stays as entered.
 */
function convertDraft(draft: BookingWindowDraft, from: EndpointTimezones, to: EndpointTimezones): BookingWindowDraft {
  const source = endpointTimezones(from);
  const destination = endpointTimezones(to);
  if (sameTimeZone(source.start, destination.start) && sameTimeZone(source.end, destination.end)) return draft;
  const { start, end } = resolveBookingWindow(draft, source);
  const startInstant = wallClockInstant(start, draft.startOccurrence);
  const endInstant = wallClockInstant(end, draft.endOccurrence);
  const startValue = startInstant ? wallClockEndpoint(startInstant, destination.start) : undefined;
  const endValue = endInstant ? wallClockEndpoint(endInstant, destination.end) : undefined;
  return {
    ...draft,
    ...(startValue
      ? { startDate: startValue.date, startTime: startValue.time, startOccurrence: startValue.occurrence }
      : {}),
    ...(endValue ? { endDate: endValue.date, endTime: endValue.time, endOccurrence: endValue.occurrence } : {}),
  };
}
export function BookingForm(props: BookingFormProps) {
  const { t } = useTranslation("booking");
  const timeFormat = useBookingTimeFormat();
  const editing = props.mode === "edit";
  const fixedTarget = props.mode === "edit" ? props.configuration : props.lockTarget ? props.initialTarget : undefined;
  const initialTarget = props.mode === "add" ? props.initialTarget : undefined;
  const initialDate = props.mode === "add" ? props.initialDate : undefined;
  const initialWindow = props.mode === "add" ? props.initialWindow : undefined;
  const initialPurpose = props.mode === "add" ? (props.initialPurpose ?? "") : (props.booking.purpose ?? "");
  const eventKind = props.mode === "add" ? props.eventKind : props.booking.kind;
  const textFields = useMemo(() => purposeFields(eventKind), [eventKind]);
  // Drafts crossing the props (initialWindow, windowAdjustment, onDraftChange, onStateChange) use displayTimezone,
  // the viewer's preference. The fields and the draft held here use formTimezones: displayTimezone for both endpoints
  // unless the viewer picks another, optionally a separate one for the end. A picked zone keeps the entered wall clock,
  // so the instants move.
  const displayTimezone = props.displayTimezone ?? (editing ? fixedTarget?.timezone : initialTarget?.timezone) ?? "UTC";
  const [selectedTarget, setSelectedTarget] = useState<BookableItemOption | undefined>(initialTarget);
  const target = editing ? props.configuration : selectedTarget;
  const [timezoneChoice, setTimezoneChoice] = useState<string>();
  // Set only while the end has its own zone.
  const [endTimezoneChoice, setEndTimezoneChoice] = useState<string>();
  const formTimezone = timezoneChoice ?? displayTimezone;
  const endFormTimezone = endTimezoneChoice ?? formTimezone;
  const formTimezones = useMemo(() => ({ start: formTimezone, end: endFormTimezone }), [formTimezone, endFormTimezone]);
  const originalDraft =
    props.mode === "edit" ? draftFromInstants(props.booking.start, props.booking.end, formTimezones) : undefined;
  const addDraft = initialWindow
    ? convertDraft(initialWindow, displayTimezone, formTimezones)
    : emptyDraft(initialDate ?? (initialTarget ? todayInTimeZone(formTimezone) : undefined));
  const [draft, setDraft] = useState<BookingWindowDraft>(() => originalDraft ?? addDraft);
  const displayDraft = useMemo(
    () => convertDraft(draft, formTimezones, displayTimezone),
    [draft, formTimezones, displayTimezone],
  );
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
    timezones: formTimezones,
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
    const next = convertDraft(adjustment, displayTimezone, formTimezones);
    setDraft((current) => (sameDraft(next, current) ? current : next));
  }, [props.windowAdjustment, props.windowAdjustmentTarget, target?.globalId, displayTimezone, formTimezones]);
  if (initialTarget !== previousInitialTarget) {
    setPreviousInitialTarget(initialTarget);
    if (!editing && !selectedTarget && initialTarget) {
      const date = todayInTimeZone(formTimezone);
      const seedDates = !draft.startDate && !initialDate;
      const nextDraft = seedDates ? { ...draft, startDate: date, endDate: date } : draft;
      setSelectedTarget(initialTarget);
      setDraft(nextDraft);
      setInitialState({
        ...initialState,
        targetGlobalId: initialTarget.globalId,
        draft: seedDates ? nextDraft : initialState.draft,
      });
    }
  }
  const allowPolicyMismatch = Boolean(originalDraft && sameDraft(draft, originalDraft));
  const enteredWindow = useMemo(() => resolveBookingWindow(draft, formTimezones).window, [draft, formTimezones]);
  const validation = useMemo(
    () =>
      target
        ? validateBookingWindow(draft, formTimezones, {
            schedulingTimezone: target.timezone,
            slotGranularityMinutes: target.slotGranularityMinutes,
            maxBookingDurationMinutes: eventKind === "MAINTENANCE" ? 0 : target.maxBookingDurationMinutes,
            ...(eventKind === "MAINTENANCE" ? ALWAYS_OPEN : openingSchedule(target)),
            enforceOpeningHours: eventKind !== "MAINTENANCE",
            allowPolicyMismatch,
          })
        : undefined,
    [draft, formTimezones, target, eventKind, allowPolicyMismatch],
  );
  const window = validation?.window;
  const selectTarget = (next: BookableItemOption | undefined) => {
    props.onTargetChange?.(next);
    setSelectedTarget(next);
    const today = todayInTimeZone(formTimezone);
    setDraft((current) => ({
      ...current,
      ...(next && !current.startDate ? { startDate: today, endDate: today } : {}),
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
        returnDate: displayDraft.startDate,
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
    formTimezones.start !== initialState.timezones.start ||
    formTimezones.end !== initialState.timezones.end ||
    purposeValue !== initialState.purpose;
  const bookingInPast = hasPastStart();
  const notifyStateChange = useEffectEvent((state: BookingFormState) => props.onStateChange?.(state));
  useEffect(() => {
    notifyStateChange({ target, draft: displayDraft, window, enteredWindow, purpose: purposeValue, eventKind, dirty });
  }, [dirty, displayDraft, enteredWindow, eventKind, purposeValue, target, window, props.windowAdjustment]);
  const compact = props.density === "compact";
  const inline = props.layout === "inline";
  const submitLabel = editing
    ? t("bookings.form.save")
    : eventKind === "MAINTENANCE"
      ? t("bookings.form.submitMaintenance")
      : t("bookings.form.submit");
  // The entered wall clock stays; a repeated-hour choice made for one zone means nothing in another.
  const applyTimezones = (next: { start: string; end: string }) => {
    const nextDraft = {
      ...draft,
      ...(next.start === formTimezones.start ? {} : { startOccurrence: undefined }),
      ...(next.end === formTimezones.end ? {} : { endOccurrence: undefined }),
    };
    props.onDraftChange?.(convertDraft(nextDraft, next, displayTimezone), target?.globalId);
    setDraft(nextDraft);
  };
  // The end follows the start's zone until it is given another; picking the start's zone for it links them again.
  const changeTimezone = (name: "start" | "end", next: string) => {
    if (name === "end") {
      setEndTimezoneChoice(sameTimeZone(next, formTimezones.start) ? undefined : next);
      applyTimezones({ ...formTimezones, end: next });
      return;
    }
    setTimezoneChoice(next);
    applyTimezones({ start: next, end: endTimezoneChoice ?? next });
  };
  const windowFields = (
    <ZonedBookingWindowFields
      displayTimezone={formTimezones.start}
      endTimezone={formTimezones.end}
      onTimezoneChange={props.fixedTimezone ? undefined : changeTimezone}
      schedulingTimezone={target?.timezone ?? formTimezone}
      slotGranularityMinutes={target?.slotGranularityMinutes ?? 1}
      maxBookingDurationMinutes={eventKind === "MAINTENANCE" ? 0 : (target?.maxBookingDurationMinutes ?? 0)}
      {...(eventKind === "MAINTENANCE" || !target ? ALWAYS_OPEN : openingSchedule(target))}
      enforceOpeningHours={Boolean(target) && eventKind !== "MAINTENANCE"}
      value={draft}
      onChange={(next) => {
        props.onDraftChange?.(convertDraft(next, formTimezones, displayTimezone), target?.globalId);
        setDraft(next);
      }}
      allowPolicyMismatch={allowPolicyMismatch}
      originalValue={originalDraft}
      disabled={busy}
      density={props.density}
      // Once both endpoints are entered the times are checked, so a rule they break shows before submission.
      showErrors={attempted || Boolean(draft.startDate && draft.startTime && draft.endDate && draft.endTime)}
      startErrorId={bookingInPast ? pastStartErrorId : undefined}
    />
  );

  return (
    <Form id={formId} of={form} className={cn(compact && "flex min-h-0 flex-col")} aria-busy={busy} onSubmit={submit}>
      <div className={compact ? "min-h-0 flex-1 space-y-4 overflow-y-auto px-4 py-3" : "space-y-6"}>
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
          <div className="@4xl:hidden">
            <BookingItemInformationCard
              as="section"
              item={target}
              displayTimezone={displayTimezone}
              date={displayDraft.startDate}
            />
          </div>
        ) : null}
        {target && eventKind === "BOOKING" && props.showRulesSummary !== false && !inline ? (
          <>
            <p className="text-sm text-muted-foreground">
              {openingHoursSummary(
                target,
                isPlainDate(draft.startDate) ? draft.startDate : todayInTimeZone(formTimezone),
                formTimezone,
                timeFormat,
                t,
              )}
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
        {inline ? (
          <div className={RESPONSIVE_INLINE_FIELD_CONTAINER_CLASS_NAME}>
            <div className={`${RESPONSIVE_INLINE_FIELD_GRID_CLASS_NAME} gap-y-4`}>
              <div className="@md:col-span-2">{windowFields}</div>
            </div>
          </div>
        ) : (
          windowFields
        )}
        {allowPolicyMismatch && validation?.policyInvalid && (
          <p role="status" className="text-sm text-muted-foreground">
            {eventKind === "MAINTENANCE"
              ? t("bookings.form.policyMismatchNotes")
              : t("bookings.form.policyMismatchPurpose")}
          </p>
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
        <p className={cn("text-right text-xs text-muted-foreground", compact ? "-mt-2" : "-mt-4")} aria-live="polite">
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
            { label: t("bookings.form.cancel"), onClick: props.onCancel, disabled: busy, alwaysVisible: true },
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
              search={{ date: displayDraft.startDate, target: target?.globalId }}
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
