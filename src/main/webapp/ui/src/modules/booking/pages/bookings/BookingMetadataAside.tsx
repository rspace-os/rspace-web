import { useTranslation } from "react-i18next";
import type { BookingDetails } from "@/modules/booking/domain/booking";
import { formatBookingEventDateTime, Panel } from "./BookingEventContext";

export function BookingMetadataAside({
  booking,
  displayTimeZone,
}: {
  booking: BookingDetails;
  displayTimeZone: string;
}) {
  const { t, i18n } = useTranslation("booking");
  return (
    <Panel
      as="aside"
      heading={t(
        booking.kind === "MAINTENANCE" ? "bookings.details.aboutMaintenance" : "bookings.details.aboutBooking",
      )}
      headingId="booking-metadata-heading"
    >
      <dl data-slot="timestamps" className="space-y-3">
        <div>
          <dt className="text-muted-foreground">{t("bookings.details.timesShownIn")}</dt>
          <dd>{displayTimeZone}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">{t("bookings.details.created")}</dt>
          <dd>
            <time dateTime={booking.createdAt}>
              {formatBookingEventDateTime(booking.createdAt, displayTimeZone, i18n.language)}
            </time>
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">{t("bookings.details.lastUpdated")}</dt>
          <dd>
            <time dateTime={booking.updatedAt}>
              {formatBookingEventDateTime(booking.updatedAt, displayTimeZone, i18n.language)}
            </time>
          </dd>
        </div>
      </dl>
    </Panel>
  );
}
