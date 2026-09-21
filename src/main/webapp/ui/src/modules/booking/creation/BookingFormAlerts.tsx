import { Link } from "@tanstack/react-router";
import { TriangleAlertIcon } from "lucide-react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Alert, AlertDescription } from "@/modules/common/ui/alert";

type BookingFormAlertsProps = {
  warning?: ReactNode;
  error?: ReactNode;
  outcomeUncertain?: boolean;
};

export function BookingFormAlerts({ warning, error, outcomeUncertain }: BookingFormAlertsProps) {
  const { t } = useTranslation("booking");

  if (!warning && !error) return null;

  return (
    <div className="space-y-2">
      {warning ? (
        <Alert role="status">
          <TriangleAlertIcon aria-hidden="true" />
          <AlertDescription>{warning}</AlertDescription>
        </Alert>
      ) : null}
      {error ? (
        <Alert variant="destructive">
          <TriangleAlertIcon aria-hidden="true" />
          <AlertDescription>
            {error}
            {outcomeUncertain ? (
              <p className="mt-2">
                {t("bookings.errors.outcomeUncertainGuidance")}{" "}
                <Link
                  className="font-medium text-primary underline underline-offset-4"
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
