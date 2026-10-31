import { Link } from "@tanstack/react-router";
import { PencilIcon } from "lucide-react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { BookingInstrumentTimeTooltip } from "@/modules/booking/components/BookingInstrumentTimeTooltip";
import { ClockChangeWarning } from "@/modules/booking/components/ClockChangeWarning";
import { useBookingTimeFormat } from "@/modules/booking/domain/bookingDisplayPreferences";
import { formatBookingDateTime } from "@/modules/booking/domain/bookingTime";
import {
  RESPONSIVE_INLINE_FIELD_CONTAINER_CLASS_NAME,
  RESPONSIVE_INLINE_FIELD_GRID_CLASS_NAME,
  RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME,
} from "@/modules/common/collection-form/responsiveFieldLayout";
import TransRichText from "@/modules/common/i18n/TransRichText";
import { buttonVariants } from "@/modules/common/ui/button";
import { UserBadge } from "@/modules/common/ui/user-badge";
import { Panel, useBookingEvent } from "./BookingEventContext";

type BookingFact = [label: string, value: ReactNode];

export function BookingDetailsView() {
  const { t, i18n } = useTranslation("booking");
  const timeFormat = useBookingTimeFormat();
  const { booking, displayTimeZone, editButtonRef } = useBookingEvent();
  const durationMinutes = Math.max(0, Math.round((Date.parse(booking.end) - Date.parse(booking.start)) / 60000));
  const facts: BookingFact[] = [
    [
      t("bookings.details.when"),
      <span key="when">
        <TransRichText
          i18nKey="booking:bookings.details.periodWithDuration"
          values={{
            start: formatBookingDateTime(booking.start, displayTimeZone, i18n.language, timeFormat),
            end: formatBookingDateTime(booking.end, displayTimeZone, i18n.language, timeFormat),
            duration: t("bookableItemDetails.minutes", { count: durationMinutes }),
          }}
          components={{
            period: (
              <BookingInstrumentTimeTooltip
                start={booking.start}
                end={booking.end}
                displayTimeZone={displayTimeZone}
                instrumentTimeZone={booking.timezone}
              />
            ),
            periodText: <span />,
            startTime: <time dateTime={booking.start} />,
            endTime: <time dateTime={booking.end} />,
            duration: <span className="text-muted-foreground" />,
          }}
        />
      </span>,
    ],
  ];
  if (booking.kind === "BOOKING" && booking.bookedBy) {
    facts.push([t("bookings.details.bookedBy"), <UserBadge key="booked-by" name={booking.bookedBy} />]);
  }
  if (booking.createdBy) {
    facts.push([t("bookings.details.createdBy"), <UserBadge key="created-by" name={booking.createdBy} />]);
  }
  facts.push([
    booking.kind === "MAINTENANCE" ? t("bookings.form.notes") : t("bookings.form.purpose"),
    booking.purpose ? (
      <span key="purpose" className="whitespace-pre-line">
        {booking.purpose}
      </span>
    ) : (
      <span key="purpose" className="text-muted-foreground">
        {t("bookings.details.noneProvided")}
      </span>
    ),
  ]);
  if (booking.state === "CANCELLED" && booking.cancellationReason) {
    facts.push([
      t("bookings.details.cancellationReason"),
      <span key="cancellation-reason" className="whitespace-pre-line">
        {booking.cancellationReason}
      </span>,
    ]);
  }

  return (
    <Panel
      heading={booking.kind === "MAINTENANCE" ? t("bookings.details.maintenanceTitle") : t("bookings.details.title")}
      headingId="booking-details-heading"
      action={
        booking.canEdit && booking.state === "CONFIRMED" && booking.target && booking.timezone ? (
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
      <div className={`${RESPONSIVE_INLINE_FIELD_CONTAINER_CLASS_NAME} space-y-4`}>
        {/* Derived from the event's instants and its visible scheduling timezone; a restricted event shows none. */}
        <ClockChangeWarning start={booking.start} end={booking.end} timezone={booking.timezone} kind={booking.kind} />
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
