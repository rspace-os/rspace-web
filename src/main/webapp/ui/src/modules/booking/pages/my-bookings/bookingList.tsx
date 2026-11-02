import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { BookableItemName } from "@/modules/booking/components/BookableItemName";
import { BookingInstrumentTimeTooltip } from "@/modules/booking/components/BookingInstrumentTimeTooltip";
import { bookingRelationshipSources } from "@/modules/booking/domain/bookingRelationshipSource";
import { type BookingTimeFormat, formatBookingDateTime } from "@/modules/booking/domain/bookingTime";
import type { CollectionConfig, CollectionTranslationKey } from "@/modules/common/collection/config";
import i18n from "@/modules/common/i18n";
import { Badge } from "@/modules/common/ui/badge";
import { InventoryItem } from "@/modules/common/ui/inventory-item";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/modules/common/ui/tooltip";
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
      <time className="whitespace-normal" dateTime={value}>
        {formatBookingDateTime(value, timeZone, undefined, timeFormat)}
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

export function bookingListConfig(
  timeZone: string,
  timeFormat: BookingTimeFormat,
): CollectionConfig<BookingListDocument> {
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
          width: 200,
          minWidth: 200,
          renderCell: ({ row }) =>
            row.target ? (
              <InventoryItem
                name={
                  <Tooltip>
                    <TooltipTrigger
                      render={row.canViewConfiguration ? <span /> : <button type="button" className="text-left" />}
                    >
                      {row.canViewConfiguration ? (
                        <BookableItemName name={row.target.value.name} globalId={row.target.globalId} />
                      ) : (
                        row.target.value.name
                      )}
                    </TooltipTrigger>
                    <TooltipContent role="tooltip">{row.target.value.name}</TooltipContent>
                  </Tooltip>
                }
                nameClassName="whitespace-normal [&_a]:line-clamp-2 [&_button]:line-clamp-2"
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
                className={`min-w-48 ${row.state === "CANCELLED" ? "text-muted-foreground" : ""}`}
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
        list: { width: 100, renderCell: ({ row }) => <BookingStateBadge state={row.state} /> },
      },
      {
        name: "start",
        type: "dateTime",
        labelKey: "booking:myBookings.fields.start" satisfies CollectionTranslationKey,
        list: {
          width: 160,
          minWidth: 150,
          renderCell: ({ row }) => dateTime(row.start, timeZone, row.timezone, timeFormat),
        },
      },
      {
        name: "end",
        type: "dateTime",
        labelKey: "booking:myBookings.fields.end" satisfies CollectionTranslationKey,
        list: {
          width: 160,
          minWidth: 150,
          renderCell: ({ row }) => dateTime(row.end, timeZone, row.timezone, timeFormat),
        },
      },
      {
        name: "purpose",
        type: "text",
        labelKey: "booking:myBookings.fields.purpose" satisfies CollectionTranslationKey,
        list: {
          widthMode: "fill",
          renderCell: ({ row }) =>
            row.purpose ? (
              <Tooltip>
                <TooltipTrigger
                  render={
                    <button
                      type="button"
                      className="block min-w-24 text-left whitespace-normal outline-none focus-visible:ring-2 focus-visible:ring-ring"
                    />
                  }
                >
                  <span className="line-clamp-2 break-words">{row.purpose}</span>
                </TooltipTrigger>
                <TooltipContent role="tooltip">{row.purpose}</TooltipContent>
              </Tooltip>
            ) : null,
        },
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
