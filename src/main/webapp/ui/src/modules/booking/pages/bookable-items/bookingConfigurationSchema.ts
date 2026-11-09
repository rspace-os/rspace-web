import * as v from "valibot";
import { SchedulingSettingsSchema } from "@/modules/booking/configuration/schedulingSettingsSchema";
import { mapBookingTarget } from "@/modules/booking/domain/booking";
import { requireBookingValue as required } from "@/modules/booking/domain/bookingMapping";
import type { BookingConfigurationsRead } from "@/modules/common/api/v2/generated/models/bookingConfigurationsRead";

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
  canManageNotificationSubscription: false,
};

function mapConfigurationFields(input: BookingConfigurationsRead) {
  const policy = v.parse(SchedulingSettingsSchema, input);
  return {
    ...input,
    ...input,
    id: input.id,
    configurationVersion: required(input.configurationVersion, "configurationVersion"),
    target: input.target === null ? null : mapBookingTarget(input.target),
    enabled: required(input.enabled, "enabled"),
    state: required(input.state, "state"),
    timezone: required(input.timezone, "timezone"),
    updatedAt: input.updatedAt,
    createdAt: input.createdAt,
    createdByName: input.createdByName,
    createdBy: input.createdBy,
    effectiveRole: input.effectiveRole ?? null,
    roleSources: input.roleSources ?? [],
    capabilities: { ...NO_BOOKING_CAPABILITIES, ...input.capabilities },
    ownerHealth: input.ownerHealth,
    ...policy,
  };
}
type OptionalConfigurationFields = "createdAt" | "updatedAt" | "createdByName" | "createdBy" | "ownerHealth";
export type BookingConfiguration = Omit<ReturnType<typeof mapConfigurationFields>, OptionalConfigurationFields> &
  Partial<Pick<ReturnType<typeof mapConfigurationFields>, OptionalConfigurationFields>>;
export function mapBookingConfiguration(input: BookingConfigurationsRead): BookingConfiguration {
  return mapConfigurationFields(input);
}
