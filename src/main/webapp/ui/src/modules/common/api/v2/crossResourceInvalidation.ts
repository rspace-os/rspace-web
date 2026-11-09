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
