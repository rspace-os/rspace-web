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
import {
  ALL_ISO_WEEKDAYS,
  ALWAYS_OPEN,
  coversInterval,
  type OpeningSchedule,
} from "@/modules/booking/domain/bookingOpeningHours";
import {
  type BookingWindowDraft,
  bookingLocale,
  currentWallClock,
  isPlainDate,
  sameTimeZone,
  wallClockDraftFromInstants,
  wallClockInstant,
  zonedDayBounds,
} from "@/modules/booking/domain/bookingTime";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
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
  t: TFunction<"booking">,
): string {
  const uniform = target.openDays.length === ALL_ISO_WEEKDAYS.length && target.openingExceptions.length === 0;
  if (uniform && target.openingStart === "00:00" && target.openingEnd === "24:00") return t("bookings.form.openAllDay");
  if (uniform && sameTimeZone(target.timezone, displayTimezone)) {
    // Read-outs print a closing midnight as 00:00, as formatOpeningRange does.
    const end = target.openingEnd === "24:00" ? "00:00" : target.openingEnd;
    return t("bookings.form.openingHours", { start: target.openingStart, end });
  }
  if (coversInterval(target, target.timezone, zonedDayBounds(date, displayTimezone))) {
    return t("bookings.form.openAllDayOnDate");
  }
  const windows = openingWindowsOnDate(date, displayTimezone, target);
  if (windows.length === 0) return t("bookings.form.closedOnDate");
  return t("bookings.form.openingHoursOnDate", {
    hours: formatList(
      windows.map((window) => `${window.start} - ${window.end}`),
      bookingLocale(),
      { type: "unit" },
    ),
  });
}

