import { Link } from "@tanstack/react-router";
import { TriangleAlertIcon } from "lucide-react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { BookingInstrumentTimeTooltip } from "@/modules/booking/components/BookingInstrumentTimeTooltip";
import {
  BOOKING_ERROR_CLASS_NAME,
  BOOKING_WARNING_CLASS_NAME,
} from "@/modules/booking/components/bookingWarningStyles";
import type { BookingConflict } from "@/modules/booking/domain/availability";
import { useBookingTimeFormat } from "@/modules/booking/domain/bookingDisplayPreferences";
import { formatAgendaPeriod } from "@/modules/booking/domain/bookingTime";
import { Alert, AlertDescription } from "@/modules/common/ui/alert";

type BookingFormAlertsProps = {
  warning?: ReactNode;
  error?: ReactNode;
  conflicts?: readonly BookingConflict[];
  displayTimezone: string;
  conflictSeverity?: "warning" | "error";
  outcomeUncertain?: boolean;
};

export function BookingFormAlerts({
  warning,
  error,
  conflicts,
  displayTimezone,
  conflictSeverity = "error",
  outcomeUncertain,
}: BookingFormAlertsProps) {
  const { t } = useTranslation("booking");
  const timeFormat = useBookingTimeFormat();

  if (!warning && !error && !conflicts?.length) return null;

  const conflictList = (items: readonly BookingConflict[]) => (
    <ul className="mt-1 list-inside list-disc">
      {items.map((booking) => {
        const label =
          booking.purpose ??
          (booking.kind === "MAINTENANCE"
            ? t("bookings.errors.overlapMaintenance", { id: booking.id })
            : booking.privacy === "busy"
              ? t("bookings.errors.overlapReserved", { id: booking.id })
              : t("bookings.errors.overlapBooking", { id: booking.id }));
        return (
          <li key={booking.id}>
            <span className="font-medium">{label}</span>
            {" · "}
            <BookingInstrumentTimeTooltip
              start={booking.start}
              end={booking.end}
              displayTimeZone={displayTimezone}
              instrumentTimeZone={booking.instrumentTimeZone}
            >
              {formatAgendaPeriod(booking.start, booking.end, displayTimezone, undefined, timeFormat)}
            </BookingInstrumentTimeTooltip>
          </li>
        );
      })}
    </ul>
  );
  const overlaps = conflicts?.filter(({ bufferOnly }) => !bufferOnly) ?? [];
  const buffered = conflicts?.filter(({ bufferOnly }) => bufferOnly) ?? [];
  const conflictMessage = conflicts?.length ? (
    <>
      {overlaps.length ? (
        <>
          <p>{t("bookings.errors.overlapSummary")}</p>
          {conflictList(overlaps)}
        </>
      ) : null}
      {buffered.length ? (
        <>
          <p className={overlaps.length ? "mt-2" : undefined}>{t("bookings.errors.bufferSummary")}</p>
          {conflictList(buffered)}
        </>
      ) : null}
    </>
  ) : null;

  return (
    <div className="space-y-2">
      {warning ? (
        <Alert role="status" className={BOOKING_WARNING_CLASS_NAME}>
          <TriangleAlertIcon aria-hidden="true" />
          <AlertDescription>{warning}</AlertDescription>
        </Alert>
      ) : null}
      {error || conflicts?.length ? (
        <Alert
          variant={conflictSeverity === "warning" ? "default" : "destructive"}
          role={conflictSeverity === "warning" ? "status" : "alert"}
          className={conflictSeverity === "warning" ? BOOKING_WARNING_CLASS_NAME : BOOKING_ERROR_CLASS_NAME}
        >
          <TriangleAlertIcon aria-hidden="true" />
          <AlertDescription>
            {error}
            {conflictMessage}
            {outcomeUncertain ? (
              <p className="mt-2">
                {t("bookings.errors.outcomeUncertainGuidance")}{" "}
                <Link
                  className="font-medium underline underline-offset-4"
                  to="/booking/my-bookings"
                  search={{ period: "upcoming" }}
                >
                  {t("bookings.errors.checkExistingBookings")}
                </Link>
              </p>
            ) : null}
          </AlertDescription>
        </Alert>
      ) : null}
    </div>
  );
}
