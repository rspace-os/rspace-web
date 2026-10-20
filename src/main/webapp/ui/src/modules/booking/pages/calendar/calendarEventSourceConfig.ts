import type { BookingListDocument } from "@/modules/booking/domain/booking";
import type { CollectionConfig } from "@/modules/common/collection/collectionConfig";

export const calendarEventSourceConfig = {
  slug: "booking-events-calendar",
  idField: "id",
  useAsTitle: "purpose",
  defaultColumns: ["purpose"],
  listSearchableFields: ["purpose"],
  labels: {
    singularKey: "booking:calendar.event",
    pluralKey: "booking:calendar.title",
  },
  fields: [
    { name: "id", type: "number", labelKey: "booking:calendar.fields.id", list: false, form: false },
    {
      name: "target",
      type: "relationship",
      relationTo: "booking-instruments",
      hasMany: false,
      labelKey: "booking:calendar.fields.target",
      capabilities: { filterOperators: [] },
    },
    {
      name: "requesterId",
      type: "number",
      labelKey: "booking:calendar.fields.requester",
      list: false,
      form: false,
      capabilities: { filterOperators: [] },
    },
    {
      name: "purpose",
      type: "text",
      maximumLength: 1_000,
      labelKey: "booking:calendar.fields.purpose",
      capabilities: {
        filterOperators: ["equals", "notEquals", "contains", "matches", "exists"],
        supportsWildcards: true,
      },
    },
    {
      name: "bookedBy",
      type: "text",
      maximumLength: 255,
      labelKey: "booking:calendar.fields.bookedBy",
      capabilities: {
        filterOperators: ["contains", "matches", "exists"],
        supportsWildcards: true,
      },
    },
    {
      name: "privacy",
      type: "select",
      options: ["full", "busy"],
      labelKey: "booking:calendar.fields.privacy",
      capabilities: { filterOperators: ["equals", "notEquals", "in", "notIn", "exists"] },
    },
    {
      name: "timezone",
      type: "text",
      labelKey: "booking:calendar.fields.timezone",
      capabilities: {
        filterOperators: ["equals", "notEquals", "contains", "matches", "exists"],
        supportsWildcards: true,
      },
    },
    {
      name: "start",
      type: "dateTime",
      labelKey: "booking:calendar.fields.start",
      capabilities: {
        filterOperators: [
          "equals",
          "notEquals",
          "greaterThan",
          "greaterThanOrEqual",
          "lessThan",
          "lessThanOrEqual",
          "exists",
        ],
      },
    },
    {
      name: "end",
      type: "dateTime",
      labelKey: "booking:calendar.fields.end",
      capabilities: {
        filterOperators: [
          "equals",
          "notEquals",
          "greaterThan",
          "greaterThanOrEqual",
          "lessThan",
          "lessThanOrEqual",
          "exists",
        ],
      },
    },
    {
      name: "kind",
      type: "select",
      options: ["BOOKING", "MAINTENANCE"],
      labelKey: "booking:bookableItemDetails.events.kind",
      capabilities: { filterOperators: ["equals", "notEquals", "in", "notIn", "exists"] },
      list: false,
      form: false,
    },
    {
      name: "canEdit",
      type: "boolean",
      labelKey: "booking:calendar.fields.editable",
      capabilities: { filterOperators: [] },
      list: false,
      form: false,
    },
    {
      name: "createdAt",
      type: "dateTime",
      labelKey: "booking:calendar.fields.createdAt",
      capabilities: { filterOperators: [] },
      list: false,
      form: false,
    },
    {
      name: "updatedAt",
      type: "dateTime",
      labelKey: "booking:calendar.fields.updatedAt",
      capabilities: { filterOperators: [] },
      list: false,
      form: false,
    },
  ],
} satisfies CollectionConfig<BookingListDocument>;
