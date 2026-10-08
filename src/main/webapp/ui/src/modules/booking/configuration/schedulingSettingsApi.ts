import {
  type BookingAdminSettings,
  BookingAdminSettingsSchema,
  type BookingSettings,
  type BookingSettingsInput,
  BookingSettingsSchema,
  SchedulingSettingsSchema,
} from "@/modules/booking/configuration/schedulingSettingsSchema";
import { parseApiV2Problem } from "@/modules/booking/domain/booking";
import { BookingDisplayPreferencesInputSchema } from "@/modules/booking/domain/bookingDisplayPreferences";
import { apiV2Headers, apiV2JsonHeaders } from "@/modules/common/api/apiV2Headers";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { fetchWithUiToken } from "@/modules/common/utils/fetchWithUiToken";

export async function loadBookingSettings(token: string, signal?: AbortSignal): Promise<BookingSettings> {
  const response = await fetchWithUiToken("/api/v2/booking-settings", {
    headers: apiV2Headers(token),
    signal,
  });
  if (!response.ok) throw new Error(`Booking settings request failed with status ${response.status}`);
  return parseOrThrow(BookingSettingsSchema, await response.json());
}

export async function loadBookingAdminSettings(
  token: string,
  signal?: AbortSignal,
): Promise<BookingAdminSettings & { etag: string }> {
  const response = await fetchWithUiToken("/api/v2/booking-settings/admin", {
    headers: apiV2Headers(token),
    signal,
  });
  if (!response.ok) throw new Error(`Booking admin settings request failed with status ${response.status}`);
  const etag = response.headers.get("ETag");
  if (!etag) throw new Error("Booking settings response is missing ETag");
  return { ...parseOrThrow(BookingAdminSettingsSchema, await response.json()), etag };
}

export async function saveBookingSettings(
  input: BookingSettingsInput,
  expectedEtag: string,
  token: string,
): Promise<BookingAdminSettings & { etag: string }> {
  const scheduling = parseOrThrow(SchedulingSettingsSchema, input);
  const display = parseOrThrow(BookingDisplayPreferencesInputSchema, {
    availabilityWindowStart: input.availabilityWindowStart,
    availabilityWindowEnd: input.availabilityWindowEnd,
    timezoneMode: input.timezoneMode,
    customTimezone: input.customTimezone,
  });
  const response = await fetchWithUiToken("/api/v2/booking-settings/admin", {
    method: "PATCH",
    headers: apiV2JsonHeaders(token, { "If-Match": expectedEtag }),
    body: JSON.stringify({
      ...scheduling,
      ...display,
    }),
  });
  if (!response.ok) throw await parseApiV2Problem(response);
  const etag = response.headers.get("ETag");
  if (!etag) throw new Error("Booking settings response is missing ETag");
  return { ...parseOrThrow(BookingAdminSettingsSchema, await response.json()), etag };
}
