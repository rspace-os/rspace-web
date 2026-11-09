export const crossResourceInvalidation: ReadonlyArray<{
  onMutations: readonly string[];
  invalidates: ReadonlyArray<{ query: string; params?: readonly string[] }>;
}> = [
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
