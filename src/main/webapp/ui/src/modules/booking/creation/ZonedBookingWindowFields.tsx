import { Temporal } from "@js-temporal/polyfill";
import { useId, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { formatBookingLength, formatIncrement } from "@/modules/booking/configuration/incrementFacts";
import {
  type IncrementSettings,
  type IncrementViolation,
  snapEndToIncrement,
} from "@/modules/booking/domain/bookingIncrement";
import type { OpeningHoursEntry } from "@/modules/booking/domain/bookingOpeningHours";
import type { BookingWindowDraft, WallClockResolution } from "@/modules/booking/domain/bookingTime";
import {
  formatPlainDate,
  formatWallClockTime,
  wallClockEndpointFromInstant,
  wallClockInstant,
} from "@/modules/booking/domain/bookingTime";
import i18n from "@/modules/common/i18n";
import { FieldError, FieldLegend, FieldSet } from "@/modules/common/ui/field";
import { Input } from "@/modules/common/ui/input";
import { Label } from "@/modules/common/ui/label";
import { cn } from "@/modules/common/utils/cn";
import { resolveBookingWindow, validateBookingWindow } from "./bookingWindowValidation";

export {
  type BookingWindowPolicy,
  type ResolvedBookingWindow,
  resolveBookingWindow,
  validateBookingWindow,
} from "./bookingWindowValidation";

function offset(instant: string, timezone: string): string {
  return Temporal.Instant.from(instant).toZonedDateTimeISO(timezone).offset;
}

export function ZonedBookingWindowFields({
  displayTimezone,
  schedulingTimezone,
  slotGranularityMinutes,
  incrementUnit = "MINUTES",
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
  incrementUnit?: IncrementSettings["incrementUnit"];
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
        incrementUnit,
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
      incrementUnit,
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
  const locale = i18n.resolvedLanguage ?? i18n.language ?? "en-US";
  const zones = { start: resolvedDisplayTimezone, end: resolvedDisplayTimezone };
  const change = (patch: Partial<BookingWindowDraft>) => onChange({ ...value, ...patch });
  const [snaps, setSnaps] = useState<Partial<Record<"start" | "end", { date: string; time: string; note: string }>>>(
    {},
  );
  const changeTime = (name: "start" | "end", time: string) => {
    const timeKey = `${name}Time` as const;
    const occurrenceKey = `${name}Occurrence` as const;
    setSnaps((current) => ({ ...current, [name]: undefined }));
    change({ [timeKey]: time, [occurrenceKey]: undefined });
  };
  // A repeated time is snapped only once the user has chosen its occurrence; until then the form asks for it.
  const typedInstant = (name: "start" | "end") => wallClockInstant(result[name], value[`${name}Occurrence`]);
  const applySnap = (name: "start" | "end", instant: string, note: (time: string, date: string) => string) => {
    const draft = wallClockEndpointFromInstant(instant, zones[name]);
    // A snap onto a repeated time keeps an occurrence only if the user chose it for this very time; a choice made
    // for the typed time does not carry over to a different one.
    const sameTime = draft.date === value[`${name}Date`] && draft.time === value[`${name}Time`];
    const next = sameTime && value[`${name}Occurrence`] ? draft : { ...draft, occurrence: undefined };
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
  const endSnapNote = (time: string, date: string) => t("bookings.form.endSnapped", { time, date, increment });
  const snapEnd = () => {
    if (originalEndpoint("end")) return;
    const start = wallClockInstant(result.start, value.startOccurrence);
    const end = typedInstant("end");
    if (!start || !end) return;
    const snap = snapEndToIncrement(rule, start, end, resolvedSchedulingTimezone);
    if (snap.kind === "instant") applySnap("end", snap.instant, endSnapNote);
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
              aria-invalid={
                showErrors && (!value[dateKey] || endpointInvalid(name, endpointResolution)) ? true : undefined
              }
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
              step={60}
              required
              aria-invalid={
                showErrors && (!endpointResolution || endpointInvalid(name, endpointResolution)) ? true : undefined
              }
              aria-describedby={describedBy}
              disabled={disabled}
              value={value[timeKey]}
              onChange={(event) => changeTime(name, event.currentTarget.value)}
              onBlur={name === "end" ? snapEnd : undefined}
            />
          </div>
        </div>
        {snapNote(name)}
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
    const timeId = `${fieldId}-${name}-time`;
    const describedBy = errorDescription(name, endpointResolution);
    return (
      <div className="min-w-0 space-y-2">
        <Label htmlFor={timeId}>{t(`bookings.form.${name}`)}</Label>
        <Input
          id={timeId}
          aria-label={t(`bookings.form.${name}Time`)}
          type="time"
          step={60}
          required
          aria-invalid={
            showErrors && (!endpointResolution || endpointInvalid(name, endpointResolution)) ? true : undefined
          }
          aria-describedby={describedBy}
          disabled={disabled}
          value={value[timeKey]}
          onChange={(event) => changeTime(name, event.currentTarget.value)}
          onBlur={name === "end" ? snapEnd : undefined}
        />
        {snapNote(name)}
        {endpointError(name, endpointResolution)}
        {occurrenceFields(name, endpointResolution, occurrence)}
      </div>
    );
  };

  const incrementError = (current: IncrementViolation | null) => {
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
      const resolved = resolveBookingWindow(value, resolvedDisplayTimezone).window;
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
        <FieldError>{t("bookings.errors.maximumDuration")}</FieldError>
      )}
    </div>
  ) : null;

  if (density === "compact" && value.startDate === value.endDate && incrementUnit !== "DAYS") {
    const dateId = `${fieldId}-date`;
    const dateInvalid = !value.startDate || Boolean(startErrorId);
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
