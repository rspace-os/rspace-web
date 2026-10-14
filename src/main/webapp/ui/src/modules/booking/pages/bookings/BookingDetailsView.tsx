import { Link } from "@tanstack/react-router";
import { PencilIcon } from "lucide-react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { BookingInstrumentTimeTooltip } from "@/modules/booking/components/BookingInstrumentTimeTooltip";
import {
  RESPONSIVE_INLINE_FIELD_CONTAINER_CLASS_NAME,
  RESPONSIVE_INLINE_FIELD_GRID_CLASS_NAME,
  RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME,
} from "@/modules/common/collection-form/responsiveFieldLayout";
import { buttonVariants } from "@/modules/common/ui/button";
import { UserBadge } from "@/modules/common/ui/user-badge";
import { formatBookingEventDateTime, Panel, useBookingEvent } from "./BookingEventContext";

export function BookingDetailsView() {
  const { t, i18n } = useTranslation("booking");
  const { booking, displayTimeZone, editButtonRef } = useBookingEvent();
  const durationMinutes = Math.max(0, Math.round((Date.parse(booking.end) - Date.parse(booking.start)) / 60000));
  const facts: Array<[string, ReactNode]> = [
    [
      t("bookings.details.when"),
      <span key="when">
        <BookingInstrumentTimeTooltip
          start={booking.start}
          end={booking.end}
          displayTimeZone={displayTimeZone}
          instrumentTimeZone={booking.timezone}
        >
          <span>
            <time dateTime={booking.start}>
              {formatBookingEventDateTime(booking.start, displayTimeZone, i18n.language)}
            </time>
            {" – "}
            <time dateTime={booking.end}>
              {formatBookingEventDateTime(booking.end, displayTimeZone, i18n.language)}
            </time>
          </span>
        </BookingInstrumentTimeTooltip>
        <span className="text-muted-foreground">{` · ${t("bookableItemDetails.minutes", { count: durationMinutes })}`}</span>
      </span>,
    ],
    ...(booking.kind === "BOOKING" && booking.bookedBy
      ? ([[t("bookings.details.bookedBy"), <UserBadge key="booked-by" name={booking.bookedBy} />]] as Array<
          [string, ReactNode]
        >)
      : []),
    ...(booking.createdBy
      ? ([[t("bookings.details.createdBy"), <UserBadge key="created-by" name={booking.createdBy} />]] as Array<
          [string, ReactNode]
        >)
      : []),
    [
      t(booking.kind === "MAINTENANCE" ? "bookings.form.notes" : "bookings.form.purpose"),
      booking.purpose ? (
        <span key="purpose" className="whitespace-pre-line">
          {booking.purpose}
        </span>
      ) : (
        <span key="purpose" className="text-muted-foreground">
          {t("bookings.details.noneProvided")}
        </span>
      ),
    ],
  ];

  return (
    <Panel
      heading={t(booking.kind === "MAINTENANCE" ? "bookings.details.maintenanceTitle" : "bookings.details.title")}
      headingId="booking-details-heading"
      action={
        booking.canEdit && booking.state === "CONFIRMED" ? (
          <Link
            ref={editButtonRef}
            className={buttonVariants({ size: "xs", variant: "ghost" })}
            to="/booking/calendar/bookings/$id/edit"
            params={{ id: String(booking.id) }}
            replace
            resetScroll={false}
          >
            <PencilIcon aria-hidden="true" />
            {t("bookings.actions.edit")}
          </Link>
        ) : null
      }
    >
      <div className={RESPONSIVE_INLINE_FIELD_CONTAINER_CLASS_NAME}>
        <dl className={`${RESPONSIVE_INLINE_FIELD_GRID_CLASS_NAME} gap-y-4`}>
          {facts.map(([label, value]) => (
            <div className={RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME} key={label}>
              <dt className="font-medium">{label}</dt>
              <dd className="min-w-0">{value}</dd>
            </div>
          ))}
        </dl>
      </div>
    </Panel>
  );
}
