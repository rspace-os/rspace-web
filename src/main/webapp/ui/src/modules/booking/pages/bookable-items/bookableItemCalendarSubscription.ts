import * as v from "valibot";
import { bookingApiV2Headers } from "@/modules/booking/domain/apiV2";
import { parseApiV2Problem } from "@/modules/booking/domain/booking";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";

const HttpUrlSchema = v.pipe(
  v.string(),
  v.url(),
  v.check((url) => url.startsWith("http://") || url.startsWith("https://")),
);

const ActiveCalendarSubscriptionStatusSchema = v.strictObject({
  active: v.literal(true),
  updatedAt: v.pipe(v.string(), v.isoTimestamp()),
  subscriptionUrl: HttpUrlSchema,
});

const InactiveCalendarSubscriptionStatusSchema = v.strictObject({
  active: v.literal(false),
  updatedAt: v.null(),
  subscriptionUrl: v.optional(v.null(), null),
});

export const CalendarSubscriptionStatusSchema = v.variant("active", [
  ActiveCalendarSubscriptionStatusSchema,
  InactiveCalendarSubscriptionStatusSchema,
]);

export const CalendarSubscriptionCreatedSchema = ActiveCalendarSubscriptionStatusSchema;

const ItemCalendarLinksSchema = v.array(
  v.strictObject({
    configurationId: v.number(),
    itemGlobalId: v.string(),
    itemName: v.string(),
    updatedAt: v.pipe(v.string(), v.isoTimestamp()),
    subscriptionUrl: HttpUrlSchema,
  }),
);

export type CalendarSubscriptionStatus = v.InferOutput<typeof CalendarSubscriptionStatusSchema>;
export type CalendarSubscriptionCreated = v.InferOutput<typeof CalendarSubscriptionCreatedSchema>;
export type UserCalendarSubscriptionStatus = CalendarSubscriptionStatus & { etag: string };
export type UserCalendarSubscriptionCreated = CalendarSubscriptionCreated & { etag: string };
export type ItemCalendarLink = v.InferOutput<typeof ItemCalendarLinksSchema>[number];

export const calendarSubscriptionQueryKey = (configurationId: number) =>
  ["api-v2", "booking-configurations", configurationId, "calendar-subscription"] as const;

function calendarSubscriptionPath(configurationId: number): string {
  return `/api/v2/booking-configurations/${configurationId}/calendar-subscription`;
}

const userCalendarSubscriptionPath = "/api/v2/users/me/booking-calendar-subscription";

export const userCalendarSubscriptionQueryKey = ["api-v2", "users", "me", "booking-calendar-subscription"] as const;

const itemCalendarLinksPath = "/api/v2/users/me/bookable-item-calendar-subscriptions";

export const itemCalendarLinksQueryKey = ["api-v2", "users", "me", "bookable-item-calendar-subscriptions"] as const;

async function requireSuccess(response: Response): Promise<Response> {
  if (!response.ok) throw await parseApiV2Problem(response);
  return response;
}

function requireEtag(response: Response): string {
  const etag = response.headers.get("ETag");
  if (etag === null) throw new Error("Calendar subscription response did not include an ETag");
  return etag;
}

export async function fetchCalendarSubscriptionStatus(
  configurationId: number,
  token: string,
  signal?: AbortSignal,
): Promise<UserCalendarSubscriptionStatus> {
  const response = await requireSuccess(
    await fetch(calendarSubscriptionPath(configurationId), {
      headers: bookingApiV2Headers(token),
      signal,
    }),
  );
  const status = parseOrThrow(CalendarSubscriptionStatusSchema, (await response.json()) as unknown);
  return { ...status, etag: requireEtag(response) };
}

/** Returns the caller's link, issuing one only when none exists. Repeating it is safe. */
export async function createCalendarSubscription(
  configurationId: number,
  token: string,
): Promise<UserCalendarSubscriptionCreated> {
  return issueLink(calendarSubscriptionPath(configurationId), token);
}

/** Replaces the caller's link. Calendars using the old one stop updating. */
export async function rotateCalendarSubscription(
  configurationId: number,
  token: string,
  etag: string,
): Promise<UserCalendarSubscriptionCreated> {
  return issueLink(`${calendarSubscriptionPath(configurationId)}/rotate`, token, etag);
}

async function issueLink(path: string, token: string, etag?: string): Promise<UserCalendarSubscriptionCreated> {
  const response = await requireSuccess(
    await fetch(path, {
      method: "POST",
      headers: bookingApiV2Headers(token, etag === undefined ? undefined : { "If-Match": etag }),
    }),
  );
  const created = parseOrThrow(CalendarSubscriptionCreatedSchema, (await response.json()) as unknown);
  return { ...created, etag: requireEtag(response) };
}

export async function revokeCalendarSubscription(configurationId: number, token: string): Promise<void> {
  const response = await fetch(calendarSubscriptionPath(configurationId), {
    method: "DELETE",
    headers: bookingApiV2Headers(token),
  });
  if (response.status !== 204) throw await parseApiV2Problem(response);
}

export async function fetchUserCalendarSubscriptionStatus(
  token: string,
  signal?: AbortSignal,
): Promise<UserCalendarSubscriptionStatus> {
  const response = await requireSuccess(
    await fetch(userCalendarSubscriptionPath, {
      headers: bookingApiV2Headers(token),
      signal,
    }),
  );
  const status = parseOrThrow(CalendarSubscriptionStatusSchema, (await response.json()) as unknown);
  return { ...status, etag: requireEtag(response) };
}

export async function createUserCalendarSubscription(token: string): Promise<UserCalendarSubscriptionCreated> {
  return issueLink(userCalendarSubscriptionPath, token);
}

export async function rotateUserCalendarSubscription(
  token: string,
  etag: string,
): Promise<UserCalendarSubscriptionCreated> {
  return issueLink(`${userCalendarSubscriptionPath}/rotate`, token, etag);
}

export async function revokeUserCalendarSubscription(token: string): Promise<void> {
  const response = await fetch(userCalendarSubscriptionPath, {
    method: "DELETE",
    headers: bookingApiV2Headers(token),
  });
  if (response.status !== 204) throw await parseApiV2Problem(response);
}

export async function fetchItemCalendarLinks(token: string, signal?: AbortSignal): Promise<ItemCalendarLink[]> {
  const response = await requireSuccess(
    await fetch(itemCalendarLinksPath, { headers: bookingApiV2Headers(token), signal }),
  );
  return parseOrThrow(ItemCalendarLinksSchema, (await response.json()) as unknown);
}

export function toWebcalUrl(feedUrl: string): string {
  if (feedUrl.startsWith("https://")) return `webcal://${feedUrl.slice("https://".length)}`;
  if (feedUrl.startsWith("http://")) return `webcal://${feedUrl.slice("http://".length)}`;
  throw new Error("Calendar subscription URL must use HTTP or HTTPS");
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
