import { Form, useField, useForm } from "@formisch/react";
import { Link } from "@tanstack/react-router";
import { CheckIcon, SaveIcon } from "lucide-react";
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
import type { Booking } from "@/modules/booking/domain/booking";
import { ALL_ISO_WEEKDAYS } from "@/modules/booking/domain/bookingOpeningHours";
import {
  type BookingWindowDraft,
  currentWallClock,
  sameTimeZone,
  wallClockDraftFromInstants,
  wallClockInstant,
} from "@/modules/booking/domain/bookingTime";
import type { BookingsCreate } from "@/modules/common/api/v2/generated/models/bookingsCreate";
import type { BookingsReadKind } from "@/modules/common/api/v2/generated/models/bookingsReadKind";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveConfig";
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
  type EndpointTimezones,
  endpointTimezones,
  type ResolvedBookingWindow,
  resolveBookingWindow,
  validateBookingWindow,
  ZonedBookingWindowFields,
} from "./ZonedBookingWindowFields";

type PurposeInput = { purpose: NonNullable<BookingsCreate["purpose"]> };

function purposeFields(eventKind: BookingsReadKind) {
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
  eventKind: BookingsReadKind;
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
  eventKind: BookingsReadKind;
  dirty: boolean;
};

type BookingFormCommonProps = {
  displayTimezone?: string;

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
  showMobileItemInformation?: boolean;
  showRulesSummary?: boolean;
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
        initialPurpose?: string;
        eventKind: BookingsReadKind;
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
  const editing = props.mode === "edit";
  const fixedTarget = props.mode === "edit" ? props.configuration : props.lockTarget ? props.initialTarget : undefined;
  const initialTarget = props.mode === "add" ? props.initialTarget : undefined;
  const initialDate = props.mode === "add" ? props.initialDate : undefined;
  const initialWindow = props.mode === "add" ? props.initialWindow : undefined;
  const initialPurpose = props.mode === "add" ? (props.initialPurpose ?? "") : (props.booking.purpose ?? "");
  const eventKind = props.mode === "add" ? props.eventKind : props.booking.kind;
  const textFields = useMemo(() => purposeFields(eventKind), [eventKind]);
  // Drafts crossing the props (initialWindow, onStateChange) use displayTimezone, the viewer's preference. The fields
  // and the draft held here use formTimezones: displayTimezone for both endpoints unless the viewer picks another,
  // optionally a separate one for the end. A picked zone keeps the entered wall clock, so the instants move.
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
    appliedWindowAdjustment.current = adjustment;
    setDraft((current) => (sameWindowDraft(adjustment, current) ? current : adjustment));
  }, [props.windowAdjustment]);
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
  const validation = useMemo(
    () =>
      target
        ? validateBookingWindow(draft, formTimezones, {
            schedulingTimezone: target.timezone,
            slotGranularityMinutes: eventKind === "MAINTENANCE" ? 1 : target.slotGranularityMinutes,
            incrementUnit: eventKind === "MAINTENANCE" ? "MINUTES" : target.incrementUnit,
            maxBookingDurationMinutes: eventKind === "MAINTENANCE" ? 0 : target.maxBookingDurationMinutes,
            openingHours: target.openingHours,
            enforceOpeningHours: eventKind !== "MAINTENANCE",
            allowPolicyMismatch,
          })
        : undefined,
    [draft, formTimezones, target, eventKind, allowPolicyMismatch],
  );
  const window = validation?.window;
  const selectTarget = (next: BookableItemOption | undefined) => {
    setTarget(next);
    const today = currentWallClock(new Date().toISOString(), formTimezone).date;
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
    !sameWindowDraft(draft, initialState.draft) ||
    formTimezones.start !== initialState.timezones.start ||
    formTimezones.end !== initialState.timezones.end ||
    purposeValue !== initialState.purpose;
  const bookingInPast = hasPastStart();
  const notifyStateChange = useEffectEvent((state: BookingFormState) => props.onStateChange?.(state));
  useEffect(() => {
    notifyStateChange({ target, draft: displayDraft, window, enteredWindow, purpose: purposeValue, eventKind, dirty });
  }, [dirty, displayDraft, enteredWindow, eventKind, purposeValue, target, window]);
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
  // The entered wall clock stays; a repeated-hour choice made for one zone means nothing in another.
  const applyTimezones = (next: { start: string; end: string }) => {
    setDraft({
      ...draft,
      ...(next.start === formTimezones.start ? {} : { startOccurrence: undefined }),
      ...(next.end === formTimezones.end ? {} : { endOccurrence: undefined }),
    });
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
      slotGranularityMinutes={eventKind === "MAINTENANCE" ? 1 : (target?.slotGranularityMinutes ?? 1)}
      incrementUnit={eventKind === "MAINTENANCE" ? "MINUTES" : (target?.incrementUnit ?? "MINUTES")}
      maxBookingDurationMinutes={eventKind === "MAINTENANCE" ? 0 : (target?.maxBookingDurationMinutes ?? 0)}
      openingHours={target?.openingHours ?? []}
      enforceOpeningHours={Boolean(target) && eventKind !== "MAINTENANCE"}
      value={draft}
      onChange={setDraft}
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
            <BookableItemPicker value={target} onChange={selectTarget} disabled={busy} eventKind={eventKind} />
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
        {allowPolicyMismatch && validation?.policyInvalid && (
          <p role="status" className="text-sm text-muted-foreground">
            {eventKind === "MAINTENANCE"
              ? t("bookings.form.policyMismatchNotes")
              : t("bookings.form.policyMismatchPurpose")}
          </p>
        )}
        {bookingInPast && <FieldError id={pastStartErrorId}>{t("bookings.warnings.past")}</FieldError>}
        <BookingFormAlerts
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
          className="sticky bottom-0 z-10 bg-popover"
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
              icon: editing ? SaveIcon : CheckIcon,
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
            {editing ? <SaveIcon aria-hidden="true" /> : null}
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
