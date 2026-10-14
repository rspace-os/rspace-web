import { createElement } from "react";
import { useTranslation } from "react-i18next";
import { bookingTimeZoneOptions, useBookingTimeFormat } from "@/modules/booking/domain/bookingDisplayPreferences";
import { bookingRelationshipSources } from "@/modules/booking/domain/bookingRelationshipSource";
import { bookingHourCycle } from "@/modules/booking/domain/bookingTime";
import type { CollectionConfig, CollectionRow } from "@/modules/common/collection/collectionConfig";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import i18n from "@/modules/common/i18n";
import { InventoryItem } from "@/modules/common/ui/inventory-item";
import { UnknownItem } from "@/modules/common/ui/unknown-item";
import type { BookingConfiguration } from "./bookingConfigurationSchema";

export const bookingConfigurationConfig = {
  slug: "bookable-items",
  relationshipSources: bookingRelationshipSources,
  idField: "id",
  labels: {
    singularKey: "booking:bookableItems.singular",
    pluralKey: "booking:bookableItems.plural",
  },
  useAsTitle: "target",
  defaultColumns: ["target", "state", "updatedAt"],
  listSearchableFields: ["target.name", "target.globalId"],
  fields: [
    { name: "id", type: "number", labelKey: "booking:bookableItems.fields.id", list: false, form: false },
    {
      name: "target",
      type: "relationship",
      relationTo: "booking-instruments",
      hasMany: false,
      labelKey: "booking:bookableItems.fields.target",
      list: {
        renderCell: ({ row }) => {
          if (row.target === null) return createElement(UnknownItem, { size: "xs" });
          return createElement(
            "div",
            { className: "grid gap-1" },
            createElement(InventoryItem, {
              name: row.target.value.name,
              globalId: row.target.globalId,
              compact: true,
              size: "xs",
            }),
          );
        },
      },
    },
    {
      name: "enabled",
      type: "boolean",
      labelKey: "booking:bookableItems.fields.enabled",
      // Shown through the combined Status column; still filterable on its own.
      list: false,
    },
    {
      name: "state",
      type: "select",
      options: ["ACTIVE", "ARCHIVED"],
      // The column header and the columns panel both read this label.
      labelKey: "booking:bookableItems.fields.status",
      form: false,
      list: {
        dependencies: ["enabled"],
        // One status column: an archived item is Archived; an active one is Enabled or Disabled.
        renderCell: ({ row }) =>
          row.state === "ARCHIVED"
            ? i18n.t("booking:bookableItemDetails.archived")
            : i18n.t(row.enabled ? "booking:bookableItemDetails.enabled" : "booking:bookableItemDetails.disabled"),
      },
    },
    {
      name: "timezone",
      type: "select",
      options: bookingTimeZoneOptions(),
      labelKey: "booking:bookableItems.fields.timezone",
      required: true,
      list: false,
      // Create-only: the API rejects timezone changes, so edit forms filter this field out.
      form: { descriptionKey: "booking:bookableItems.fields.timezoneDescription" },
    },
    {
      name: "updatedAt",
      type: "dateTime",
      labelKey: "booking:bookableItems.fields.updatedAt",
      form: false,
      list: {
        renderCell: ({ row }) => createElement(ConfigurationUpdatedAt, { value: row.updatedAt }),
      },
    },
    {
      name: "slotGranularityMinutes",
      type: "number",
      labelKey: "booking:settings.fields.granularity",
      list: false,
      form: false,
    },
    {
      name: "openingHours",
      type: "array",
      labelKey: "booking:bookableItemDetails.fields.openingHours",
      minRows: 1,
      maxRows: 7,
      list: false,
      form: false,
      fields: [
        {
          name: "dayOfWeek",
          type: "number",
          labelKey: "booking:settings.openingHours.dayOfWeek",
          required: true,
          writeConstraints: { integer: true, minimum: 1, maximum: 7 },
        },
        {
          name: "start",
          type: "text",
          labelKey: "booking:settings.fields.openingStart",
          required: true,
          writeConstraints: { format: "time" },
        },
        {
          name: "end",
          type: "text",
          labelKey: "booking:settings.fields.openingEnd",
          required: true,
          writeConstraints: { format: "time", allow24HourEnd: true },
        },
      ],
    },
    {
      name: "bufferBeforeMinutes",
      type: "number",
      labelKey: "booking:settings.fields.bufferBefore",
      list: false,
      form: false,
    },
    {
      name: "bufferAfterMinutes",
      type: "number",
      labelKey: "booking:settings.fields.bufferAfter",
      list: false,
      form: false,
    },
    {
      name: "allowDoubleBooking",
      type: "boolean",
      labelKey: "booking:settings.fields.allowDoubleBooking",
      list: false,
      form: false,
    },
    {
      name: "maxBookingDurationMinutes",
      type: "number",
      labelKey: "booking:settings.fields.maximumDuration",
      list: false,
      form: false,
    },
  ],
} satisfies CollectionConfig<BookingConfiguration>;

/** One row of the bookable-items table. `target` is null when its item cannot be resolved. */
export type BookingConfigurationRow = CollectionRow<BookingConfiguration, "id" | "target">;

export const bookingConfigurationFields = resolveCollectionConfig(bookingConfigurationConfig).fields;

function ConfigurationUpdatedAt({ value }: { value: string | null | undefined }) {
  const { i18n, t } = useTranslation("booking");
  const timeFormat = useBookingTimeFormat();
  return value
    ? new Intl.DateTimeFormat(i18n.language, {
        dateStyle: "medium",
        timeStyle: "short",
        hourCycle: bookingHourCycle(timeFormat),
      }).format(new Date(value))
    : t("bookableItemDetails.notAvailable");
}
