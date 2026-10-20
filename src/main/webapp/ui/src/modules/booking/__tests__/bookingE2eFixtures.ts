import { type APIRequestContext, expect } from "@playwright/test";

export type OpeningHoursEntry = { dayOfWeek: number; start: string; end: string };
export type BookingConfiguration = {
  id: number;
  configurationVersion: number;
  timezone: string;
  openingHours: OpeningHoursEntry[];
};

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
      target: { relationTo: "booking-instruments", value: instrumentId },
      ...(openingHours ? { openingHours } : {}),
    },
  });
  expect(response.ok(), await response.text()).toBe(true);
  return response.json() as Promise<BookingConfiguration>;
}
