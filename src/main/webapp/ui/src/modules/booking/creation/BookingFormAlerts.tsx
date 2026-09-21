import { Link } from "@tanstack/react-router";
import { TriangleAlertIcon } from "lucide-react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import type { BookingConflict } from "@/modules/booking/domain/availability";
import { formatAgendaPeriod } from "@/modules/booking/domain/bookingTime";
import { Alert, AlertDescription } from "@/modules/common/ui/alert";

type BookingFormAlertsProps = {
  warning?: ReactNode;
  error?: ReactNode;
  conflicts?: readonly BookingConflict[];
  conflictSeverity?: "warning" | "error";
  outcomeUncertain?: boolean;
};

export function BookingFormAlerts({
  warning,
  error,
  conflicts,
  conflictSeverity = "error",
  outcomeUncertain,
}: BookingFormAlertsProps) {
  const { t, i18n } = useTranslation("booking");

  if (!warning && !error && !conflicts?.length) return null;

  const conflictMessage = conflicts?.length ? (
    <>
      <p>{t("bookings.errors.overlapSummary")}</p>
      <ul className="mt-1 list-inside list-disc">
        {conflicts.map((booking) => {
          const label =
            booking.purpose ??
            t(
              booking.kind === "MAINTENANCE"
                ? "bookings.errors.overlapMaintenance"
                : booking.privacy === "busy"
                  ? "bookings.errors.overlapReserved"
                  : "bookings.errors.overlapBooking",
              { id: booking.id },
            );
          return (
            <li key={booking.id}>
              <span className="font-medium">{label}</span>
              {` · ${formatAgendaPeriod(booking.start, booking.end, booking.timezone, i18n.language)}`}
            </li>
          );
        })}
      </ul>
    </>
  ) : null;

  return (
    <div className="space-y-2">
      {warning ? (
        <Alert
          role="status"
          className="border-amber-600 bg-amber-100 text-amber-950 *:data-[slot=alert-description]:text-amber-950 dark:border-amber-400 dark:bg-amber-950 dark:text-amber-200 dark:*:data-[slot=alert-description]:text-amber-200"
        >
          <TriangleAlertIcon aria-hidden="true" />
          <AlertDescription>{warning}</AlertDescription>
        </Alert>
      ) : null}
      {error || conflicts?.length ? (
        <Alert
          variant={conflictSeverity === "warning" ? "default" : "destructive"}
          role={conflictSeverity === "warning" ? "status" : "alert"}
          className={
            conflictSeverity === "warning"
              ? "border-amber-600 bg-amber-100 text-amber-950 *:data-[slot=alert-description]:text-amber-950 dark:border-amber-400 dark:bg-amber-950 dark:text-amber-200 dark:*:data-[slot=alert-description]:text-amber-200"
              : "border-red-700 bg-red-100 text-red-950 *:data-[slot=alert-description]:text-red-950 dark:border-red-400 dark:bg-red-950 dark:text-red-200 dark:*:data-[slot=alert-description]:text-red-200"
          }
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
