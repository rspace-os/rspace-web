import { Temporal } from "@js-temporal/polyfill";
import { GlobeIcon, InfoIcon } from "lucide-react";
import { type ChangeEvent, useId, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { BookingInstrumentTimeTooltip } from "@/modules/booking/components/BookingInstrumentTimeTooltip";
import { formatBookingLength, formatIncrement } from "@/modules/booking/configuration/incrementFacts";
import { maximumDurationMessage } from "@/modules/booking/creation/useCreateBooking";
import { bookingTimeZoneOptions, useBookingTimeFormat } from "@/modules/booking/domain/bookingDisplayPreferences";
import {
  type IncrementSettings,
  type IncrementViolation,
  snapEndToIncrement,
  snapStartToGrid,
} from "@/modules/booking/domain/bookingIncrement";
import type { OpeningHoursEntry } from "@/modules/booking/domain/bookingOpeningHours";
import {
  type BookingWindowDraft,
  bookingDateTimeLocale,
  formatPlainDate,
  formatWallClockTime,
  sameDraft,
  sameTimeZone,
  type WallClockResolution,
  wallClockEndpointFromInstant,
  wallClockInstant,
} from "@/modules/booking/domain/bookingTime";
import { Alert, AlertDescription } from "@/modules/common/ui/alert";
import { Button } from "@/modules/common/ui/button";
import { FieldError, FieldLegend, FieldSet } from "@/modules/common/ui/field";
import { Input } from "@/modules/common/ui/input";
import { Label } from "@/modules/common/ui/label";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/modules/common/ui/tooltip";
import { cn } from "@/modules/common/utils/cn";
import { resolveBookingWindow, validateBookingWindow } from "./bookingWindowValidation";

export {
  type BookingWindowPolicy,
  type EndpointTimezones,
  endpointTimezones,
  type ResolvedBookingWindow,
  resolveBookingWindow,
  validateBookingWindow,
} from "./bookingWindowValidation";

function offset(instant: string, timezone: string): string {
  return Temporal.Instant.from(instant).toZonedDateTimeISO(timezone).offset;
}

/** What the end snap could not apply: a Day end that does not exist, or a repeated one to choose. */
type EndIssue = (
  | { kind: "missing"; date: string; time: string }
  | { kind: "choose"; date: string; time: string; earlier: string; later: string }
) & { draft: BookingWindowDraft; contextKey: string };

/** Maintenance is exempt from the item's increment, as the server is. */
export const UNRESTRICTED_INCREMENT: IncrementSettings = {
  slotGranularityMinutes: 1,
  incrementUnit: "MINUTES",
  alignToIncrement: false,
};

export function ZonedBookingWindowFields({
  displayTimezone,
  schedulingTimezone,
  slotGranularityMinutes,
  incrementUnit = "MINUTES",
  alignToIncrement = true,
  maxBookingDurationMinutes,
  openingHours,
  enforceOpeningHours = true,
  value,
  onChange,
  allowPolicyMismatch = false,
  originalValue,
  disabled = false,
  density = "comfortable",
  showErrors = true,
  startErrorId,
  endTimezone,
  onTimezoneChange,
}: {
  displayTimezone?: string;
  schedulingTimezone?: string;
  slotGranularityMinutes: number;
  incrementUnit?: IncrementSettings["incrementUnit"];
  alignToIncrement?: boolean;
  maxBookingDurationMinutes: number;
  openingHours: readonly OpeningHoursEntry[];
  enforceOpeningHours?: boolean;
  value: BookingWindowDraft;
  onChange: (value: BookingWindowDraft) => void;
  allowPolicyMismatch?: boolean;
  originalValue?: BookingWindowDraft;
  disabled?: boolean;
  density?: "comfortable" | "compact";
  showErrors?: boolean;
  startErrorId?: string;
  /** The end's zone when it differs from displayTimezone, which then applies to the start only. */
  endTimezone?: string;
  /**
   * Offers a globe beside the start time that shows or hides a timezone field under each endpoint's row, and a note with
   * the entered times in the scheduling timezone when either zone differs from it. The entered wall clock stays as typed
   * when either zone changes.
   */
  onTimezoneChange?: (name: "start" | "end", timezone: string) => void;
}) {
  const { t } = useTranslation("booking");
  const endpointLabels = {
    start: {
      label: t("bookings.form.start"),
      time: t("bookings.form.startTime"),
      date: t("bookings.form.startDate"),
      timezone: t("bookings.form.startTimezone"),
    },
    end: {
      label: t("bookings.form.end"),
      time: t("bookings.form.endTime"),
      date: t("bookings.form.endDate"),
      timezone: t("bookings.form.endTimezone"),
    },
  };
  const timeFormat = useBookingTimeFormat();
  const fieldId = `booking-window-${useId()}`;
  const windowErrorId = `${fieldId}-errors`;
  const resolvedDisplayTimezone = displayTimezone ?? "UTC";
  const resolvedSchedulingTimezone = schedulingTimezone ?? resolvedDisplayTimezone;
  const resolvedEndTimezone = endTimezone ?? resolvedDisplayTimezone;
  const zones = { start: resolvedDisplayTimezone, end: resolvedEndTimezone };
  const result = useMemo(
    () =>
      validateBookingWindow(value, zones, {
        schedulingTimezone: resolvedSchedulingTimezone,
        slotGranularityMinutes,
        incrementUnit,
        alignToIncrement,
        maxBookingDurationMinutes,
        openingHours,
        enforceOpeningHours,
        allowPolicyMismatch,
      }),
    [
      value,
      resolvedDisplayTimezone,
      resolvedEndTimezone,
      resolvedSchedulingTimezone,
      slotGranularityMinutes,
      incrementUnit,
      alignToIncrement,
      maxBookingDurationMinutes,
      openingHours,
      enforceOpeningHours,
      allowPolicyMismatch,
    ],
  );
  const {
    rule,
    incrementViolation: violation,
    missingDayEnd,
    openingInvalid,
    maximumDurationInvalid,
    policyInvalid,
  } = result;
  const increment = formatIncrement({ slotGranularityMinutes, incrementUnit }, t);
  const locale = bookingDateTimeLocale(timeFormat);
  const change = (patch: Partial<BookingWindowDraft>) => onChange({ ...value, ...patch });
  // The endpoint each snap produced, so the adjustment is announced rather than silent until the next edit.
  const [snaps, setSnaps] = useState<Partial<Record<"start" | "end", { date: string; time: string; note: string }>>>(
    {},
  );
  const [endIssue, setEndIssue] = useState<EndIssue>();
  // An end issue belongs to the draft, rule and zones it was found for; any change retires it.
  const endIssueContextKey = [
    slotGranularityMinutes,
    incrementUnit,
    alignToIncrement,
    zones.start,
    zones.end,
    resolvedSchedulingTimezone,
  ].join("|");
  const changeTime = (name: "start" | "end", time: string) => {
    const timeKey = `${name}Time` as const;
    const occurrenceKey = `${name}Occurrence` as const;
    setSnaps((current) => ({ ...current, [name]: undefined }));
    change({ [timeKey]: time, [occurrenceKey]: undefined });
  };
  // A repeated time is snapped only once the user has chosen its occurrence; until then the form asks for it.
  const typedInstant = (name: "start" | "end") => wallClockInstant(result[name], value[`${name}Occurrence`]);
  const applySnap = (
    name: "start" | "end",
    instant: string,
    note: (time: string, date: string) => string,
    // The user picked this instant's occurrence, rather than typing a time the snap moved.
    chosen = false,
  ) => {
    const draft = wallClockEndpointFromInstant(instant, zones[name]);
    // A snap onto a repeated time keeps an occurrence only if the user chose it for this very time; a choice made
    // for the typed time does not carry over to a different one.
    const sameTime = draft.date === value[`${name}Date`] && draft.time === value[`${name}Time`];
    const next = chosen || (sameTime && value[`${name}Occurrence`]) ? draft : { ...draft, occurrence: undefined };
    if (
      next.date === value[`${name}Date`] &&
      next.time === value[`${name}Time`] &&
      next.occurrence === value[`${name}Occurrence`]
    ) {
      return;
    }
    setSnaps((current) => ({
      ...current,
      [name]: {
        date: next.date,
        time: next.time,
        note: note(formatWallClockTime(next.time, locale), formatPlainDate(next.date, locale)),
      },
    }));
    change({ [`${name}Date`]: next.date, [`${name}Time`]: next.time, [`${name}Occurrence`]: next.occurrence });
  };
  const originalEndpoint = (name: "start" | "end") =>
    originalValue &&
    value[`${name}Date`] === originalValue[`${name}Date`] &&
    value[`${name}Time`] === originalValue[`${name}Time`] &&
    value[`${name}Occurrence`] === originalValue[`${name}Occurrence`];
  // Only an aligned start moves to the clock grid; the end follows the start by whole increments.
  const snapStart = () => {
    if (originalEndpoint("start")) return;
    if (rule.kind !== "elapsed" || !rule.aligned) return;
    const instant = typedInstant("start");
    if (!instant) return;
    applySnap("start", snapStartToGrid(instant, rule.minutes, resolvedSchedulingTimezone), (time) =>
      t("bookings.form.startSnapped", { time, increment }),
    );
  };
  const endSnapNote = (time: string, date: string) => t("bookings.form.endSnapped", { time, date, increment });
  const snapEnd = () => {
    if (originalEndpoint("end")) return;
    const start = wallClockInstant(result.start, value.startOccurrence);
    const end = typedInstant("end");
    if (!start || !end) return;
    const snap = snapEndToIncrement(rule, start, end, resolvedSchedulingTimezone);
    setEndIssue(undefined);
    if (snap.kind === "instant") {
      applySnap("end", snap.instant, endSnapNote);
      return;
    }
    // The nearest Day end does not exist; name it rather than moving the booking to another date.
    if (snap.kind === "dayEndMissing") {
      setEndIssue({ kind: "missing", date: snap.date, time: snap.time, draft: value, contextKey: endIssueContextKey });
      return;
    }
    // In another display zone the repeated time reads as two different times, so ask which one to use here.
    if (!sameTimeZone(zones.end, resolvedSchedulingTimezone)) {
      setEndIssue({ ...snap, kind: "choose", draft: value, contextKey: endIssueContextKey });
      return;
    }
    if (snap.date === value.endDate && snap.time === value.endTime) return;
    setSnaps((current) => ({
      ...current,
      end: {
        date: snap.date,
        time: snap.time,
        note: endSnapNote(formatWallClockTime(snap.time, locale), formatPlainDate(snap.date, locale)),
      },
    }));
    change({ endDate: snap.date, endTime: snap.time, endOccurrence: undefined });
  };
  // Shown only while the draft is the one the end snap examined.
  const activeEndIssue =
    endIssue && endIssue.contextKey === endIssueContextKey && sameDraft(endIssue.draft, value) ? endIssue : undefined;
  const endIssueFields = () => {
    if (!activeEndIssue) return null;
    const timezone = resolvedSchedulingTimezone;
    const date = formatPlainDate(activeEndIssue.date, locale);
    const time = formatWallClockTime(activeEndIssue.time, locale);
    if (activeEndIssue.kind === "missing") {
      return (
        <FieldError id={`${fieldId}-end-issue`}>
          {t("bookings.errors.dayEndMissing", { time, date, timezone })}
        </FieldError>
      );
    }
    const choice = activeEndIssue;
    return occurrenceChoiceFields({
      inputName: `${fieldId}-end-issue-occurrence`,
      fieldsetId: `${fieldId}-end-issue`,
      legend: t("bookings.form.repeatedEnd", { time, date, timezone }),
      values: choice,
      timezone,
      onSelect: (occurrence) => {
        setEndIssue(undefined);
        applySnap("end", choice[occurrence], endSnapNote, true);
      },
    });
  };
  const snapNote = (name: "start" | "end") => {
    const snap = snaps[name];
    return (
      <div aria-live="polite">
        {snap && snap.time === value[`${name}Time`] && snap.date === value[`${name}Date`] ? (
          <p role="status" id={`${fieldId}-${name}-snap`} className="text-sm text-muted-foreground">
            {snap.note}
          </p>
        ) : null}
      </div>
    );
  };

  const timezoneFieldId = (name: "start" | "end") => `${fieldId}-${name}-timezone`;
  const [timezoneOpen, setTimezoneOpen] = useState(false);
  // Typed text that is not yet a known zone; dropped on blur so the field shows the current zone again.
  const [timezoneText, setTimezoneText] = useState<Partial<Record<"start" | "end", string>>>({});
  const timezoneOptions = useMemo(
    () => bookingTimeZoneOptions(resolvedDisplayTimezone, resolvedEndTimezone, resolvedSchedulingTimezone),
    [resolvedDisplayTimezone, resolvedEndTimezone, resolvedSchedulingTimezone],
  );
  const showTimezoneFields = Boolean(onTimezoneChange) && timezoneOpen;

  const endpointUnresolved = (name: "start" | "end", endpointResolution: WallClockResolution | undefined) =>
    endpointResolution?.kind === "nonexistent" ||
    (endpointResolution?.kind === "ambiguous" && !value[`${name}Occurrence`]);

  const endpointInvalid = (name: "start" | "end", endpointResolution: WallClockResolution | undefined) =>
    (name === "start" && Boolean(startErrorId)) ||
    endpointUnresolved(name, endpointResolution) ||
    (name === "end" && result.orderInvalid) ||
    (policyInvalid && !allowPolicyMismatch);

  const errorDescription = (name: "start" | "end", endpointResolution: WallClockResolution | undefined) =>
    showErrors
      ? [
          name === "start" ? startErrorId : undefined,
          endpointUnresolved(name, endpointResolution) ? `${fieldId}-${name}-error` : undefined,
          !endpointResolution || result.orderInvalid || (policyInvalid && !allowPolicyMismatch)
            ? windowErrorId
            : undefined,
          name === "end" && activeEndIssue ? `${fieldId}-end-issue` : undefined,
        ]
          .filter(Boolean)
          .join(" ") || undefined
      : undefined;

  const occurrenceChoiceFields = ({
    inputName,
    fieldsetId,
    legend,
    values,
    timezone,
    selected,
    error,
    onSelect,
  }: {
    inputName: string;
    fieldsetId?: string;
    legend: string;
    values: { earlier: string; later: string };
    timezone: string;
    selected?: "earlier" | "later";
    error?: { id: string; message: string };
    onSelect: (choice: "earlier" | "later") => void;
  }) => (
    <fieldset className="space-y-2" id={fieldsetId}>
      <legend className="text-sm font-medium">{legend}</legend>
      {(["earlier", "later"] as const).map((choice) => (
        <Label key={choice} className="flex items-center gap-2">
          <input
            type="radio"
            name={inputName}
            disabled={disabled}
            checked={selected === choice}
            aria-invalid={error ? true : undefined}
            aria-describedby={error?.id}
            onChange={() => onSelect(choice)}
          />
          {choice === "earlier"
            ? t("bookings.form.earlierOccurrence", { offset: offset(values[choice], timezone) })
            : t("bookings.form.laterOccurrence", { offset: offset(values[choice], timezone) })}
        </Label>
      ))}
      {error ? <FieldError id={error.id}>{error.message}</FieldError> : null}
    </fieldset>
  );

  const occurrenceFields = (
    name: "start" | "end",
    endpointResolution: WallClockResolution | undefined,
    occurrence: "earlier" | "later" | undefined,
  ) => {
    if (endpointResolution?.kind !== "ambiguous") return null;
    const occurrenceKey = `${name}Occurrence` as const;
    const endpointErrorId = `${fieldId}-${name}-error`;
    return occurrenceChoiceFields({
      inputName: `${fieldId}-${name}-occurrence`,
      legend: t("bookings.form.occurrence"),
      values: endpointResolution,
      timezone: zones[name],
      selected: occurrence,
      error:
        showErrors && !occurrence
          ? { id: endpointErrorId, message: t("bookings.errors.occurrenceRequired") }
          : undefined,
      onSelect: (choice) => {
        // Choosing the occurrence resolves the start, so an aligned start snaps now rather than on blur.
        if (name === "start" && rule.kind === "elapsed" && rule.aligned) {
          const snapped = snapStartToGrid(endpointResolution[choice], rule.minutes, resolvedSchedulingTimezone);
          if (snapped !== endpointResolution[choice]) {
            applySnap("start", snapped, (time) => t("bookings.form.startSnapped", { time, increment }), true);
            return;
          }
        }
        change({ [occurrenceKey]: choice });
      },
    });
  };

  const endpointError = (name: "start" | "end", endpointResolution: WallClockResolution | undefined) =>
    showErrors && endpointResolution?.kind === "nonexistent" ? (
      <FieldError id={`${fieldId}-${name}-error`}>{t("bookings.errors.nonexistentTime")}</FieldError>
    ) : null;

  const timeInput = (name: "start" | "end", endpointResolution: WallClockResolution | undefined) => {
    const inputProps = {
      id: `${fieldId}-${name}-time`,
      "aria-label": endpointLabels[name].time,
      type: "time",
      // Only an aligned start has a clock grid; every other time is a whole minute.
      step: name === "start" && rule.kind === "elapsed" && rule.aligned ? rule.minutes * 60 : 60,
      required: true,
      "aria-invalid":
        showErrors && (!endpointResolution || endpointInvalid(name, endpointResolution)) ? true : undefined,
      "aria-describedby": errorDescription(name, endpointResolution),
      disabled,
      value: value[`${name}Time`],
      onChange: (event: ChangeEvent<HTMLInputElement>) => changeTime(name, event.currentTarget.value),
      onBlur: name === "start" ? snapStart : snapEnd,
    };
    if (!onTimezoneChange || name !== "start") return <Input {...inputProps} />;
    const timezoneLabel = t("bookings.form.changeTimezone");
    return (
      <div className="flex items-center gap-2">
        <Input {...inputProps} className="min-w-0 flex-1" />
        <Tooltip>
          <TooltipTrigger
            render={
              <Button
                type="button"
                variant="outline"
                size="icon"
                aria-label={timezoneLabel}
                aria-expanded={showTimezoneFields}
                aria-controls={showTimezoneFields ? `${timezoneFieldId("start")} ${timezoneFieldId("end")}` : undefined}
                disabled={disabled}
                onClick={() => setTimezoneOpen((open) => !open)}
              />
            }
          >
            <GlobeIcon aria-hidden="true" />
          </TooltipTrigger>
          <TooltipContent>{timezoneLabel}</TooltipContent>
        </Tooltip>
      </div>
    );
  };

  const timezoneField = (name: "start" | "end") => {
    if (!onTimezoneChange || !showTimezoneFields) return null;
    const id = timezoneFieldId(name);
    return (
      <div id={id} className="space-y-2">
        <Label htmlFor={`${id}-input`}>{endpointLabels[name].timezone}</Label>
        <Input
          id={`${id}-input`}
          role="combobox"
          aria-expanded="false"
          list={`${id}-options`}
          disabled={disabled}
          value={timezoneText[name] ?? zones[name]}
          onChange={(event) => {
            const next = event.currentTarget.value;
            // Only a known zone reaches the form; a partial name would not resolve.
            if (timezoneOptions.includes(next)) {
              setTimezoneText((text) => ({ ...text, [name]: undefined }));
              if (next !== zones[name]) onTimezoneChange(name, next);
            } else {
              setTimezoneText((text) => ({ ...text, [name]: next }));
            }
          }}
          onBlur={() => setTimezoneText((text) => ({ ...text, [name]: undefined }))}
        />
        <datalist id={`${id}-options`}>
          {timezoneOptions.map((timeZone) => (
            <option key={timeZone} value={timeZone} />
          ))}
        </datalist>
      </div>
    );
  };

  // From the resolved endpoints rather than the policy-checked window, so the note stays when the times are rejected.
  const startInstant = wallClockInstant(result.start, value.startOccurrence);
  const endInstant = wallClockInstant(result.end, value.endOccurrence);
  // The time only while it falls on the entered date, otherwise the date as well. On a clock-change day the offset
  // tells the repeated hour's two occurrences apart.
  const instrumentTime = (instant: string, enteredDate: string) => {
    const local = Temporal.Instant.from(instant).toZonedDateTimeISO(resolvedSchedulingTimezone);
    const clock = formatWallClockTime(
      local.toPlainTime().toString({ smallestUnit: "minute" }),
      bookingDateTimeLocale(timeFormat),
    );
    const time = local.hoursInDay === 24 ? clock : t("time.wallClockWithOffset", { time: clock, offset: local.offset });
    const date = local.toPlainDate().toString();
    return date === enteredDate ? time : t("time.wallClockWithDate", { date: formatPlainDate(date), time });
  };
  const instrumentTimes =
    (!sameTimeZone(resolvedSchedulingTimezone, zones.start) || !sameTimeZone(resolvedSchedulingTimezone, zones.end)) &&
    startInstant &&
    endInstant &&
    !result.orderInvalid ? (
      // A standing note rather than an alert, so screen readers are not interrupted on every keystroke.
      <Alert role="note">
        <InfoIcon aria-hidden="true" />
        <AlertDescription>
          {t("bookings.form.instrumentTimes", {
            timezone: resolvedSchedulingTimezone,
            start: instrumentTime(startInstant, value.startDate),
            end: instrumentTime(endInstant, value.endDate),
          })}
        </AlertDescription>
      </Alert>
    ) : null;

  const endpoint = (
    name: "start" | "end",
    endpointResolution: WallClockResolution | undefined,
    occurrence: "earlier" | "later" | undefined,
  ) => {
    const dateKey = `${name}Date` as const;
    const occurrenceKey = `${name}Occurrence` as const;
    const dateId = `${fieldId}-${name}-date`;
    const timeId = `${fieldId}-${name}-time`;
    const describedBy = errorDescription(name, endpointResolution);
    const endpointInstant = wallClockInstant(endpointResolution, occurrence);
    const dateInput = (
      <Input
        id={dateId}
        aria-label={endpointLabels[name].date}
        type="date"
        required
        aria-invalid={showErrors && (!value[dateKey] || endpointInvalid(name, endpointResolution)) ? true : undefined}
        aria-describedby={describedBy}
        disabled={disabled}
        value={value[dateKey]}
        onChange={(event) => change({ [dateKey]: event.currentTarget.value, [occurrenceKey]: undefined })}
      />
    );
    return (
      <FieldSet className="gap-3">
        <FieldLegend>{endpointLabels[name].label}</FieldLegend>
        <div className={cn("grid sm:grid-cols-2", density === "compact" ? "gap-2" : "gap-4")}>
          <div className="space-y-2">
            <Label htmlFor={dateId}>{t("bookings.form.date")}</Label>
            <BookingInstrumentTimeTooltip
              start={endpointInstant}
              displayTimeZone={zones[name]}
              instrumentTimeZone={resolvedSchedulingTimezone}
              trigger={dateInput}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor={timeId}>{t("bookings.form.time")}</Label>
            {timeInput(name, endpointResolution)}
            {snapNote(name)}
            {name === "end" ? endIssueFields() : null}
          </div>
        </div>
        {timezoneField(name)}
        {endpointError(name, endpointResolution)}
        {occurrenceFields(name, endpointResolution, occurrence)}
      </FieldSet>
    );
  };

  const compactTime = (
    name: "start" | "end",
    endpointResolution: WallClockResolution | undefined,
    occurrence: "earlier" | "later" | undefined,
  ) => {
    const timeId = `${fieldId}-${name}-time`;
    return (
      <div className="min-w-0 space-y-2">
        <Label htmlFor={timeId}>{endpointLabels[name].label}</Label>
        {timeInput(name, endpointResolution)}
        {snapNote(name)}
        {name === "end" ? endIssueFields() : null}
        {endpointError(name, endpointResolution)}
        {occurrenceFields(name, endpointResolution, occurrence)}
      </div>
    );
  };

  const incrementError = (current: IncrementViolation | null) => {
    // The end field already names the missing Day end the snap found.
    if (activeEndIssue?.kind === "missing" && (current === "duration" || current === "dayEndMissing")) return null;
    if (current === "alignment") {
      return (
        <FieldError>{t("bookings.errors.alignment", { increment, timezone: resolvedSchedulingTimezone })}</FieldError>
      );
    }
    if (current === "dayEndMissing" && missingDayEnd) {
      return (
        <FieldError>
          {t("bookings.errors.dayEndMissing", {
            time: formatWallClockTime(missingDayEnd.time, locale),
            date: formatPlainDate(missingDayEnd.date, locale),
            timezone: resolvedSchedulingTimezone,
          })}
        </FieldError>
      );
    }
    if (current === "duration" || current === "dayEndMissing") {
      const resolved = resolveBookingWindow(value, zones).window;
      const length = resolved
        ? formatBookingLength(incrementUnit, resolved, resolvedSchedulingTimezone, t, locale)
        : "";
      return (
        <FieldError>
          {rule.kind === "days"
            ? t("bookings.errors.dayIncrement", { length, increment, timezone: resolvedSchedulingTimezone })
            : t("bookings.errors.durationMultiple", { length, increment })}
        </FieldError>
      );
    }
    return null;
  };

  const windowErrors = showErrors ? (
    <div id={windowErrorId}>
      {(!result.start || !result.end) && <FieldError>{t("bookings.errors.windowRequired")}</FieldError>}
      {result.orderInvalid && <FieldError>{t("bookings.errors.endAfterStart")}</FieldError>}
      {!allowPolicyMismatch && incrementError(violation)}
      {openingInvalid && !allowPolicyMismatch && <FieldError>{t("bookings.errors.openingHours")}</FieldError>}
      {maximumDurationInvalid && !allowPolicyMismatch && (
        <FieldError>{maximumDurationMessage(result.maximumDurationLimitMinutes, t)}</FieldError>
      )}
    </div>
  ) : null;

  // The compact layout has one date for both endpoints, so a multi-day draft or Day increment needs both dates.
  if (density === "compact" && value.startDate === value.endDate && incrementUnit !== "DAYS") {
    const dateId = `${fieldId}-date`;
    const dateInvalid = !value.startDate || Boolean(startErrorId);
    const compactTooltipStart = startInstant ?? endInstant;
    const compactTooltipEnd = startInstant && !result.orderInvalid ? endInstant : undefined;
    const dateInput = (
      <Input
        id={dateId}
        aria-label={t("bookings.form.date")}
        type="date"
        required
        aria-invalid={showErrors && dateInvalid ? true : undefined}
        aria-describedby={showErrors ? (!value.startDate ? windowErrorId : startErrorId) : undefined}
        disabled={disabled}
        value={value.startDate}
        onChange={(event) =>
          change({
            startDate: event.currentTarget.value,
            startOccurrence: undefined,
            endDate: event.currentTarget.value,
            endOccurrence: undefined,
          })
        }
      />
    );
    return (
      <div className="space-y-4">
        <div className="space-y-2">
          <Label htmlFor={dateId}>{t("bookings.form.date")}</Label>
          <BookingInstrumentTimeTooltip
            start={compactTooltipStart}
            end={compactTooltipEnd}
            displayTimeZone={resolvedDisplayTimezone}
            instrumentTimeZone={resolvedSchedulingTimezone}
            trigger={dateInput}
          />
        </div>
        <div className="grid grid-cols-2 gap-2">
          {compactTime("start", result.start, value.startOccurrence)}
          {compactTime("end", result.end, value.endOccurrence)}
        </div>
        {timezoneField("start")}
        {timezoneField("end")}
        {instrumentTimes}
        {windowErrors}
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {endpoint("start", result.start, value.startOccurrence)}
      {endpoint("end", result.end, value.endOccurrence)}
      {instrumentTimes}
      {windowErrors}
    </div>
  );
}
