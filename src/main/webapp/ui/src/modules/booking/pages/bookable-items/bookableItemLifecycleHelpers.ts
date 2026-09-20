import { ApiV2ProblemError } from "@/modules/booking/domain/booking";
import type { BookingConfigurationRow } from "./bookingConfiguration";

export type BookableItemsBulkAction = "enable" | "disable" | "archive";

export type BookableItemsLifecycleErrorKey =
  | "bookableItems.lifecycleErrors.restore"
  | "bookableItems.lifecycleErrors.stale"
  | "bookableItems.lifecycleErrors.stateChanged"
  | "bookableItems.permanentDeleteDialog.error";

export function lifecycleErrorKey(
  error: unknown,
  fallback: BookableItemsLifecycleErrorKey,
): BookableItemsLifecycleErrorKey {
  if (error instanceof ApiV2ProblemError && error.status === 412) {
    return "bookableItems.lifecycleErrors.stale";
  }
  if (error instanceof ApiV2ProblemError && error.status === 409) {
    return "bookableItems.lifecycleErrors.stateChanged";
  }
  return fallback;
}

export function requiredVersion(configuration: BookingConfigurationRow): number {
  if (configuration.configurationVersion === undefined) {
    throw new Error("The booking configuration version is missing from the table projection");
  }
  return configuration.configurationVersion;
}
