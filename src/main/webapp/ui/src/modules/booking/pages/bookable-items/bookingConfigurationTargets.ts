import { requireBookingValue } from "@/modules/booking/domain/bookingMapping";
import { searchBookingConfigurationTargets } from "@/modules/common/api/v2/generated/booking-configuration-targets/booking-configuration-targets";
import type { BookingConfigurationTarget } from "@/modules/common/api/v2/generated/models/bookingConfigurationTarget";
import type { BookingConfigurationTargetControllerTargetDirectoryDocument } from "@/modules/common/api/v2/generated/models/bookingConfigurationTargetControllerTargetDirectoryDocument";

export const MINIMUM_TARGET_QUERY_LENGTH = 2;

export function mapBookingTargetDirectory(
  response: BookingConfigurationTargetControllerTargetDirectoryDocument,
): BookingConfigurationTarget[] {
  return response.docs.map((target) => {
    if (target.deleted !== false) throw new Error("An eligible booking target must not be deleted");
    return {
      ...target,
      id: requireBookingValue(target.id, "id"),
      globalId: requireBookingValue(target.globalId, "globalId"),
      name: requireBookingValue(target.name, "name"),
    };
  });
}
export async function searchBookingTargets(query: string, signal?: AbortSignal): Promise<BookingConfigurationTarget[]> {
  const q = query.trim();
  return mapBookingTargetDirectory(await searchBookingConfigurationTargets({ q, limit: 20 }, { signal }));
}
