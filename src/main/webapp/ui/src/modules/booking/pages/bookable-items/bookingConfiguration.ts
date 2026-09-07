import { createElement } from "react";
import * as v from "valibot";
import {
  schedulingSettingsEntries,
  validMaximumBookingDuration,
  validOpeningHours,
} from "@/modules/booking/configuration/schedulingSettings";
import { bookingApiV2Headers } from "@/modules/booking/domain/apiV2";
import type { CollectionConfig, CollectionRow } from "@/modules/common/collection/collectionConfig";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import i18n from "@/modules/common/i18n";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { v2ListEnvelope } from "@/modules/common/queries/v2Pagination";
import { RoleSourceSchema } from "@/modules/common/resource-access/schemas";
import { serializeRsqlExpression } from "@/modules/common/table-list/rsql/rsqlCodec";
import { Badge } from "@/modules/common/ui/badge";
import { InventoryItem } from "@/modules/common/ui/inventory-item";
import { UnknownItem } from "@/modules/common/ui/unknown-item";

const NO_BOOKING_CAPABILITIES = {
  canEditConfiguration: false,
  canViewAudit: false,
  canViewAccess: false,
  canManageAssignments: false,
  canManageOwners: false,
  canCreateBooking: false,
  canManageOwnBookings: false,
  canManageAllEvents: false,
  canCreateBlockout: false,
  canSubscribeCalendar: false,
  canLeaveConfiguration: false,
};

export const BookingConfigurationSchema = v.pipe(
  v.object({
    id: v.number(),
    configurationVersion: v.number(),
    target: v.nullable(
      v.object({
        relationTo: v.literal("booking-instruments"),
        value: v.object({
          id: v.number(),
          name: v.string(),
          deleted: v.boolean(),
          // Supplied by the catalogue adapter; the collection API may omit them.
          parentContainerName: v.optional(v.nullable(v.string())),
          parentContainerGlobalId: v.optional(v.nullable(v.string())),
        }),
        globalId: v.string(),
      }),
    ),
    enabled: v.boolean(),
    state: v.picklist(["ACTIVE", "ARCHIVED"]),
    timezone: v.string(),
    ...schedulingSettingsEntries,
    // Fixed-projection consumers, such as Calendar, deliberately omit this field.
    updatedAt: v.optional(v.nullable(v.pipe(v.string(), v.isoTimestamp()))),
    createdAt: v.optional(v.nullable(v.pipe(v.string(), v.isoTimestamp()))),
    createdByName: v.optional(v.nullable(v.string())),
    createdBy: v.optional(
      v.object({
        relationTo: v.literal("users"),
        value: v.number(),
      }),
    ),
    effectiveRole: v.optional(v.nullable(v.string()), null),
    roleSources: v.optional(v.array(RoleSourceSchema), []),
    capabilities: v.optional(
      v.object({
        canEditConfiguration: v.boolean(),
        canViewAudit: v.boolean(),
        canViewAccess: v.boolean(),
        canManageAssignments: v.boolean(),
        canManageOwners: v.boolean(),
        canCreateBooking: v.boolean(),
        canManageOwnBookings: v.boolean(),
        canManageAllEvents: v.boolean(),
        canCreateBlockout: v.boolean(),
        canSubscribeCalendar: v.boolean(),
        canLeaveConfiguration: v.boolean(),
      }),
      NO_BOOKING_CAPABILITIES,
    ),
    ownerHealth: v.optional(v.object({ hasEffectiveOwner: v.boolean() })),
  }),
  v.forward(
    v.check((configuration) => validOpeningHours(configuration.openingStart, configuration.openingEnd)),
    ["openingEnd"],
  ),
  v.forward(
    v.check((configuration) =>
      validMaximumBookingDuration(configuration.maxBookingDurationMinutes, configuration.slotGranularityMinutes),
    ),
    ["maxBookingDurationMinutes"],
  ),
);

export type BookingConfiguration = v.InferOutput<typeof BookingConfigurationSchema>;

const InstrumentLocationSchema = v.object({
  parentContainerName: v.nullable(v.string()),
  parentContainerGlobalId: v.nullable(v.string()),
});

export const BookingConfigurationInputSchema = v.pipe(
  v.object({
    target: v.object({
      relationTo: v.literal("booking-instruments"),
      value: v.number(),
    }),
    enabled: v.boolean(),
    ...schedulingSettingsEntries,
  }),
  v.forward(
    v.check((configuration) => validOpeningHours(configuration.openingStart, configuration.openingEnd)),
    ["openingEnd"],
  ),
  v.forward(
    v.check((configuration) =>
      validMaximumBookingDuration(configuration.maxBookingDurationMinutes, configuration.slotGranularityMinutes),
    ),
    ["maxBookingDurationMinutes"],
  ),
);

export type BookingConfigurationInput = v.InferOutput<typeof BookingConfigurationInputSchema>;

export const BookingConfigurationUpdateInputSchema = v.pipe(
  v.object({
    enabled: v.boolean(),
    ...schedulingSettingsEntries,
  }),
  v.forward(
    v.check((configuration) => validOpeningHours(configuration.openingStart, configuration.openingEnd)),
    ["openingEnd"],
  ),
  v.forward(
    v.check((configuration) =>
      validMaximumBookingDuration(configuration.maxBookingDurationMinutes, configuration.slotGranularityMinutes),
    ),
    ["maxBookingDurationMinutes"],
  ),
);

export type BookingConfigurationUpdateInput = v.InferOutput<typeof BookingConfigurationUpdateInputSchema>;

