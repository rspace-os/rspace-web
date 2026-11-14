// The item popover owns subscription status updates/refetches to preserve response ETags.
export const crossResourceInvalidation: ReadonlyArray<{
  onMutations: readonly string[];
  invalidates: ReadonlyArray<{ query: string; params?: readonly string[] }>;
}> = [
  {
    onMutations: ["replaceMyBookingPreferences", "resetMyBookingPreferences"],
    invalidates: [{ query: "getMyBookingPreferences" }],
  },
  {
    onMutations: ["patchAdminBookingSettings"],
    invalidates: [
      { query: "getBookingSettings" },
      { query: "getAdminBookingSettings" },
      { query: "getMyBookingPreferences" },
    ],
  },
  {
    onMutations: [
      "createBooking",
      "updateBooking",
      "createBookingConfiguration",
      "updateBookingConfiguration",
      "deleteBookingConfiguration",
      "createManyBookingConfigurations",
      "updateManyBookingConfigurations",
      "deleteManyBookingConfigurations",
      "replaceBookingConfigurationAccess",
      "leaveBookingConfigurationAccess",
    ],
    invalidates: [{ query: "searchBookingCatalogue" }, { query: "searchBookingCatalogueLocations" }],
  },
  {
    onMutations: ["deleteBookingConfiguration", "updateBookingConfiguration"],
    invalidates: [{ query: "listBookings" }, { query: "countBookings" }],
  },
  {
    onMutations: [
      "createUserBookingCalendarSubscription",
      "rotateUserBookingCalendarSubscription",
      "revokeUserBookingCalendarSubscription",
    ],
    invalidates: [{ query: "getUserBookingCalendarSubscription" }],
  },
  {
    onMutations: ["updateBookingConfiguration", "deleteBookingConfiguration"],
    invalidates: [{ query: "getBookingCalendarSubscription", params: ["id"] }],
  },
  {
    onMutations: [
      "createBookingCalendarSubscription",
      "rotateBookingCalendarSubscription",
      "revokeBookingCalendarSubscription",
      "updateBookingConfiguration",
      "deleteBookingConfiguration",
    ],
    invalidates: [{ query: "listMyBookableItemCalendarSubscriptions" }],
  },
  {
    onMutations: [
      "createBooking",
      "updateBooking",
      "createBookingConfiguration",
      "updateBookingConfiguration",
      "deleteBookingConfiguration",
      "createManyBookingConfigurations",
      "updateManyBookingConfigurations",
      "deleteManyBookingConfigurations",
      "replaceBookingConfigurationAccess",
      "leaveBookingConfigurationAccess",
    ],
    invalidates: [{ query: "searchCalendarEvents" }, { query: "searchCalendarCatalogue" }],
  },
  {
    onMutations: [
      "createBookingCalendarSubscription",
      "rotateBookingCalendarSubscription",
      "revokeBookingCalendarSubscription",
      "updateBookingConfiguration",
      "deleteBookingConfiguration",
    ],
    invalidates: [{ query: "listMyBookableItemCalendarSubscriptions" }],
  },
  {
    onMutations: [
      "createBooking",
      "updateBooking",
      "createBookingConfiguration",
      "updateBookingConfiguration",
      "deleteBookingConfiguration",
      "createManyBookingConfigurations",
      "updateManyBookingConfigurations",
      "deleteManyBookingConfigurations",
      "replaceBookingConfigurationAccess",
      "leaveBookingConfigurationAccess",
    ],
    invalidates: [{ query: "countBookingCatalogueAvailability" }],
  },
  {
    onMutations: [
      "createBookingConfiguration",
      "updateBookingConfiguration",
      "deleteBookingConfiguration",
      "createManyBookingConfigurations",
      "updateManyBookingConfigurations",
      "deleteManyBookingConfigurations",
    ],
    invalidates: [{ query: "searchBookingConfigurationTargets" }],
  },
];
