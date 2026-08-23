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
  timezone: string,
): { window?: ResolvedBookingWindow; start?: WallClockResolution; end?: WallClockResolution; orderInvalid: boolean } {
  const start = resolution(draft.startDate, draft.startTime, timezone);
  const end = resolution(draft.endDate, draft.endTime, timezone);
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
  timezone,
  value,
  onChange,
  onResolved,
  disabled = false,
}: {
  timezone: string;
  value: BookingWindowDraft;
  onChange: (value: BookingWindowDraft) => void;
  onResolved: (value: ResolvedBookingWindow | undefined) => void;
  disabled?: boolean;
}) {
  const { t } = useTranslation("booking");
  const result = useMemo(() => resolveBookingWindow(value, timezone), [value, timezone]);
  useEffect(() => onResolved(result.window), [onResolved, result.window]);
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
        <div className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-2">
            <Label htmlFor={`booking-${name}-date`}>{t("bookings.form.date")}</Label>
            <Input
              id={`booking-${name}-date`}
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
              type="time"
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
                {t(`bookings.form.${choice}Occurrence`, { offset: offset(endpointResolution[choice], timezone) })}
              </Label>
            ))}
            {!occurrence && <FieldError>{t("bookings.errors.occurrenceRequired")}</FieldError>}
          </fieldset>
        )}
      </FieldSet>
    );
  };

  return (
    <div className="space-y-6">
      <p className="text-sm text-muted-foreground">{t("bookings.form.timezone", { timezone })}</p>
      {endpoint("start", result.start, value.startOccurrence)}
      {endpoint("end", result.end, value.endOccurrence)}
      {result.orderInvalid && <FieldError>{t("bookings.errors.endAfterStart")}</FieldError>}
    </div>
  );
}
