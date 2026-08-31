import { Temporal } from "@js-temporal/polyfill";
import { useEffect, useMemo } from "react";
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
  value,
  onChange,
  onResolved,
  allowPolicyMismatch = false,
  disabled = false,
  density = "comfortable",
}: {
  displayTimezone?: string;
  schedulingTimezone?: string;
  /** @deprecated Pass displayTimezone and schedulingTimezone separately. */
  timezone?: string;
  slotGranularityMinutes: number;
  maxBookingDurationMinutes: number;
  openingStart: string;
  openingEnd: string;
  value: BookingWindowDraft;
  onChange: (value: BookingWindowDraft) => void;
  onResolved: (value: ResolvedBookingWindow | undefined) => void;
  allowPolicyMismatch?: boolean;
  disabled?: boolean;
  density?: "comfortable" | "compact";
}) {
  const { t } = useTranslation("booking");
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

  const endpoint = (
    name: "start" | "end",
    endpointResolution: WallClockResolution | undefined,
    occurrence: "earlier" | "later" | undefined,
  ) => {
    const dateKey = `${name}Date` as const;
    const timeKey = `${name}Time` as const;
    const occurrenceKey = `${name}Occurrence` as const;
    return (
      <FieldSet>
        <FieldLegend>{t(`bookings.form.${name}`)}</FieldLegend>
        <div className={cn("grid sm:grid-cols-2", density === "compact" ? "gap-2" : "gap-4")}>
          <div className="space-y-2">
            <Label htmlFor={`booking-${name}-date`}>{t("bookings.form.date")}</Label>
            <Input
              id={`booking-${name}-date`}
              aria-label={t(`bookings.form.${name}Date`)}
              type="date"
              required
              disabled={disabled}
              value={value[dateKey]}
              onChange={(event) => change({ [dateKey]: event.currentTarget.value, [occurrenceKey]: undefined })}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor={`booking-${name}-time`}>{t("bookings.form.time")}</Label>
            <Input
              id={`booking-${name}-time`}
              aria-label={t(`bookings.form.${name}Time`)}
              type="time"
              step={slotGranularityMinutes * 60}
              required
              disabled={disabled}
              value={value[timeKey]}
              onChange={(event) => change({ [timeKey]: event.currentTarget.value, [occurrenceKey]: undefined })}
            />
          </div>
        </div>
        {endpointResolution?.kind === "nonexistent" && <FieldError>{t("bookings.errors.nonexistentTime")}</FieldError>}
        {endpointResolution?.kind === "ambiguous" && (
          <fieldset className="space-y-2">
            <legend className="text-sm font-medium">{t("bookings.form.occurrence")}</legend>
            {(["earlier", "later"] as const).map((choice) => (
              <Label key={choice} className="flex items-center gap-2">
                <input
                  type="radio"
                  name={`booking-${name}-occurrence`}
                  disabled={disabled}
                  checked={occurrence === choice}
                  onChange={() => change({ [occurrenceKey]: choice })}
                />
                {t(`bookings.form.${choice}Occurrence`, {
                  offset: offset(endpointResolution[choice], resolvedDisplayTimezone),
                })}
              </Label>
            ))}
            {!occurrence && <FieldError>{t("bookings.errors.occurrenceRequired")}</FieldError>}
          </fieldset>
        )}
      </FieldSet>
    );
  };

  return (
    <div className={density === "compact" ? "space-y-4" : "space-y-6"}>
      <p className="text-sm text-muted-foreground">
        {t("bookings.form.timezone", { timezone: resolvedDisplayTimezone })}
      </p>
      {resolvedDisplayTimezone === resolvedSchedulingTimezone ? null : (
        <p className="text-sm text-muted-foreground">
          {t("bookings.form.schedulingTimezone", { timezone: resolvedSchedulingTimezone })}
        </p>
      )}
      {endpoint("start", result.start, value.startOccurrence)}
      {endpoint("end", result.end, value.endOccurrence)}
      {result.orderInvalid && <FieldError>{t("bookings.errors.endAfterStart")}</FieldError>}
      {granularityInvalid && !allowPolicyMismatch && <FieldError>{t("bookings.errors.granularity")}</FieldError>}
      {openingInvalid && !allowPolicyMismatch && <FieldError>{t("bookings.errors.openingHours")}</FieldError>}
      {maximumDurationInvalid && !allowPolicyMismatch && (
        <FieldError>{t("bookings.errors.maximumDuration")}</FieldError>
      )}
    </div>
  );
}
