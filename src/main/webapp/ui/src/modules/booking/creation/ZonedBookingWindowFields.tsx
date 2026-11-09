import { Temporal } from "@js-temporal/polyfill";
import { useId, useMemo } from "react";
import { useTranslation } from "react-i18next";
import { BookingInstrumentTimeTooltip } from "@/modules/booking/components/BookingInstrumentTimeTooltip";
import type { OpeningHoursEntry } from "@/modules/booking/domain/bookingOpeningHours";
import {
  type BookingWindowDraft,
  type WallClockResolution,
  wallClockInstant,
} from "@/modules/booking/domain/bookingTime";
import { FieldDescription, FieldError, FieldLegend, FieldSet } from "@/modules/common/ui/field";
import { Input } from "@/modules/common/ui/input";
import { Label } from "@/modules/common/ui/label";
import { cn } from "@/modules/common/utils/cn";
import { validateBookingWindow } from "./bookingWindowValidation";

export {
  type BookingWindowPolicy,
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
}) {
  const { t } = useTranslation("booking");
  const fieldId = `booking-window-${useId()}`;
  const windowErrorId = `${fieldId}-errors`;
  const resolvedDisplayTimezone = displayTimezone ?? "UTC";
  const resolvedSchedulingTimezone = schedulingTimezone ?? resolvedDisplayTimezone;
  const result = useMemo(
    () =>
      validateBookingWindow(value, resolvedDisplayTimezone, {
        schedulingTimezone: resolvedSchedulingTimezone,
        slotGranularityMinutes,
        maxBookingDurationMinutes,
        openingHours,
        enforceOpeningHours,
        allowPolicyMismatch,
      }),
    [
      value,
      resolvedDisplayTimezone,
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
    if (snappedTime !== value[timeKey]) change({ [timeKey]: snappedTime, [occurrenceKey]: undefined });
  };

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
              offset: offset(endpointResolution[choice], resolvedDisplayTimezone),
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

  const endpoint = (
    name: "start" | "end",
    endpointResolution: WallClockResolution | undefined,
    occurrence: "earlier" | "later" | undefined,
  ) => {
    const dateKey = `${name}Date` as const;
    const timeKey = `${name}Time` as const;
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
      <FieldSet>
        <FieldLegend>{t(`bookings.form.${name}`)}</FieldLegend>
        <div className={cn("grid sm:grid-cols-2", density === "compact" ? "gap-2" : "gap-4")}>
          <div className="space-y-2">
            <Label htmlFor={dateId}>{t("bookings.form.date")}</Label>
            <BookingInstrumentTimeTooltip
              start={endpointInstant}
              displayTimeZone={resolvedDisplayTimezone}
              instrumentTimeZone={resolvedSchedulingTimezone}
              trigger={dateInput}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor={timeId}>{t("bookings.form.time")}</Label>
            <Input
              id={timeId}
              aria-label={t(`bookings.form.${name}Time`)}
              type="time"
              step={slotGranularityMinutes * 60}
              required
              aria-invalid={
                showErrors && (!endpointResolution || endpointInvalid(name, endpointResolution)) ? true : undefined
              }
              aria-describedby={describedBy}
              disabled={disabled}
              value={value[timeKey]}
              onChange={(event) => change({ [timeKey]: event.currentTarget.value, [occurrenceKey]: undefined })}
              onBlur={() => snapTime(name)}
            />
          </div>
        </div>
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
    const timeKey = `${name}Time` as const;
    const occurrenceKey = `${name}Occurrence` as const;
    const timeId = `${fieldId}-${name}-time`;
    const describedBy = errorDescription(name, endpointResolution);
    return (
      <div className="min-w-0 space-y-2">
        <Label htmlFor={timeId}>{t(`bookings.form.${name}`)}</Label>
        <Input
          id={timeId}
          aria-label={t(`bookings.form.${name}Time`)}
          type="time"
          step={slotGranularityMinutes * 60}
          required
          aria-invalid={
            showErrors && (!endpointResolution || endpointInvalid(name, endpointResolution)) ? true : undefined
          }
          aria-describedby={describedBy}
          disabled={disabled}
          value={value[timeKey]}
          onChange={(event) => change({ [timeKey]: event.currentTarget.value, [occurrenceKey]: undefined })}
          onBlur={() => snapTime(name)}
        />
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
        <FieldError>{t("bookings.errors.maximumDuration")}</FieldError>
      )}
    </div>
  ) : null;

  const timezoneDescription = (
    <FieldDescription className="mb-2">
      {t("bookings.form.timezone", { timezone: resolvedDisplayTimezone })}
    </FieldDescription>
  );

  if (density === "compact" && value.startDate === value.endDate) {
    const dateId = `${fieldId}-date`;
    const dateInvalid = !value.startDate || Boolean(startErrorId);
    const startInstant = wallClockInstant(result.start, value.startOccurrence);
    const endInstant = wallClockInstant(result.end, value.endOccurrence);
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
        {timezoneDescription}
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
        {windowErrors}
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {timezoneDescription}
      {endpoint("start", result.start, value.startOccurrence)}
      {endpoint("end", result.end, value.endOccurrence)}
      {windowErrors}
    </div>
  );
}