function openingSchedule({ openingStart, openingEnd, openDays, openingExceptions }: OpeningSchedule): OpeningSchedule {
  return { openingStart, openingEnd, openDays, openingExceptions };
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
  const initialPurpose = props.mode === "add" ? (props.initialPurpose ?? "") : (props.booking.purpose ?? "");
  const eventKind = props.mode === "add" ? props.eventKind : props.booking.kind;
  const textFields = useMemo(() => purposeFields(eventKind), [eventKind]);
  // Drafts crossing the props (initialWindow, windowAdjustment, onDraftChange, onStateChange) use displayTimezone,
  // the viewer's preference. The fields and the draft held here use formTimezones: displayTimezone for both endpoints
  // unless the viewer picks another, optionally a separate one for the end. A picked zone keeps the entered wall clock,
  // so the instants move.
  const displayTimezone = props.displayTimezone ?? (editing ? fixedTarget?.timezone : initialTarget?.timezone) ?? "UTC";
  const [target, setTarget] = useState<BookableItemOption | undefined>(editing ? fixedTarget : initialTarget);
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
    : emptyDraft(
        initialDate ?? (initialTarget ? currentWallClock(new Date().toISOString(), formTimezone).date : undefined),
      );
  const [draft, setDraft] = useState<BookingWindowDraft>(() => originalDraft ?? addDraft);
  const displayDraft = useMemo(
    () => convertDraft(draft, formTimezones, displayTimezone),
    [draft, formTimezones, displayTimezone],
  );
  const appliedWindowAdjustment = useRef<BookingWindowDraft | undefined>(undefined);
  const [attempted, setAttempted] = useState(false);
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
    setDraft((current) => (sameWindowDraft(next, current) ? current : next));
  }, [props.windowAdjustment, props.windowAdjustmentTarget, target?.globalId, displayTimezone, formTimezones]);
  useEffect(() => {
    const adjustment = props.windowAdjustment;
    const adjustmentTarget = props.windowAdjustmentTarget ?? target?.globalId;
    if (
      adjustment &&
      adjustment === appliedWindowAdjustment.current &&
      adjustmentTarget === target?.globalId &&
      sameWindowDraft(adjustment, displayDraft)
    ) {
      props.onWindowAdjustmentApplied?.(adjustment, adjustmentTarget);
    }
  }, [
    displayDraft,
    props.windowAdjustment,
    props.windowAdjustmentTarget,
    props.onWindowAdjustmentApplied,
    target?.globalId,
  ]);
  if (initialTarget !== previousInitialTarget) {
    setPreviousInitialTarget(initialTarget);
    if (!editing && !target && initialTarget) {
      const date = currentWallClock(new Date().toISOString(), formTimezone).date;
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
  const allowPolicyMismatch = Boolean(originalDraft && sameWindowDraft(draft, originalDraft));
  const enteredWindow = useMemo(() => resolveBookingWindow(draft, formTimezones).window, [draft, formTimezones]);
  const window = useMemo(
    () =>
      target
        ? validateBookingWindow(draft, formTimezones, {
            schedulingTimezone: target.timezone,
            slotGranularityMinutes: target.slotGranularityMinutes,
            maxBookingDurationMinutes: eventKind === "MAINTENANCE" ? 0 : target.maxBookingDurationMinutes,
            ...(eventKind === "MAINTENANCE" ? ALWAYS_OPEN : openingSchedule(target)),
            enforceOpeningHours: eventKind !== "MAINTENANCE",
            allowPolicyMismatch,
          }).window
        : undefined,
    [draft, formTimezones, target, eventKind, allowPolicyMismatch],
  );
  const selectTarget = (next: BookableItemOption | undefined) => {
    props.onTargetChange?.(next);
    setTarget(next);
    const today = currentWallClock(new Date().toISOString(), formTimezone).date;
    setDraft((current) => ({
      ...current,
      ...(next && !current.startDate ? { startDate: today, endDate: today } : {}),
      startOccurrence: undefined,
      endOccurrence: undefined,
    }));
  };
  const submit = async (input: PurposeInput) => {
    // Commit validation attributes before looking up the first invalid field.
    flushSync(() => setAttempted(true));
    if (!target || !window || submittingRef.current || busy || props.submissionBlocked) {
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
    !sameWindowDraft(draft, initialState.draft) ||
    formTimezones.start !== initialState.timezones.start ||
    formTimezones.end !== initialState.timezones.end ||
    purposeValue !== initialState.purpose;
  const bookingInPast = window !== undefined && Date.parse(window.end) <= Date.now();
  const notifyStateChange = useEffectEvent((state: BookingFormState) => props.onStateChange?.(state));
  useEffect(() => {
    notifyStateChange({ target, draft: displayDraft, window, enteredWindow, purpose: purposeValue, eventKind, dirty });
  }, [dirty, displayDraft, enteredWindow, eventKind, purposeValue, target, window]);
  const compact = props.density === "compact";
  const inline = props.layout === "inline";
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
      disabled={busy}
      density={props.density}
      // Once both endpoints are entered the times are checked, so a rule they break shows before submission.
      showErrors={attempted || Boolean(draft.startDate && draft.startTime && draft.endDate && draft.endTime)}
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
      <div className={compact ? "min-h-0 flex-1 space-y-4 overflow-y-auto px-4 py-3" : "space-y-6"}>
        {fixedTarget && !compact && !inline ? (
          <div className="space-y-1">
            <p className="text-sm font-medium">{t("bookings.form.item")}</p>
            <InventoryItem
              name={fixedTarget?.name ?? ""}
              globalId={fixedTarget?.globalId ?? ""}
              href={`/globalId/${fixedTarget?.globalId ?? ""}`}
              idLinkLabel={t("bookings.form.openItem", { globalId: fixedTarget?.globalId ?? "" })}
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
                isPlainDate(draft.startDate)
                  ? draft.startDate
                  : currentWallClock(new Date().toISOString(), formTimezone).date,
                formTimezone,
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
        {props.afterWindowFields}
        <BookingFormAlerts
          warning={
            bookingInPast && props.warning ? (
              <>
                <p>{t("bookings.warnings.past")}</p>
                <p>{props.warning}</p>
              </>
            ) : (
              (props.warning ?? (bookingInPast ? t("bookings.warnings.past") : undefined))
            )
          }
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
              label: editing
                ? t("bookings.form.save")
                : eventKind === "MAINTENANCE"
                  ? t("bookings.form.submitMaintenance")
                  : t("bookings.form.submit"),
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
