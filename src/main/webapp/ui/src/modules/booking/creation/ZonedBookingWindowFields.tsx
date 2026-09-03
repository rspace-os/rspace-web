import { Temporal } from "@js-temporal/polyfill";
import { useEffect, useId, useMemo } from "react";
import { useTranslation } from "react-i18next";
import {
  type BookingWindowDraft,
  resolveWallClock,
  type WallClockResolution,
  wallClockInstant,
} from "@/modules/booking/domain/bookingTime";
import { FieldError, FieldLegend, FieldSet } from "@/modules/common/ui/field";
import { Input } from "@/modules/common/ui/input";
import { Label } from "@/modules/common/ui/label";
import { cn } from "@/modules/common/utils/cn";

export type ResolvedBookingWindow = { start: string; end: string };

function resolution(date: string, time: string, timezone: string): WallClockResolution | undefined {
  if (!date || !time) return undefined;
  try {
    return resolveWallClock(date, time, timezone);
  } catch {
    return undefined;
  }
}

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

export function resolveBookingWindow(
  draft: BookingWindowDraft,
  displayTimezone: string,
): { window?: ResolvedBookingWindow; start?: WallClockResolution; end?: WallClockResolution; orderInvalid: boolean } {
  const start = resolution(draft.startDate, draft.startTime, displayTimezone);
  const end = resolution(draft.endDate, draft.endTime, displayTimezone);
  const startInstant = wallClockInstant(start, draft.startOccurrence);
  const endInstant = wallClockInstant(end, draft.endOccurrence);
  const orderInvalid = Boolean(startInstant && endInstant && Temporal.Instant.compare(endInstant, startInstant) <= 0);
  return {
    start,
    end,
    orderInvalid,
    window: startInstant && endInstant && !orderInvalid ? { start: startInstant, end: endInstant } : undefined,
  };
}

