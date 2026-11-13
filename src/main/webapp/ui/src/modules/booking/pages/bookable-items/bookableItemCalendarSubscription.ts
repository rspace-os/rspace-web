import { requireBookingValue } from "@/modules/booking/domain/bookingMapping";
import {
  createBookingCalendarSubscription,
  getBookingCalendarSubscription,
  getGetBookingCalendarSubscriptionQueryOptions,
  revokeBookingCalendarSubscription,
  rotateBookingCalendarSubscription,
} from "@/modules/common/api/v2/generated/booking-configurations/booking-configurations";
import type { BookingCalendarSubscriptionCreated } from "@/modules/common/api/v2/generated/models/bookingCalendarSubscriptionCreated";
import type { BookingCalendarSubscriptionStatus } from "@/modules/common/api/v2/generated/models/bookingCalendarSubscriptionStatus";
import type { UserBookingCalendarSubscriptionControllerItemLinkDocument } from "@/modules/common/api/v2/generated/models/userBookingCalendarSubscriptionControllerItemLinkDocument";
import {
  createUserBookingCalendarSubscription,
  getGetUserBookingCalendarSubscriptionQueryOptions,
  getListMyBookableItemCalendarSubscriptionsQueryOptions,
  getUserBookingCalendarSubscription,
  listMyBookableItemCalendarSubscriptions,
  revokeUserBookingCalendarSubscription,
  rotateUserBookingCalendarSubscription,
} from "@/modules/common/api/v2/generated/users/users";

function checkSubscriptionStatus(body: BookingCalendarSubscriptionStatus): BookingCalendarSubscriptionStatus {
  if (
    body.active
      ? body.updatedAt === null || body.subscriptionUrl === null
      : body.updatedAt !== null || body.subscriptionUrl !== null
  )
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
export async function createCalendarSubscription(configurationId: number): Promise<UserCalendarSubscriptionCreated> {
  const response = await createBookingCalendarSubscription(configurationId);
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
export async function createUserCalendarSubscription(): Promise<UserCalendarSubscriptionCreated> {
  const response = await createUserBookingCalendarSubscription();
  return withEtag({ ...response, body: checkSubscriptionCreated(response.body) });
}
export async function revokeUserCalendarSubscription(): Promise<void> {
  await revokeUserBookingCalendarSubscription();
}
export async function rotateCalendarSubscription(
  configurationId: number,
  etag: string,
): Promise<UserCalendarSubscriptionCreated> {
  const response = await rotateBookingCalendarSubscription(configurationId, { "If-Match": etag });
  return withEtag({ ...response, body: checkSubscriptionCreated(response.body) });
}
export async function rotateUserCalendarSubscription(etag: string): Promise<UserCalendarSubscriptionCreated> {
  const response = await rotateUserBookingCalendarSubscription({ "If-Match": etag });
  return withEtag({ ...response, body: checkSubscriptionCreated(response.body) });
}
function mapItemLinks(
  response: Awaited<ReturnType<typeof listMyBookableItemCalendarSubscriptions>>,
): UserBookingCalendarSubscriptionControllerItemLinkDocument[] {
  const docs = requireBookingValue(response.docs, "docs");
  for (const doc of docs) checkFeedUrl(requireBookingValue(doc.subscriptionUrl, "subscriptionUrl"));
  return docs;
}
export async function fetchItemCalendarLinks(
  signal?: AbortSignal,
): Promise<UserBookingCalendarSubscriptionControllerItemLinkDocument[]> {
  return mapItemLinks(await listMyBookableItemCalendarSubscriptions({ signal }));
}
export function itemCalendarLinksQuery() {
  return getListMyBookableItemCalendarSubscriptionsQueryOptions<
    UserBookingCalendarSubscriptionControllerItemLinkDocument[]
  >({
    query: { select: mapItemLinks },
  });
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
