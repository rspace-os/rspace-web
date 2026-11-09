import {
  createOrReplaceBookingCalendarSubscription,
  getBookingCalendarSubscription,
  getGetBookingCalendarSubscriptionQueryOptions,
  revokeBookingCalendarSubscription,
} from "@/modules/common/api/v2/generated/booking-configurations/booking-configurations";
import type { BookingCalendarSubscriptionCreated } from "@/modules/common/api/v2/generated/models/bookingCalendarSubscriptionCreated";
import type { BookingCalendarSubscriptionStatus } from "@/modules/common/api/v2/generated/models/bookingCalendarSubscriptionStatus";
import {
  createOrReplaceUserBookingCalendarSubscription,
  getGetUserBookingCalendarSubscriptionQueryOptions,
  getUserBookingCalendarSubscription,
  revokeUserBookingCalendarSubscription,
} from "@/modules/common/api/v2/generated/users/users";

function checkSubscriptionStatus(body: BookingCalendarSubscriptionStatus): BookingCalendarSubscriptionStatus {
  if (body.active ? body.updatedAt === null : body.updatedAt !== null || body.subscriptionUrl !== null)
    throw new Error("Invalid calendar subscription status");
  if (body.subscriptionUrl !== null) checkFeedUrl(body.subscriptionUrl);
  return body;
}
function checkSubscriptionCreated(body: BookingCalendarSubscriptionCreated): BookingCalendarSubscriptionCreated {
  if (!body.active || body.updatedAt === null) throw new Error("Invalid calendar subscription creation");
  checkFeedUrl(body.subscriptionUrl);
  return body;
}
function checkFeedUrl(value: string): void {
  const url = new URL(value);
  if (url.protocol !== "http:" && url.protocol !== "https:")
    throw new Error("Calendar subscription URL must use HTTP or HTTPS");
}
export type UserCalendarSubscriptionStatus = BookingCalendarSubscriptionStatus & { etag: string };
export type UserCalendarSubscriptionCreated = BookingCalendarSubscriptionCreated & { etag: string };
function withEtag<T>(response: { body: T; headers: Headers }): T & { etag: string } {
  const etag = response.headers.get("ETag");
  if (etag === null) throw new Error("Calendar subscription response did not include an ETag");
  return { ...response.body, etag };
}
function mapStatusResponse(
  response: Awaited<ReturnType<typeof getBookingCalendarSubscription>>,
): UserCalendarSubscriptionStatus {
  return withEtag({ ...response, body: checkSubscriptionStatus(response.body) });
}
export function calendarSubscriptionQuery(configurationId: number) {
  return getGetBookingCalendarSubscriptionQueryOptions<UserCalendarSubscriptionStatus>(configurationId, {
    query: { select: mapStatusResponse },
  });
}
export function userCalendarSubscriptionQuery() {
  return getGetUserBookingCalendarSubscriptionQueryOptions<UserCalendarSubscriptionStatus>({
    query: { select: mapStatusResponse },
  });
}
export async function fetchCalendarSubscriptionStatus(
  configurationId: number,
  signal?: AbortSignal,
): Promise<UserCalendarSubscriptionStatus> {
  const response = await getBookingCalendarSubscription(configurationId, { signal });
  return withEtag({ ...response, body: checkSubscriptionStatus(response.body) });
}
export async function createOrReplaceCalendarSubscription(
  configurationId: number,
  etag: string,
): Promise<UserCalendarSubscriptionCreated> {
  const response = await createOrReplaceBookingCalendarSubscription(configurationId, { "If-Match": etag });
  return withEtag({ ...response, body: checkSubscriptionCreated(response.body) });
}
export async function revokeCalendarSubscription(configurationId: number): Promise<void> {
  await revokeBookingCalendarSubscription(configurationId);
}
export async function fetchUserCalendarSubscriptionStatus(
  signal?: AbortSignal,
): Promise<UserCalendarSubscriptionStatus> {
  const response = await getUserBookingCalendarSubscription({ signal });
  return withEtag({ ...response, body: checkSubscriptionStatus(response.body) });
}
export async function createOrReplaceUserCalendarSubscription(etag: string): Promise<UserCalendarSubscriptionCreated> {
  const response = await createOrReplaceUserBookingCalendarSubscription({ "If-Match": etag });
  return withEtag({ ...response, body: checkSubscriptionCreated(response.body) });
}
export async function revokeUserCalendarSubscription(): Promise<void> {
  await revokeUserBookingCalendarSubscription();
}
export function toWebcalUrl(feedUrl: string): string {
  const webcalUrl = feedUrl.replace(/^https?:\/\//, "webcal://");
  if (webcalUrl === feedUrl) throw new Error("Calendar subscription URL must use HTTP or HTTPS");
  return webcalUrl;
}

export function calendarApplicationUrls(feedUrl: string): {
  apple: string;
  google: string;
  other: string;
} {
  const webcal = toWebcalUrl(feedUrl);
  return {
    apple: webcal,
    google: `https://calendar.google.com/calendar/r?cid=${encodeURIComponent(webcal)}`,
    other: webcal,
  };
}
