import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { BookingInstrumentTimeTooltip } from "@/modules/booking/components/BookingInstrumentTimeTooltip";
import { bookingRelationshipSources } from "@/modules/booking/domain/bookingRelationshipSource";
import { type BookingTimeFormat, formatBookingDateTime } from "@/modules/booking/domain/bookingTime";
import type { CollectionConfig } from "@/modules/common/collection/collectionConfig";
import i18n from "@/modules/common/i18n";
import { Badge } from "@/modules/common/ui/badge";
import { InventoryItem } from "@/modules/common/ui/inventory-item";
import { UnknownItem } from "@/modules/common/ui/unknown-item";
import type { BookingListDocument } from "../../domain/booking";

function dateTime(
  value: BookingListDocument["start"],
  timeZone: string,
  instrumentTimeZone: string | null,
  timeFormat: BookingTimeFormat,
): ReactNode {
  return (
    <BookingInstrumentTimeTooltip start={value} displayTimeZone={timeZone} instrumentTimeZone={instrumentTimeZone}>
      {/* On a clock-change day the offset tells the two occurrences of a repeated hour apart. */}
      <time dateTime={value}>{formatBookingDateTime(value, timeZone, i18n.language, timeFormat)}</time>
    </BookingInstrumentTimeTooltip>
  );
}

function BookingStateBadge({ state }: { state: BookingListDocument["state"] }) {
  const { t } = useTranslation("booking");
  return state === "CANCELLED" ? (
    <Badge variant="outline" className="text-muted-foreground">
      {t("bookings.details.cancelled")}
    </Badge>
  ) : (
    <Badge variant="secondary">{t("bookings.details.confirmed")}</Badge>
  );
}

export function bookingListConfig(
  timeZone: string,
  timeFormat: BookingTimeFormat = "AUTOMATIC",
): CollectionConfig<BookingListDocument> {
  return {
    slug: "my-bookings",
    relationshipSources: bookingRelationshipSources,
    idField: "id",
    useAsTitle: "target",
    labels: {
      singularKey: "booking:myBookings.singular",
      pluralKey: "booking:myBookings.plural",
    },
    defaultColumns: ["target", "state", "start", "end", "purpose"],
    defaultSort: [
      { field: "start", direction: "asc" },
      { field: "id", direction: "asc" },
    ],
    listSearchableFields: ["target.name", "target.globalId", "purpose"],
    fields: [
      { name: "id", type: "number", labelKey: "booking:myBookings.fields.id", list: false },
      {
        name: "kind",
        type: "select",
        options: ["BOOKING", "MAINTENANCE"],
        labelKey: "booking:myBookings.fields.kind",
        list: false,
      },
      {
        name: "target",
        type: "relationship",
        relationTo: "booking-instruments",
        hasMany: false,
        labelKey: "booking:myBookings.fields.target",
        list: {
          // The item name identifies the row, so it gets the width the other columns can spare.
          width: 260,
          minWidth: 200,
          renderCell: ({ row }) =>
            row.target ? (
              <InventoryItem
                name={row.target.value.name}
                nameTitle={row.target.value.name}
                globalId={row.target.globalId}
                href={row.canViewConfiguration ? `/globalId/${row.target.globalId}` : undefined}
                idLinkLabel={
                  row.canViewConfiguration
                    ? i18n.t("common:tableList.filters.openRecord", { globalId: row.target.globalId })
                    : undefined
                }
                compact
                compactIdPlacement="below"
                size="xs"
                className={row.state === "CANCELLED" ? "text-muted-foreground" : undefined}
              />
            ) : (
              <UnknownItem size="xs" />
            ),
        },
      },
      {
        name: "state",
        type: "select",
        options: ["CONFIRMED", "CANCELLED"],
        labelKey: "booking:myBookings.fields.state",
        list: { width: 120, renderCell: ({ row }) => <BookingStateBadge state={row.state} /> },
      },
      {
        name: "start",
        type: "dateTime",
        labelKey: "booking:myBookings.fields.start",
        // Fits a medium date with a 12-hour time ("Sep 30, 2026, 10:00 AM"); the defaults total 1100px so the table fits the 1120px content column at 1440px.
        list: {
          width: 185,
          minWidth: 180,
          renderCell: ({ row }) => dateTime(row.start, timeZone, row.timezone, timeFormat),
        },
      },
      {
        name: "end",
        type: "dateTime",
        labelKey: "booking:myBookings.fields.end",
        // Fits a medium date with a 12-hour time ("Sep 30, 2026, 10:00 AM"); the defaults total 1100px so the table fits the 1120px content column at 1440px.
        list: {
          width: 185,
          minWidth: 180,
          renderCell: ({ row }) => dateTime(row.end, timeZone, row.timezone, timeFormat),
        },
      },
      {
        name: "purpose",
        type: "text",
        labelKey: "booking:myBookings.fields.purpose",
      },
      { name: "timezone", type: "text", labelKey: "booking:myBookings.fields.timezone", list: false },
    ],
  } satisfies CollectionConfig<BookingListDocument>;
}
