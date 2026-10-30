import type { BookingListDocument } from "@/modules/booking/domain/booking";
import type { CollectionConfig, CollectionTranslationKey } from "@/modules/common/collection/config";
import i18n from "@/modules/common/i18n";

export const calendarEventSourceConfig = {
  slug: "booking-events-calendar",
  idField: "id",
  useAsTitle: "purpose",
  defaultColumns: ["purpose"],
  listSearchableFields: ["purpose"],
  labels: {
    singularKey: "booking:calendar.event" satisfies CollectionTranslationKey,
    pluralKey: "booking:calendar.title" satisfies CollectionTranslationKey,
  },
  fields: [
    {
      name: "id",
      type: "number",
      labelKey: "booking:calendar.fields.id" satisfies CollectionTranslationKey,
      list: false,
      form: false,
    },
    {
      name: "target",
      type: "relationship",
      relationTo: "instruments",
      hasMany: false,
      labelKey: "booking:calendar.fields.target" satisfies CollectionTranslationKey,
      capabilities: { filterOperators: [] },
    },
    {
      name: "requesterId",
      type: "number",
      labelKey: "booking:calendar.fields.requester" satisfies CollectionTranslationKey,
      list: false,
      form: false,
      capabilities: { filterOperators: [] },
    },
    {
      name: "purpose",
      type: "text",
      maximumLength: 1_000,
      labelKey: "booking:calendar.fields.purpose" satisfies CollectionTranslationKey,
      capabilities: {
        filterOperators: ["equals", "notEquals", "contains", "matches", "exists"],
        supportsWildcards: true,
      },
    },
    {
      name: "bookedBy",
      type: "text",
      maximumLength: 255,
      labelKey: "booking:calendar.fields.bookedBy" satisfies CollectionTranslationKey,
      capabilities: {
        filterOperators: ["contains", "matches", "exists"],
        supportsWildcards: true,
      },
    },
    {
      name: "privacy",
      type: "select",
      options: [
        {
          value: "full",
          get label() {
            return i18n.t("booking:calendar.privacy.full");
          },
        },
        {
          value: "busy",
          get label() {
            return i18n.t("booking:calendar.privacy.busy");
          },
        },
      ],
      labelKey: "booking:calendar.fields.privacy" satisfies CollectionTranslationKey,
      capabilities: { filterOperators: ["equals", "notEquals", "in", "notIn", "exists"] },
    },
    {
      name: "timezone",
      type: "text",
      labelKey: "booking:calendar.fields.timezone" satisfies CollectionTranslationKey,
      capabilities: {
        filterOperators: ["equals", "notEquals", "contains", "matches", "exists"],
        supportsWildcards: true,
      },
    },
    {
      name: "start",
      type: "dateTime",
      labelKey: "booking:calendar.fields.start" satisfies CollectionTranslationKey,
      capabilities: {
        filterOperators: ["equals", "notEquals", "greaterThan", "greaterThanOrEqual", "lessThan", "lessThanOrEqual"],
      },
    },
    {
      name: "end",
      type: "dateTime",
      labelKey: "booking:calendar.fields.end" satisfies CollectionTranslationKey,
      capabilities: {
        filterOperators: ["equals", "notEquals", "greaterThan", "greaterThanOrEqual", "lessThan", "lessThanOrEqual"],
      },
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
      labelKey: "booking:bookableItemDetails.events.kind" satisfies CollectionTranslationKey,
      capabilities: { filterOperators: ["equals", "notEquals", "exists"] },
      list: false,
      form: false,
    },
    {
      name: "canEdit",
      type: "boolean",
      labelKey: "booking:calendar.fields.editable" satisfies CollectionTranslationKey,
      capabilities: { filterOperators: [] },
      list: false,
      form: false,
    },
    {
      name: "createdAt",
      type: "dateTime",
      labelKey: "booking:calendar.fields.createdAt" satisfies CollectionTranslationKey,
      capabilities: { filterOperators: [] },
      list: false,
      form: false,
    },
    {
      name: "updatedAt",
      type: "dateTime",
      labelKey: "booking:calendar.fields.updatedAt" satisfies CollectionTranslationKey,
      capabilities: { filterOperators: [] },
      list: false,
      form: false,
    },
  ],
} satisfies CollectionConfig<BookingListDocument>;
