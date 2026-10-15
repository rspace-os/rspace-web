import { createElement } from "react";
import { useTranslation } from "react-i18next";
import {
  bookingTimeZoneOptions,
  browserTimeZone,
  useBookingTimeFormat,
} from "@/modules/booking/domain/bookingDisplayPreferences";
import { bookingRelationshipSources } from "@/modules/booking/domain/bookingRelationshipSource";
import { formatBookingDateTime } from "@/modules/booking/domain/bookingTime";
import type { CollectionConfig, CollectionRow, CollectionTranslationKey } from "@/modules/common/collection/config";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveConfig";
import i18n from "@/modules/common/i18n";
import { InventoryItem } from "@/modules/common/ui/inventory-item";
import { UnknownItem } from "@/modules/common/ui/unknown-item";
import type { BookingConfiguration } from "./bookingConfigurationSchema";

export const bookingConfigurationConfig = {
  slug: "bookable-items",
  relationshipSources: bookingRelationshipSources,
  idField: "id",
  labels: {
    singularKey: "booking:bookableItems.singular" satisfies CollectionTranslationKey,
    pluralKey: "booking:bookableItems.plural" satisfies CollectionTranslationKey,
  },
  useAsTitle: "target",
  defaultColumns: ["target", "state", "updatedAt"],
  listSearchableFields: ["target.name", "target.globalId"],
  fields: [
    {
      name: "id",
      type: "number",
      labelKey: "booking:bookableItems.fields.id" satisfies CollectionTranslationKey,
      list: false,
      form: false,
    },
    {
      name: "target",
      type: "relationship",
      relationTo: "instruments",
      hasMany: false,
      labelKey: "booking:bookableItems.fields.target" satisfies CollectionTranslationKey,
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
      labelKey: "booking:bookableItems.fields.enabled" satisfies CollectionTranslationKey,
      // Shown through the combined Status column; still filterable on its own.
      list: false,
    },
    {
      name: "state",
      type: "select",
      options: [
        {
          value: "ACTIVE",
          get label() {
            return i18n.t("booking:bookableItems.states.active");
          },
        },
        {
          value: "ARCHIVED",
          get label() {
            return i18n.t("booking:bookableItemDetails.archived");
          },
        },
      ],
      // The column header and the columns panel both read this label.
      labelKey: "booking:bookableItems.fields.status" satisfies CollectionTranslationKey,
      form: false,
      list: {
        dependencies: ["enabled"],
        // One status column: an archived item is Archived; an active one is Enabled or Disabled.
        renderCell: ({ row }) =>
          row.state === "ARCHIVED"
            ? i18n.t("booking:bookableItemDetails.archived")
            : row.enabled
              ? i18n.t("booking:bookableItemDetails.enabled")
              : i18n.t("booking:bookableItemDetails.disabled"),
      },
    },
    {
      name: "timezone",
      type: "select",
      options: bookingTimeZoneOptions(),
      labelKey: "booking:bookableItems.fields.timezone" satisfies CollectionTranslationKey,
      required: true,
      list: false,
      // Create-only: the API rejects timezone changes, so edit forms filter this field out.
      form: { descriptionKey: "booking:bookableItems.fields.timezoneDescription" satisfies CollectionTranslationKey },
    },
    {
      name: "updatedAt",
      type: "dateTime",
      labelKey: "booking:bookableItems.fields.updatedAt" satisfies CollectionTranslationKey,
      form: false,
      list: {
        renderCell: ({ row }) => createElement(ConfigurationUpdatedAt, { value: row.updatedAt }),
      },
    },
    {
      name: "slotGranularityMinutes",
      type: "number",
      labelKey: "booking:settings.fields.granularity" satisfies CollectionTranslationKey,
      list: false,
      form: false,
    },
    {
      name: "openingHours",
      type: "array",
      labelKey: "booking:bookableItemDetails.fields.openingHours" satisfies CollectionTranslationKey,
      minRows: 1,
      maxRows: 7,
      list: false,
      form: false,
      fields: [
        {
          name: "dayOfWeek",
          type: "number",
          labelKey: "booking:settings.openingHours.dayOfWeek" satisfies CollectionTranslationKey,
          required: true,
          writeConstraints: { integer: true, minimum: 1, maximum: 7 },
        },
        {
          name: "start",
          type: "text",
          labelKey: "booking:settings.fields.openingStart" satisfies CollectionTranslationKey,
          required: true,
          writeConstraints: { format: "time" },
        },
        {
          name: "end",
          type: "text",
          labelKey: "booking:settings.fields.openingEnd" satisfies CollectionTranslationKey,
          required: true,
          writeConstraints: { format: "time", allow24HourEnd: true },
        },
      ],
    },
    {
      name: "bufferBeforeMinutes",
      type: "number",
      labelKey: "booking:settings.fields.bufferBefore" satisfies CollectionTranslationKey,
      list: false,
      form: false,
    },
    {
      name: "bufferAfterMinutes",
      type: "number",
      labelKey: "booking:settings.fields.bufferAfter" satisfies CollectionTranslationKey,
      list: false,
      form: false,
    },
    {
      name: "allowDoubleBooking",
      type: "boolean",
      labelKey: "booking:settings.fields.allowDoubleBooking" satisfies CollectionTranslationKey,
      list: false,
      form: false,
    },
    {
      name: "maxBookingDurationMinutes",
      type: "number",
      labelKey: "booking:settings.fields.maximumDuration" satisfies CollectionTranslationKey,
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
    ? formatBookingDateTime(value, browserTimeZone() ?? "UTC", i18n.language, timeFormat)
    : t("bookableItemDetails.notAvailable");
}
