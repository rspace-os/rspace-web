import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { BookingInstrumentTimeTooltip } from "@/modules/booking/components/BookingInstrumentTimeTooltip";
import { bookingRelationshipSources } from "@/modules/booking/domain/bookingRelationshipSource";
import { bookingHourCycle } from "@/modules/booking/domain/bookingTime";
import type { CollectionConfig, CollectionTranslationKey } from "@/modules/common/collection/config";
import i18n from "@/modules/common/i18n";
import { Badge } from "@/modules/common/ui/badge";
import { InventoryItem } from "@/modules/common/ui/inventory-item";
import { UnknownItem } from "@/modules/common/ui/unknown-item";
import type { BookingListDocument } from "../../domain/booking";

function dateTime(value: BookingListDocument["start"], timeZone: string, instrumentTimeZone: string | null): ReactNode {
  return (
    <BookingInstrumentTimeTooltip start={value} displayTimeZone={timeZone} instrumentTimeZone={instrumentTimeZone}>
      <time dateTime={value}>
        {new Intl.DateTimeFormat(i18n.language, {
          dateStyle: "medium",
          timeStyle: "short",
          hourCycle: bookingHourCycle(),
          timeZone,
        }).format(new Date(value))}
      </time>
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

export function bookingListConfig(timeZone: string): CollectionConfig<BookingListDocument> {
  return {
    slug: "my-bookings",
    relationshipSources: bookingRelationshipSources,
    idField: "id",
    useAsTitle: "target",
    labels: {
      singularKey: "booking:myBookings.singular" satisfies CollectionTranslationKey,
      pluralKey: "booking:myBookings.plural" satisfies CollectionTranslationKey,
    },
    defaultColumns: ["target", "state", "start", "end", "purpose"],
    defaultSort: [
      { field: "start", direction: "asc" },
      { field: "id", direction: "asc" },
    ],
    listSearchableFields: ["target.name", "target.globalId", "purpose"],
    fields: [
      {
        name: "id",
        type: "number",
        labelKey: "booking:myBookings.fields.id" satisfies CollectionTranslationKey,
        list: false,
      },
      {
        name: "kind",
        type: "select",
        options: [
          {
            value: "BOOKING",
            get label() {
              return i18n.t("booking:bookings.form.typeBooking");
            },
          },
          {
            value: "MAINTENANCE",
            get label() {
              return i18n.t("booking:bookings.form.typeBlockout");
            },
          },
        ],
        labelKey: "booking:myBookings.fields.kind" satisfies CollectionTranslationKey,
        list: false,
      },
      {
        name: "target",
        type: "relationship",
        relationTo: "instruments",
        hasMany: false,
        labelKey: "booking:myBookings.fields.target" satisfies CollectionTranslationKey,
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
        options: [
          {
            value: "CONFIRMED",
            get label() {
              return i18n.t("booking:bookings.details.confirmed");
            },
          },
          {
            value: "CANCELLED",
            get label() {
              return i18n.t("booking:bookings.details.cancelled");
            },
          },
        ],
        labelKey: "booking:myBookings.fields.state" satisfies CollectionTranslationKey,
        list: { width: 120, renderCell: ({ row }) => <BookingStateBadge state={row.state} /> },
      },
      {
        name: "start",
        type: "dateTime",
        labelKey: "booking:myBookings.fields.start" satisfies CollectionTranslationKey,
        // Fits a medium date with a 12-hour time ("Sep 30, 2026, 10:00 AM"); the defaults total 1100px so the table fits the 1120px content column at 1440px.
        list: { width: 185, minWidth: 180, renderCell: ({ row }) => dateTime(row.start, timeZone, row.timezone) },
      },
      {
        name: "end",
        type: "dateTime",
        labelKey: "booking:myBookings.fields.end" satisfies CollectionTranslationKey,
        // Fits a medium date with a 12-hour time ("Sep 30, 2026, 10:00 AM"); the defaults total 1100px so the table fits the 1120px content column at 1440px.
        list: { width: 185, minWidth: 180, renderCell: ({ row }) => dateTime(row.end, timeZone, row.timezone) },
      },
      {
        name: "purpose",
        type: "text",
        labelKey: "booking:myBookings.fields.purpose" satisfies CollectionTranslationKey,
      },
      {
        name: "timezone",
        type: "text",
        labelKey: "booking:myBookings.fields.timezone" satisfies CollectionTranslationKey,
        list: false,
      },
    ],
  } satisfies CollectionConfig<BookingListDocument>;
}
