import { createElement } from "react";
import * as v from "valibot";
import type { CollectionConfig, CollectionRow } from "@/modules/common/collection/collectionConfig";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import i18n from "@/modules/common/i18n";
import { InventoryItem } from "@/modules/common/ui/inventory-item";
import { UnknownItem } from "@/modules/common/ui/unknown-item";
import { schedulingSettingsEntries, validMaximumBookingDuration, validOpeningHours } from "./schedulingSettings";

export const BookingConfigurationSchema = v.pipe(
  v.object({
    id: v.number(),
    target: v.nullable(
      v.object({
        relationTo: v.literal("instruments"),
        value: v.object({ id: v.number(), name: v.string(), deleted: v.boolean() }),
        globalId: v.string(),
      }),
    ),
    enabled: v.boolean(),
    timezone: v.string(),
    ...schedulingSettingsEntries,
    // Fixed-projection consumers, such as Calendar, deliberately omit this field.
    updatedAt: v.optional(v.nullable(v.string())),
  }),
  v.check((configuration) => validOpeningHours(configuration.openingStart, configuration.openingEnd)),
  v.check((configuration) =>
    validMaximumBookingDuration(configuration.maxBookingDurationMinutes, configuration.slotGranularityMinutes),
  ),
);

export type BookingConfiguration = v.InferOutput<typeof BookingConfigurationSchema>;

export const BookingConfigurationInputSchema = v.pipe(
  v.object({
    target: v.object({
      relationTo: v.literal("instruments"),
      value: v.number(),
    }),
    enabled: v.boolean(),
    timezone: v.string(),
    ...schedulingSettingsEntries,
  }),
  v.check((configuration) => validOpeningHours(configuration.openingStart, configuration.openingEnd)),
  v.check((configuration) =>
    validMaximumBookingDuration(configuration.maxBookingDurationMinutes, configuration.slotGranularityMinutes),
  ),
);

export type BookingConfigurationInput = v.InferOutput<typeof BookingConfigurationInputSchema>;

export const BookingConfigurationUpdateInputSchema = v.pipe(
  v.object({
    target: v.optional(
      v.object({
        relationTo: v.literal("instruments"),
        value: v.number(),
      }),
    ),
    enabled: v.boolean(),
    timezone: v.string(),
    ...schedulingSettingsEntries,
  }),
  v.check((configuration) => validOpeningHours(configuration.openingStart, configuration.openingEnd)),
  v.check((configuration) =>
    validMaximumBookingDuration(configuration.maxBookingDurationMinutes, configuration.slotGranularityMinutes),
  ),
);

export type BookingConfigurationUpdateInput = v.InferOutput<typeof BookingConfigurationUpdateInputSchema>;

export const bookingConfigurationConfig = {
  slug: "bookable-items",
  idField: "id",
  labels: {
    singularKey: "booking:bookableItems.singular",
    pluralKey: "booking:bookableItems.plural",
  },
  useAsTitle: "target",
  defaultColumns: ["target", "enabled", "timezone", "updatedAt"],
  listSearchableFields: ["target.name", "timezone"],
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
          return createElement(InventoryItem, {
            name: row.target.value.name,
            globalId: row.target.globalId,
            href: `/globalId/${row.target.globalId}`,
            idLinkLabel: i18n.t("common:tableList.filters.openRecord", { globalId: row.target.globalId }),
            compact: true,
            size: "xs",
          });
        },
      },
    },
    { name: "enabled", type: "boolean", labelKey: "booking:bookableItems.fields.enabled" },
    {
      name: "timezone",
      type: "select",
      options: Intl.supportedValuesOf("timeZone"),
      labelKey: "booking:bookableItems.fields.timezone",
    },
    {
      name: "updatedAt",
      type: "dateTime",
      labelKey: "booking:bookableItems.fields.updatedAt",
      form: false,
    },
    {
      name: "slotGranularityMinutes",
      type: "number",
      labelKey: "booking:settings.fields.granularity",
      list: false,
      form: false,
    },
    {
      name: "openingStart",
      type: "text",
      labelKey: "booking:settings.fields.openingStart",
      list: false,
      form: false,
    },
    {
      name: "openingEnd",
      type: "text",
      labelKey: "booking:settings.fields.openingEnd",
      list: false,
      form: false,
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