export function ZonedBookingWindowFields({
  displayTimezone,
  schedulingTimezone,
  timezone,
  slotGranularityMinutes,
  maxBookingDurationMinutes,
  openingStart,
  openingEnd,
  enforceOpeningHours = true,
  value,
  onChange,
  onResolved,
  allowPolicyMismatch = false,
  disabled = false,
  density = "comfortable",
  showErrors = true,
}: {
  displayTimezone?: string;
  schedulingTimezone?: string;
  /** @deprecated Pass displayTimezone and schedulingTimezone separately. */
  timezone?: string;
  slotGranularityMinutes: number;
  maxBookingDurationMinutes: number;
  openingStart: string;
  openingEnd: string;
  enforceOpeningHours?: boolean;
  value: BookingWindowDraft;
  onChange: (value: BookingWindowDraft) => void;
  onResolved: (value: ResolvedBookingWindow | undefined) => void;
  allowPolicyMismatch?: boolean;
  disabled?: boolean;
  density?: "comfortable" | "compact";
  showErrors?: boolean;
}) {
  const { t } = useTranslation("booking");
  const fieldId = `booking-window-${useId()}`;
  const windowErrorId = `${fieldId}-errors`;
  const resolvedDisplayTimezone = displayTimezone ?? timezone ?? "UTC";
  const resolvedSchedulingTimezone = schedulingTimezone ?? timezone ?? resolvedDisplayTimezone;
  const result = useMemo(() => resolveBookingWindow(value, resolvedDisplayTimezone), [resolvedDisplayTimezone, value]);
  const schedulingEndpoint = (instant: string | undefined) =>
    instant ? Temporal.Instant.from(instant).toZonedDateTimeISO(resolvedSchedulingTimezone) : undefined;
  const schedulingStart = schedulingEndpoint(result.window?.start);
  const schedulingEnd = schedulingEndpoint(result.window?.end);
  const startMinute = schedulingStart ? schedulingStart.hour * 60 + schedulingStart.minute : undefined;
  const endMinute = schedulingEnd ? schedulingEnd.hour * 60 + schedulingEnd.minute : undefined;
  const granularityInvalid =
    (startMinute !== undefined && startMinute % slotGranularityMinutes !== 0) ||
    (endMinute !== undefined && endMinute % slotGranularityMinutes !== 0);
  const openingInvalid =
    enforceOpeningHours &&
    Boolean(result.window && schedulingStart && schedulingEnd) &&
    (schedulingStart?.toPlainDate().toString() !== schedulingEnd?.toPlainDate().toString() ||
      `${String(schedulingStart?.hour).padStart(2, "0")}:${String(schedulingStart?.minute).padStart(2, "0")}` <
        openingStart ||
      (openingEnd !== "24:00" &&
        `${String(schedulingEnd?.hour).padStart(2, "0")}:${String(schedulingEnd?.minute).padStart(2, "0")}` >
          openingEnd));
  const maximumDurationInvalid =
    Boolean(result.window) &&
    maxBookingDurationMinutes > 0 &&
    Temporal.Instant.from(result.window?.end ?? "").epochMilliseconds -
      Temporal.Instant.from(result.window?.start ?? "").epochMilliseconds >
      maxBookingDurationMinutes * 60_000;
  const policyInvalid = granularityInvalid || openingInvalid || maximumDurationInvalid;
  const resolvedWindow = policyInvalid && !allowPolicyMismatch ? undefined : result.window;
  useEffect(() => onResolved(resolvedWindow), [onResolved, resolvedWindow]);
  const change = (patch: Partial<BookingWindowDraft>) => onChange({ ...value, ...patch });
  const snapTime = (name: "start" | "end") => {
    const timeKey = `${name}Time` as const;
    const occurrenceKey = `${name}Occurrence` as const;
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

  const endpointInvalid = (name: "start" | "end", endpointResolution: WallClockResolution | undefined) =>
    endpointResolution?.kind === "nonexistent" ||
    (endpointResolution?.kind === "ambiguous" && !value[`${name}Occurrence`]) ||
    (name === "end" && result.orderInvalid) ||
    policyInvalid;

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
    const endpointErrorId = `${fieldId}-${name}-error`;
    const dateId = `${fieldId}-${name}-date`;
    const timeId = `${fieldId}-${name}-time`;
    const describedBy =
      showErrors && endpointInvalid(name, endpointResolution) ? `${endpointErrorId} ${windowErrorId}` : undefined;
    return (
      <FieldSet>
        <FieldLegend>{t(`bookings.form.${name}`)}</FieldLegend>
        <div className={cn("grid sm:grid-cols-2", density === "compact" ? "gap-2" : "gap-4")}>
          <div className="space-y-2">
            <Label htmlFor={dateId}>{t("bookings.form.date")}</Label>
            <Input
              id={dateId}
              aria-label={t(`bookings.form.${name}Date`)}
              type="date"
              required
              aria-invalid={showErrors && endpointInvalid(name, endpointResolution) ? true : undefined}
              aria-describedby={describedBy}
              disabled={disabled}
              value={value[dateKey]}
              onChange={(event) => change({ [dateKey]: event.currentTarget.value, [occurrenceKey]: undefined })}
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
              aria-invalid={showErrors && endpointInvalid(name, endpointResolution) ? true : undefined}
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
    const endpointErrorId = `${fieldId}-${name}-error`;
    const describedBy =
      showErrors && endpointInvalid(name, endpointResolution) ? `${endpointErrorId} ${windowErrorId}` : undefined;
    return (
      <div className="min-w-0 space-y-2">
        <Label htmlFor={timeId}>{t(`bookings.form.${name}`)}</Label>
        <Input
          id={timeId}
          aria-label={t(`bookings.form.${name}Time`)}
          type="time"
          step={slotGranularityMinutes * 60}
          required
          aria-invalid={showErrors && endpointInvalid(name, endpointResolution) ? true : undefined}
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
      {result.orderInvalid && <FieldError>{t("bookings.errors.endAfterStart")}</FieldError>}
      {granularityInvalid && !allowPolicyMismatch && <FieldError>{t("bookings.errors.granularity")}</FieldError>}
      {openingInvalid && !allowPolicyMismatch && <FieldError>{t("bookings.errors.openingHours")}</FieldError>}
      {maximumDurationInvalid && !allowPolicyMismatch && (
        <FieldError>{t("bookings.errors.maximumDuration")}</FieldError>
      )}
    </div>
  ) : null;

  if (density === "compact") {
    const dateId = `${fieldId}-date`;
    const dateInvalid = endpointInvalid("start", result.start) || endpointInvalid("end", result.end);
    return (
      <div className="space-y-4">
        <div className="space-y-2">
          <Label htmlFor={dateId}>{t("bookings.form.date")}</Label>
          <Input
            id={dateId}
            aria-label={t("bookings.form.date")}
            type="date"
            required
            aria-invalid={showErrors && dateInvalid ? true : undefined}
            aria-describedby={showErrors && dateInvalid ? windowErrorId : undefined}
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
      {endpoint("start", result.start, value.startOccurrence)}
      {endpoint("end", result.end, value.endOccurrence)}
      {windowErrors}
    </div>
  );
}
