import { type APIRequestContext, expect } from "@playwright/test";
import type { BookingConfigurationsRead } from "@/modules/common/api/v2/generated/models/bookingConfigurationsRead";
import type { BookingOpeningHoursEntry } from "@/modules/common/api/v2/generated/models/bookingOpeningHoursEntry";

export type OpeningHoursEntry = BookingOpeningHoursEntry;
export type BookingConfiguration = Required<
  Pick<BookingConfigurationsRead, "id" | "configurationVersion" | "timezone">
> & { openingHours: OpeningHoursEntry[] };

export async function createBookingConfiguration(
  apiContext: APIRequestContext,
  apiKey: string,
  instrumentId: number,
  openingHours?: OpeningHoursEntry[],
): Promise<BookingConfiguration> {
  const response = await apiContext.post("/api/v2/booking-configurations", {
    headers: { apiKey },
    data: {
      enabled: true,
      target: { relationTo: "instruments", value: instrumentId },
      ...(openingHours ? { openingHours } : {}),
    },
  });
  expect(response.ok(), await response.text()).toBe(true);
  return response.json() as Promise<BookingConfiguration>;
}
