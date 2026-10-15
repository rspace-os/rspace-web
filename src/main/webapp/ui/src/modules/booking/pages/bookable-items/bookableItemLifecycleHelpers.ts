import { ApiV2ProblemError } from "@/modules/booking/domain/booking";
import type { BookingConfigurationRow } from "./bookingConfiguration";

export type BookableItemsBulkAction = "enable" | "disable" | "archive";

type BookableItemsLifecycleErrorKey =
  | "bookableItems.lifecycleErrors.restore"
  | "bookableItems.lifecycleErrors.stale"
  | "bookableItems.lifecycleErrors.stateChanged"
  | "bookableItems.permanentDeleteDialog.error";
type BookableItemDetailLifecycleErrorKey =
  | "bookableItemDetails.lifecycleErrors.restore"
  | "bookableItemDetails.lifecycleErrors.stale"
  | "bookableItemDetails.lifecycleErrors.stateChanged"
  | "bookableItemDetails.permanentDeleteDialog.error";
type LifecycleErrorKey = BookableItemsLifecycleErrorKey | BookableItemDetailLifecycleErrorKey;

export function lifecycleErrorKey<
  TStale extends LifecycleErrorKey,
  TChanged extends LifecycleErrorKey,
  TFallback extends LifecycleErrorKey,
>(error: unknown, fallback: TFallback, stale: TStale, stateChanged: TChanged): TStale | TChanged | TFallback {
  if (error instanceof ApiV2ProblemError && error.status === 412) return stale;
  if (error instanceof ApiV2ProblemError && error.status === 409) return stateChanged;
  return fallback;
}

export function requiredVersion(configuration: BookingConfigurationRow): number {
  if (configuration.configurationVersion === undefined) {
    throw new Error("The booking configuration version is missing from the table projection");
  }
  return configuration.configurationVersion;
}