export const BOOKING_CONFIGURATION_READ_FIELDS =
  "id,configurationVersion,target,enabled,state,timezone,slotGranularityMinutes,openingStart,openingEnd,bufferBeforeMinutes,bufferAfterMinutes,maxBookingDurationMinutes,allowDoubleBooking,createdAt,createdByName,updatedAt,effectiveRole,roleSources,capabilities,ownerHealth";

const bookingConfigurationReadParameters = {
  depth: "1",
  "fields[booking-configurations]": BOOKING_CONFIGURATION_READ_FIELDS,
};

async function withLocation(
  configuration: BookingConfiguration,
  token: string,
  signal?: AbortSignal,
): Promise<BookingConfiguration> {
  if (!configuration.target) return configuration;
  const target = configuration.target.value;
  if (target.parentContainerName && target.parentContainerGlobalId) return configuration;
  const parameters = new URLSearchParams({
    "fields[instruments]": "parentContainerName,parentContainerGlobalId",
  });
  let response: Response;
  try {
    response = await fetch(`/api/v2/instruments/${target.id}?${parameters}`, {
      headers: bookingApiV2Headers(token),
      signal,
    });
  } catch (error) {
    if (signal?.aborted) throw error;
    return configuration;
  }
  if (!response.ok) return configuration;
  const location = parseOrThrow(InstrumentLocationSchema, (await response.json()) as unknown);
  return {
    ...configuration,
    target: {
      ...configuration.target,
      value: { ...target, ...location },
    },
  };
}

export async function fetchBookingConfiguration(
  id: number,
  token: string,
  signal?: AbortSignal,
): Promise<BookingConfiguration> {
  const parameters = new URLSearchParams({
    ...bookingConfigurationReadParameters,
  });
  const response = await fetch(`/api/v2/booking-configurations/${id}?${parameters}`, {
    headers: bookingApiV2Headers(token),
    signal,
  });
  if (!response.ok) throw new Error(`Booking configuration request failed with status ${response.status}`);
  return parseOrThrow(BookingConfigurationSchema, (await response.json()) as unknown);
}

export async function fetchBookingConfigurationByTarget(
  globalId: string,
  token: string,
  signal?: AbortSignal,
): Promise<BookingConfiguration> {
  const where = serializeRsqlExpression<BookingConfiguration>({
    kind: "comparison",
    field: "target",
    operator: "equals",
    value: globalId,
  });
  const parameters = new URLSearchParams({
    ...bookingConfigurationReadParameters,
    limit: "2",
    where,
  });
  const response = await fetch(`/api/v2/booking-configurations?${parameters}`, {
    headers: bookingApiV2Headers(token),
    signal,
  });
  if (!response.ok) throw new Error(`Booking configuration request failed with status ${response.status}`);
  const configurations = parseOrThrow(
    v2ListEnvelope(BookingConfigurationSchema),
    (await response.json()) as unknown,
  ).docs;
  if (configurations.length !== 1 || configurations[0].target?.globalId !== globalId) {
    throw new Error(`Expected exactly one booking configuration for ${globalId}`);
  }
  return configurations[0];
}

export async function fetchBookingConfigurationDetailsByTarget(
  globalId: string,
  token: string,
  signal?: AbortSignal,
): Promise<BookingConfiguration> {
  return withLocation(await fetchBookingConfigurationByTarget(globalId, token, signal), token, signal);
}

/** Returns a readable configuration for one Inventory target, or null when none is visible. */
export async function findBookingConfigurationByTarget(
  globalId: string,
  token: string,
  signal?: AbortSignal,
): Promise<BookingConfiguration | null> {
  const where = serializeRsqlExpression<BookingConfiguration>({
    kind: "comparison",
    field: "target",
    operator: "equals",
    value: globalId,
  });
  const parameters = new URLSearchParams({
    ...bookingConfigurationReadParameters,
    limit: "2",
    where,
  });
  const response = await fetch(`/api/v2/booking-configurations?${parameters}`, {
    headers: bookingApiV2Headers(token),
    signal,
  });
  if (!response.ok) throw new Error(`Booking configuration request failed with status ${response.status}`);
  const configurations = parseOrThrow(
    v2ListEnvelope(BookingConfigurationSchema),
    (await response.json()) as unknown,
  ).docs;
  if (configurations.length > 1) throw new Error(`Expected at most one booking configuration for ${globalId}`);
  return configurations[0] ?? null;
}

/** Returns the readable Booking configurations for a bounded set of Instrument global IDs. */
export async function fetchBookingOwnershipCandidates(
  globalIds: readonly string[],
  token: string,
  signal?: AbortSignal,
): Promise<readonly BookingConfiguration[]> {
  if (globalIds.length === 0) return [];
  const where = serializeRsqlExpression<BookingConfiguration>({
    kind: "comparison",
    field: "target",
    operator: "in",
    value: globalIds,
  });
  const parameters = new URLSearchParams({
    ...bookingConfigurationReadParameters,
    limit: String(Math.min(globalIds.length, 100)),
    where,
  });
  const response = await fetch(`/api/v2/booking-configurations?${parameters}`, {
    headers: bookingApiV2Headers(token),
    signal,
  });
  if (!response.ok) throw new Error(`Booking configuration request failed with status ${response.status}`);
  return parseOrThrow(v2ListEnvelope(BookingConfigurationSchema), (await response.json()) as unknown).docs;
}

export const bookingConfigurationConfig = {
  slug: "bookable-items",
  idField: "id",
  labels: {
    singularKey: "booking:bookableItems.singular",
    pluralKey: "booking:bookableItems.plural",
  },
  useAsTitle: "target",
  defaultColumns: ["target", "state", "enabled", "updatedAt"],
  listSearchableFields: ["target.name"],
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
