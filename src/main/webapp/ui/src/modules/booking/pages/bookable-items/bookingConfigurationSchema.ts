import * as v from "valibot";
import { type SchedulingSettings, schedulingSettingsEntries } from "@/modules/booking/configuration/schedulingSettings";
import { isValidTimeZone } from "@/modules/booking/domain/bookingDisplayPreferences";
import { validIncrementForSchedule, validMaximumForIncrement } from "@/modules/booking/domain/bookingIncrement";
import { RoleSourceSchema } from "@/modules/common/resource-access/schemas";

const bookingCapabilityFields = {
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
  canManageNotificationSubscription: v.optional(v.boolean(), false),
};
const NO_BOOKING_CAPABILITIES = Object.fromEntries(Object.keys(bookingCapabilityFields).map((key) => [key, false])) as {
  [Key in keyof typeof bookingCapabilityFields]: false;
};

const validConfigurationMaximumDuration = (settings: SchedulingSettings) =>
  validMaximumForIncrement(settings.maxBookingDurationMinutes, settings);
const validConfigurationIncrement = (settings: SchedulingSettings) => validIncrementForSchedule(settings);

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
    capabilities: v.optional(v.object(bookingCapabilityFields), NO_BOOKING_CAPABILITIES),
    ownerHealth: v.optional(v.object({ hasEffectiveOwner: v.optional(v.boolean()) })),
  }),
  v.forward(
    v.check((settings) => validConfigurationMaximumDuration(settings)),
    ["maxBookingDurationMinutes"],
  ),
);

export type BookingConfiguration = v.InferOutput<typeof BookingConfigurationSchema>;

export const BookingConfigurationInputSchema = v.pipe(
  v.object({
    target: v.object({
      relationTo: v.literal("booking-instruments"),
      value: v.number(),
    }),
    enabled: v.boolean(),
    timezone: v.pipe(v.string(), v.check(isValidTimeZone)),
    ...schedulingSettingsEntries,
  }),
  v.forward(
    v.check((settings) => validConfigurationMaximumDuration(settings)),
    ["maxBookingDurationMinutes"],
  ),
  v.forward(
    v.check((settings) => validConfigurationIncrement(settings)),
    ["slotGranularityMinutes"],
  ),
);

export type BookingConfigurationInput = v.InferOutput<typeof BookingConfigurationInputSchema>;

export const BookingConfigurationUpdateInputSchema = v.pipe(
  v.object({
    enabled: v.boolean(),
    ...schedulingSettingsEntries,
  }),
  v.forward(
    v.check((settings) => validConfigurationMaximumDuration(settings)),
    ["maxBookingDurationMinutes"],
  ),
  v.forward(
    v.check((settings) => validConfigurationIncrement(settings)),
    ["slotGranularityMinutes"],
  ),
);

export type BookingConfigurationUpdateInput = v.InferOutput<typeof BookingConfigurationUpdateInputSchema>;
