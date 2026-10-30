import { createElement } from "react";
import type { CollectionConfig, CollectionRow } from "@/modules/common/collection/config";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveConfig";
import i18n from "@/modules/common/i18n";
import { Badge } from "@/modules/common/ui/badge";
import { InventoryItem } from "@/modules/common/ui/inventory-item";
import { UnknownItem } from "@/modules/common/ui/unknown-item";
import type { BookingConfiguration } from "./bookingConfigurationSchema";

export const bookingConfigurationConfig = {
  slug: "bookable-items",
  idField: "id",
  labels: {
    singularKey: "booking:bookableItems.singular",
    pluralKey: "booking:bookableItems.plural",
  },
  useAsTitle: "target",
  defaultColumns: ["target", "state", "enabled", "updatedAt"],
  listSearchableFields: ["target.name", "target.globalId"],
  fields: [
    { name: "id", type: "number", labelKey: "booking:bookableItems.fields.id", list: false, form: false },
    {
      name: "target",
      type: "relationship",
      relationTo: "instruments",
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
            row.ownerHealth?.hasEffectiveOwner === false
              ? createElement(
                  Badge,
                  { variant: "destructive", className: "w-fit" },
                  i18n.t("booking:bookableItems.ownerHealth.needsOwner"),
                )
              : null,
          );
        },
      },
    },
    {
      name: "enabled",
      type: "boolean",
      labelKey: "booking:bookableItems.fields.enabled",
      list: {
        renderCell: ({ row }) =>
          i18n.t(row.enabled ? "booking:bookableItemDetails.enabled" : "booking:bookableItemDetails.disabled"),
      },
    },
    {
      name: "state",
      type: "select",
      options: ["ACTIVE", "ARCHIVED"],
      labelKey: "booking:bookableItems.fields.state",
      form: false,
      list: {
        renderCell: ({ row }) =>
          row.state === "ACTIVE"
            ? i18n.t("booking:bookableItems.states.active")
            : i18n.t("booking:bookableItemDetails.archived"),
      },
    },
    {
      name: "timezone",
      type: "select",
      options: Intl.supportedValuesOf("timeZone"),
      labelKey: "booking:bookableItems.fields.timezone",
      list: false,
      form: false,
    },
    {
      name: "updatedAt",
      type: "dateTime",
      labelKey: "booking:bookableItems.fields.updatedAt",
      form: false,
      list: {
        renderCell: ({ row }) =>
          row.updatedAt
            ? new Intl.DateTimeFormat(i18n.language, { dateStyle: "medium", timeStyle: "short" }).format(
                new Date(row.updatedAt),
              )
            : i18n.t("booking:bookableItemDetails.notAvailable"),
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
      labelKey: "booking:settings.fields.buffer",
      list: false,
      form: false,
    },
    {
      name: "bufferAfterMinutes",
      type: "number",
      labelKey: "booking:settings.fields.buffer",
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
