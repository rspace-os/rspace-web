import type { ReactNode } from "react";
import type { CollectionConfig } from "@/modules/common/collection/collectionConfig";
import i18n from "@/modules/common/i18n";
import { InventoryItem } from "@/modules/common/ui/inventory-item";
import type { BookingListDocument } from "../../domain/booking";

function dateTime(value: BookingListDocument["start"], timeZone: string): ReactNode {
  return (
    <time dateTime={value}>
      {new Intl.DateTimeFormat(i18n.language, { dateStyle: "medium", timeStyle: "short", timeZone }).format(
        new Date(value),
      )}
    </time>
  );
}

export function bookingListConfig(timeZone: string): CollectionConfig<BookingListDocument> {
  return {
    slug: "my-bookings",
    idField: "id",
    useAsTitle: "target",
    labels: {
      singularKey: "booking:myBookings.singular",
      pluralKey: "booking:myBookings.plural",
    },
    defaultColumns: ["target", "start", "end", "purpose"],
    defaultSort: [
      { field: "start", direction: "asc" },
      { field: "id", direction: "asc" },
    ],
    listSearchableFields: ["target.name"],
    fields: [
      { name: "id", type: "number", labelKey: "booking:myBookings.fields.id", list: false },
      {
        name: "target",
        type: "relationship",
        relationTo: "instruments",
        hasMany: false,
        labelKey: "booking:myBookings.fields.target",
        list: {
          renderCell: ({ row }) => (
            <InventoryItem
              name={row.target.value.name}
              globalId={row.target.globalId}
              href={`/globalId/${row.target.globalId}`}
              idLinkLabel={i18n.t("common:tableList.filters.openRecord", { globalId: row.target.globalId })}
              compact
              size="xs"
            />
          ),
        },
      },
      {
        name: "start",
        type: "dateTime",
        labelKey: "booking:myBookings.fields.start",
        list: { renderCell: ({ row }) => dateTime(row.start, timeZone) },
      },
      {
        name: "end",
        type: "dateTime",
        labelKey: "booking:myBookings.fields.end",
        list: { renderCell: ({ row }) => dateTime(row.end, timeZone) },
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
