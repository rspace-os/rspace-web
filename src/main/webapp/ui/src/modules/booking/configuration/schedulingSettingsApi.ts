import {
  getAdminBookingSettings,
  getBookingSettings,
  getGetAdminBookingSettingsSuspenseQueryOptions,
  getGetBookingSettingsSuspenseQueryOptions,
  patchAdminBookingSettings,
} from "@/modules/common/api/v2/generated/booking-settings/booking-settings";
import type { BookingSettingsControllerSettingsPatch } from "@/modules/common/api/v2/generated/models/bookingSettingsControllerSettingsPatch";
import { retryUnlessClientError } from "@/modules/common/queries/queryRetry";
import {
  type BookingAdminSettings,
  type BookingSettings,
  mapBookingAdminSettings,
  mapBookingSettings,
} from "./schedulingSettingsSchema";

function withEtag(
  response: Awaited<ReturnType<typeof getAdminBookingSettings>>,
): BookingAdminSettings & { etag: string } {
  const etag = response.headers.get("ETag");
  if (!etag) throw new Error("Booking settings response is missing ETag");
  return { ...mapBookingAdminSettings(response.body), etag };
}

export async function loadBookingSettings(signal?: AbortSignal): Promise<BookingSettings> {
  return mapBookingSettings(await getBookingSettings({ signal }));
}
export async function loadBookingAdminSettings(signal?: AbortSignal): Promise<BookingAdminSettings & { etag: string }> {
  return withEtag(await getAdminBookingSettings({ signal }));
}
export function bookingSettingsQuery() {
  return getGetBookingSettingsSuspenseQueryOptions<BookingSettings>({
    query: { select: mapBookingSettings, retry: retryUnlessClientError },
  });
}
export function bookingAdminSettingsQuery() {
  return getGetAdminBookingSettingsSuspenseQueryOptions<BookingAdminSettings & { etag: string }>({
    query: { select: withEtag, retry: retryUnlessClientError },
  });
}
export async function saveBookingSettings(
  input: BookingSettingsControllerSettingsPatch,
  expectedEtag: string,
): Promise<BookingAdminSettings & { etag: string }> {
  return withEtag(await patchAdminBookingSettings(input, { "If-Match": expectedEtag }));
}
