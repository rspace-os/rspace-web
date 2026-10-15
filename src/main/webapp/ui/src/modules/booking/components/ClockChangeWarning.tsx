import { Temporal } from "@js-temporal/polyfill";
import { ClockAlertIcon } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { BOOKING_WARNING_CLASS_NAME } from "@/modules/booking/components/bookingWarningStyles";
import { clockChangeCrossing } from "@/modules/booking/domain/bookingClockChange";
import { useBookingTimeFormat } from "@/modules/booking/domain/bookingDisplayPreferences";
import {
  bookingDateTimeLocale,
  bookingLocale,
  formatBookingDateTime,
  formatDurationMinutes,
} from "@/modules/booking/domain/bookingTime";
import { Alert, AlertDescription } from "@/modules/common/ui/alert";

/**
 * A non-blocking note when an event's interval crosses a clock change in its scheduling timezone. It never replaces
 * a validation error and is derived from the event itself, so it needs no stored flag. Without the scheduling
 * timezone, as on a restricted event, it shows nothing rather than guessing a zone.
 */
export function ClockChangeWarning({
  start,
  end,
  timezone,
  kind,
}: {
  start: string | undefined;
  end: string | undefined;
  timezone: string | null | undefined;
  kind: "BOOKING" | "MAINTENANCE";
}) {
  const { t } = useTranslation("booking");
  const timeFormat = useBookingTimeFormat();
  const crossing = useMemo(
    () => (start && end && timezone ? clockChangeCrossing(start, end, timezone) : undefined),
    [start, end, timezone],
  );
  let message: string | undefined;
  if (crossing && start && end) {
    const locale = bookingLocale();
    const zonedStart = Temporal.Instant.from(start).toZonedDateTimeISO(crossing.timezone);
    const zonedEnd = Temporal.Instant.from(end).toZonedDateTimeISO(crossing.timezone);
    const values = {
      kind: kind === "MAINTENANCE" ? "maintenance" : "booking",
      timezone: crossing.timezone,
      duration: formatDurationMinutes(crossing.elapsedMinutes, locale, { largestUnit: "hour" }),
    };
    const count = crossing.changes.length;
    // Same local time on a later date reads as calendar days; anything else as its local range.
    const calendarDaySpan =
      kind === "BOOKING" &&
      crossing.calendarDays > 0 &&
      Temporal.PlainTime.compare(zonedStart.toPlainTime(), zonedEnd.toPlainTime()) === 0;
    if (calendarDaySpan) {
      const days = { ...values, days: crossing.calendarDays };
      message = crossing.daylightSaving
        ? t("bookings.clockChange.daylightSavingDays", days)
        : t("bookings.clockChange.changesDays", { ...days, count });
    } else {
      const dateTimeLocale = bookingDateTimeLocale(timeFormat);
      const range = {
        ...values,
        start: formatBookingDateTime(start, crossing.timezone, dateTimeLocale, timeFormat),
        end: formatBookingDateTime(end, crossing.timezone, dateTimeLocale, timeFormat),
      };
      message = crossing.daylightSaving
        ? t("bookings.clockChange.daylightSavingRange", range)
        : t("bookings.clockChange.changesRange", { ...range, count });
    }
  }
  // A live region announces changes, not its first content, so the text is set after mount and again for each new
  // interval; an unchanged sentence for a different interval is varied by an invisible space so it is announced.
  // Wording that changes for the same interval, such as after a time-format or language change, is resynchronised.
  const announcementKey = `${start}|${end}|${timezone}|${kind}|${message ?? ""}`;
  const announced = useRef<string>(undefined);
  const [announcement, setAnnouncement] = useState("");
  useEffect(() => {
    if (announced.current === announcementKey) return;
    announced.current = announcementKey;
    setAnnouncement((previous) => (!message ? "" : previous === message ? `${message}\u00a0` : message));
  }, [announcementKey, message]);
  // The note is visible only when needed; the visually hidden live region stays mounted and reserves no space.
  return (
    <>
      {message ? (
        <Alert role="note" className={BOOKING_WARNING_CLASS_NAME}>
          <ClockAlertIcon aria-hidden="true" />
          <AlertDescription>{message}</AlertDescription>
        </Alert>
      ) : null}
      <p className="sr-only" aria-live="polite" aria-atomic="true">
        {announcement}
      </p>
    </>
  );
}
