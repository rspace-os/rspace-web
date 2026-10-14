import { Temporal } from "@js-temporal/polyfill";
import { GlobeIcon, InfoIcon } from "lucide-react";
import { type ChangeEvent, useId, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { BookingInstrumentTimeTooltip } from "@/modules/booking/components/BookingInstrumentTimeTooltip";
import { maximumDurationMessage } from "@/modules/booking/creation/useCreateBooking";
import { bookingTimeZoneOptions } from "@/modules/booking/domain/bookingDisplayPreferences";
import type { OpeningHoursEntry } from "@/modules/booking/domain/bookingOpeningHours";
import {
  type BookingWindowDraft,
  formatPlainDate,
  formatWallClockTime,
  sameTimeZone,
  type WallClockResolution,
  wallClockInstant,
} from "@/modules/booking/domain/bookingTime";
import { Alert, AlertDescription } from "@/modules/common/ui/alert";
import { Button } from "@/modules/common/ui/button";
import { FieldError, FieldLegend, FieldSet } from "@/modules/common/ui/field";
import { Input } from "@/modules/common/ui/input";
import { Label } from "@/modules/common/ui/label";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/modules/common/ui/tooltip";
import { cn } from "@/modules/common/utils/cn";
import { validateBookingWindow } from "./bookingWindowValidation";

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

function snapTimeToIncrement(time: string, incrementMinutes: number, schedulingOffsetMinutes = 0): string {
  const match = /^(\d{2}):(\d{2})$/.exec(time);
  if (!match) return time;
  const minutes = Number(match[1]) * 60 + Number(match[2]);
  const firstValidMinute = ((-schedulingOffsetMinutes % incrementMinutes) + incrementMinutes) % incrementMinutes;
  const latestValidMinute =
    firstValidMinute + Math.floor((24 * 60 - 1 - firstValidMinute) / incrementMinutes) * incrementMinutes;
  const snappedMinutes = Math.min(
    latestValidMinute,
    Math.max(
      firstValidMinute,
      firstValidMinute + Math.round((minutes - firstValidMinute) / incrementMinutes) * incrementMinutes,
    ),
  );
  return `${String(Math.floor(snappedMinutes / 60)).padStart(2, "0")}:${String(snappedMinutes % 60).padStart(2, "0")}`;
}

export function ZonedBookingWindowFields({
  displayTimezone,
  schedulingTimezone,
  slotGranularityMinutes,
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
  const fieldId = `booking-window-${useId()}`;
  const windowErrorId = `${fieldId}-errors`;
  const resolvedDisplayTimezone = displayTimezone ?? "UTC";
  const resolvedSchedulingTimezone = schedulingTimezone ?? resolvedDisplayTimezone;
  const resolvedEndTimezone = endTimezone ?? resolvedDisplayTimezone;
  const zones = { start: resolvedDisplayTimezone, end: resolvedEndTimezone };
  const result = useMemo(
    () =>
      validateBookingWindow(
        value,
        { start: resolvedDisplayTimezone, end: resolvedEndTimezone },
        {
          schedulingTimezone: resolvedSchedulingTimezone,
          slotGranularityMinutes,
          maxBookingDurationMinutes,
          openingHours,
          enforceOpeningHours,
          allowPolicyMismatch,
        },
      ),
    [
      value,
      resolvedDisplayTimezone,
      resolvedEndTimezone,
      resolvedSchedulingTimezone,
      slotGranularityMinutes,
      maxBookingDurationMinutes,
      openingHours,
      enforceOpeningHours,
      allowPolicyMismatch,
    ],
  );
  const { granularityInvalid, openingInvalid, maximumDurationInvalid, policyInvalid } = result;
  const schedulingEndpoint = (instant: string | undefined) =>
    instant ? Temporal.Instant.from(instant).toZonedDateTimeISO(resolvedSchedulingTimezone) : undefined;
  const change = (patch: Partial<BookingWindowDraft>) => onChange({ ...value, ...patch });
  // The time each endpoint was last snapped to, so the adjustment is shown rather than silent.
  const [snappedTimes, setSnappedTimes] = useState<Partial<Record<"start" | "end", string>>>({});
  const changeTime = (name: "start" | "end", time: string) => {
    const timeKey = `${name}Time` as const;
    const occurrenceKey = `${name}Occurrence` as const;
    setSnappedTimes((current) => ({ ...current, [name]: undefined }));
    change({ [timeKey]: time, [occurrenceKey]: undefined });
  };
  const snapTime = (name: "start" | "end") => {
    const dateKey = `${name}Date` as const;
    const timeKey = `${name}Time` as const;
    const occurrenceKey = `${name}Occurrence` as const;
    if (
      originalValue &&
      value[dateKey] === originalValue[dateKey] &&
      value[timeKey] === originalValue[timeKey] &&
      value[occurrenceKey] === originalValue[occurrenceKey]
    ) {
      return;
    }
    const endpointResolution = result[name];
    const instant =
      endpointResolution?.kind === "unique"
        ? endpointResolution.instant
        : endpointResolution?.kind === "ambiguous"
          ? endpointResolution[value[occurrenceKey] ?? "earlier"]
          : undefined;
    const schedulingTime = schedulingEndpoint(instant);
    const match = /^(\d{2}):(\d{2})$/.exec(value[timeKey]);
    const displayMinute = match ? Number(match[1]) * 60 + Number(match[2]) : undefined;
    const schedulingOffsetMinutes =
      schedulingTime && displayMinute !== undefined
        ? schedulingTime.hour * 60 + schedulingTime.minute - displayMinute
        : 0;
    const snappedTime = snapTimeToIncrement(value[timeKey], slotGranularityMinutes, schedulingOffsetMinutes);
    if (snappedTime === value[timeKey]) return;
    setSnappedTimes((current) => ({ ...current, [name]: snappedTime }));
    change({ [timeKey]: snappedTime, [occurrenceKey]: undefined });
  };
  const snapNote = (name: "start" | "end") => {
    const snappedTime = snappedTimes[name];
    return (
      <div aria-live="polite">
        {snappedTime !== undefined && snappedTime === value[`${name}Time`] ? (
          <p role="status" id={`${fieldId}-${name}-snap`} className="text-sm text-muted-foreground">
            {t("bookings.form.timeSnapped", {
              time: formatWallClockTime(snappedTime),
              increment: slotGranularityMinutes,
            })}
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
        ]
          .filter(Boolean)
          .join(" ") || undefined
      : undefined;

  const occurrenceFields = (
    name: "start" | "end",
    endpointResolution: WallClockResolution | undefined,
    occurrence: "earlier" | "later" | undefined,
  ) => {
    if (endpointResolution?.kind !== "ambiguous") return null;
    const occurrenceKey = `${name}Occurrence` as const;
    const endpointErrorId = `${fieldId}-${name}-error`;
    return (
      <fieldset className="space-y-2">
        <legend className="text-sm font-medium">{t("bookings.form.occurrence")}</legend>
        {(["earlier", "later"] as const).map((choice) => (
          <Label key={choice} className="flex items-center gap-2">
            <input
              type="radio"
              name={`${fieldId}-${name}-occurrence`}
              disabled={disabled}
              checked={occurrence === choice}
              aria-invalid={showErrors && !occurrence ? true : undefined}
              aria-describedby={showErrors && !occurrence ? endpointErrorId : undefined}
              onChange={() => change({ [occurrenceKey]: choice })}
            />
            {t(`bookings.form.${choice}Occurrence`, {
              offset: offset(endpointResolution[choice], zones[name]),
            })}
          </Label>
        ))}
        {showErrors && !occurrence && (
          <FieldError id={endpointErrorId}>{t("bookings.errors.occurrenceRequired")}</FieldError>
        )}
      </fieldset>
    );
  };

  const endpointError = (name: "start" | "end", endpointResolution: WallClockResolution | undefined) =>
    showErrors && endpointResolution?.kind === "nonexistent" ? (
      <FieldError id={`${fieldId}-${name}-error`}>{t("bookings.errors.nonexistentTime")}</FieldError>
    ) : null;

  const timeInput = (name: "start" | "end", endpointResolution: WallClockResolution | undefined) => {
    const inputProps = {
      id: `${fieldId}-${name}-time`,
      "aria-label": t(`bookings.form.${name}Time`),
      type: "time",
      step: slotGranularityMinutes * 60,
      required: true,
      "aria-invalid":
        showErrors && (!endpointResolution || endpointInvalid(name, endpointResolution)) ? true : undefined,
      "aria-describedby": errorDescription(name, endpointResolution),
      disabled,
      value: value[`${name}Time`],
      onChange: (event: ChangeEvent<HTMLInputElement>) => changeTime(name, event.currentTarget.value),
      onBlur: () => snapTime(name),
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
        <Label htmlFor={`${id}-input`}>{t(`bookings.form.${name}Timezone`)}</Label>
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
    let time = formatWallClockTime(local.toPlainTime().toString({ smallestUnit: "minute" }));
    if (local.hoursInDay !== 24) time += ` (UTC${local.offset})`;
    const date = local.toPlainDate().toString();
    return date === enteredDate ? time : `${formatPlainDate(date)} ${time}`;
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
        aria-label={t(`bookings.form.${name}Date`)}
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
        <FieldLegend>{t(`bookings.form.${name}`)}</FieldLegend>
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
        <Label htmlFor={timeId}>{t(`bookings.form.${name}`)}</Label>
        {timeInput(name, endpointResolution)}
        {snapNote(name)}
        {endpointError(name, endpointResolution)}
        {occurrenceFields(name, endpointResolution, occurrence)}
      </div>
    );
  };

  const windowErrors = showErrors ? (
    <div id={windowErrorId}>
      {(!result.start || !result.end) && <FieldError>{t("bookings.errors.windowRequired")}</FieldError>}
      {result.orderInvalid && <FieldError>{t("bookings.errors.endAfterStart")}</FieldError>}
      {granularityInvalid && !allowPolicyMismatch && <FieldError>{t("bookings.errors.granularity")}</FieldError>}
      {openingInvalid && !allowPolicyMismatch && <FieldError>{t("bookings.errors.openingHours")}</FieldError>}
      {maximumDurationInvalid && !allowPolicyMismatch && (
        <FieldError>{maximumDurationMessage(result.maximumDurationLimitMinutes, t)}</FieldError>
      )}
    </div>
  ) : null;

  if (density === "compact" && value.startDate === value.endDate) {
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
