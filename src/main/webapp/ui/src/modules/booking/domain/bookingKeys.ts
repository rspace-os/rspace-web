import { apiV2Root } from "@/modules/common/queries/apiV2QueryKeys";

export const bookingKeys = {
  all: apiV2Root("bookings"),
  calendarEvents: () => [...bookingKeys.all, "calendar-events"] as const,
  calendarAvailability: () => [...bookingKeys.all, "calendar-availability"] as const,
  catalogue: () => [...bookingKeys.all, "booking-catalogue"] as const,
  upcoming: () => [...bookingKeys.all, "dashboard", "upcoming"] as const,
  itemEvents: (...parameters: readonly unknown[]) =>
    [...bookingKeys.all, "bookable-item-events", ...parameters] as const,
};

export const bookingConfigurationKeys = {
  all: apiV2Root("booking-configurations"),
  target: (globalId: string) => [...bookingConfigurationKeys.all, "target", globalId] as const,
  pickerTarget: (globalId: string | undefined) =>
    [...bookingConfigurationKeys.all, "picker", "target", globalId] as const,
  availability: () => [...bookingConfigurationKeys.all, "availability"] as const,
};

export const bookingTargetKeys = {
  all: apiV2Root("booking-configuration-targets"),
};

export const bookingSettingsKeys = {
  all: apiV2Root("booking-settings"),
};

export const bookingCatalogueKeys = {
  all: apiV2Root("booking-catalogue"),
  allItemsPrefix: (scope?: unknown) =>
    scope === undefined
      ? ([...bookingKeys.catalogue(), "all-items"] as const)
      : ([...bookingKeys.catalogue(), "all-items", scope] as const),
  availabilityCountsPrefix: (scope?: unknown) =>
    scope === undefined
      ? ([...bookingKeys.catalogue(), "availability-counts"] as const)
      : ([...bookingKeys.catalogue(), "availability-counts", scope] as const),
  pickers: () => [...bookingCatalogueKeys.all, "picker"] as const,
};

export const bookingSubscriptionKeys = {
  all: apiV2Root("users"),
  calendar: (configurationId: number) =>
    [...apiV2Root("booking-configurations"), configurationId, "calendar-subscription"] as const,
  notifications: (subjectId: number) =>
    [...apiV2Root("users"), subjectId, "booking-notification-subscriptions"] as const,
};
