import { Link } from "@tanstack/react-router";
import { useTranslation } from "react-i18next";
import { BookingInstrumentTimeTooltip } from "@/modules/booking/components/BookingInstrumentTimeTooltip";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { formatBookingAgendaDateTime } from "@/modules/booking/domain/bookingAgenda";
import type { TableListCardField } from "@/modules/common/table-list/components/TableListCardView";
import { TableListCard } from "@/modules/common/table-list/components/TableListCardView";
import { buttonVariants } from "@/modules/common/ui/button";
import { InventoryItem } from "@/modules/common/ui/inventory-item";
import { UnknownItem } from "@/modules/common/ui/unknown-item";
import { cn } from "@/modules/common/utils/cn";

export type BookingSummaryCardProps = {
  booking: BookingListDocument;
  timeZone: string;
  variant?: "default" | "plain";
};

export function BookingSummaryCard({ booking, timeZone, variant = "default" }: BookingSummaryCardProps) {
  const { t, i18n } = useTranslation("booking");
  const { t: commonT } = useTranslation("common");
  const isFullPrivacy = booking.privacy === "full";
  const purpose = isFullPrivacy ? booking.purpose : null;
  const target = booking.target;
  const itemName = target?.value.name ?? commonT("values.unknownItem");
  const location = target?.value.parentContainerName;
  const canLinkToItem = isFullPrivacy && booking.canViewConfiguration && target !== null;
  const actionLabel = t("dashboard.agenda.viewDetailsFor", {
    name: itemName,
    id: booking.id,
  });
  const detailsLabel = t("dashboard.agenda.bookingDetails");
  const formatDateTime = (value: string) =>
    formatBookingAgendaDateTime(value, booking.start, booking.end, timeZone, i18n.resolvedLanguage ?? i18n.language);

  const fields: TableListCardField[] = [
    {
      id: "start",
      label: t("myBookings.fields.start"),
      value: (
        <BookingInstrumentTimeTooltip
          start={booking.start}
          displayTimeZone={timeZone}
          instrumentTimeZone={booking.timezone}
        >
          <time dateTime={booking.start}>{formatDateTime(booking.start)}</time>
        </BookingInstrumentTimeTooltip>
      ),
    },
    {
      id: "end",
      label: t("myBookings.fields.end"),
      value: (
        <BookingInstrumentTimeTooltip
          start={booking.end}
          displayTimeZone={timeZone}
          instrumentTimeZone={booking.timezone}
        >
          <time dateTime={booking.end}>{formatDateTime(booking.end)}</time>
        </BookingInstrumentTimeTooltip>
      ),
    },
    ...(location
      ? [
          {
            id: "location",
            label: t("allBookableItems.filters.location"),
            value: <span className="block whitespace-normal break-words">{location}</span>,
            fullWidth: true,
          },
        ]
      : []),
    ...(isFullPrivacy
      ? [
          {
            id: "purpose",
            label: booking.kind === "MAINTENANCE" ? t("bookings.form.notes") : t("myBookings.fields.purpose"),
            value: (
              <span title={purpose ?? undefined} className="block whitespace-normal break-words">
                {purpose || t("bookings.details.noneProvided")}
              </span>
            ),
            fullWidth: true,
          },
        ]
      : []),
  ];

  const item = {
    id: String(booking.id),
    title: target ? (
      <div className="min-w-0 space-y-1">
        <InventoryItem
          name={target.value.name}
          nameClassName="whitespace-normal overflow-visible break-words"
          globalId={target.globalId}
          href={canLinkToItem ? `/globalId/${target.globalId}` : undefined}
          idLinkLabel={
            canLinkToItem ? commonT("tableList.filters.openRecord", { globalId: target.globalId }) : undefined
          }
          idPlacement="title"
          size="xs"
          className="min-w-0 w-full items-start gap-2 border-0 p-0"
        />
      </div>
    ) : (
      <UnknownItem size="xs" className="min-w-0 border-0 p-0" />
    ),
    fields,
    actions: isFullPrivacy ? (
      <Link
        to="/booking/calendar/bookings/$id"
        params={{ id: String(booking.id) }}
        aria-label={actionLabel}
        className={cn(buttonVariants({ variant: "outline", size: "sm" }))}
        data-slot="button"
      >
        {detailsLabel}
      </Link>
    ) : null,
  };

  return (
    <div data-slot="booking-summary-card">
      <TableListCard item={item} variant={variant} />
    </div>
  );
}
