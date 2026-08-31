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
  subscriptionUrl: v.nullable(HttpUrlSchema),
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

export const CalendarSubscriptionCreatedSchema = v.strictObject({
  ...ActiveCalendarSubscriptionStatusSchema.entries,
  subscriptionUrl: HttpUrlSchema,
});

export type CalendarSubscriptionStatus = v.InferOutput<typeof CalendarSubscriptionStatusSchema>;
export type CalendarSubscriptionCreated = v.InferOutput<typeof CalendarSubscriptionCreatedSchema>;

export const calendarSubscriptionQueryKey = (configurationId: number) =>
  ["api-v2", "booking-configurations", configurationId, "calendar-subscription"] as const;

function calendarSubscriptionPath(configurationId: number): string {
  return `/api/v2/booking-configurations/${configurationId}/calendar-subscription`;
}

const userCalendarSubscriptionPath = "/api/v2/users/me/booking-calendar-subscription";

export const userCalendarSubscriptionQueryKey = ["api-v2", "users", "me", "booking-calendar-subscription"] as const;

async function requireSuccess(response: Response): Promise<Response> {
  if (!response.ok) throw await parseApiV2Problem(response);
  return response;
}

export async function fetchCalendarSubscriptionStatus(
  configurationId: number,
  token: string,
  signal?: AbortSignal,
): Promise<CalendarSubscriptionStatus> {
  const response = await requireSuccess(
    await fetch(calendarSubscriptionPath(configurationId), {
      headers: bookingApiV2Headers(token),
      signal,
    }),
  );
  return parseOrThrow(CalendarSubscriptionStatusSchema, (await response.json()) as unknown);
}

export async function createOrReplaceCalendarSubscription(
  configurationId: number,
  token: string,
): Promise<CalendarSubscriptionCreated> {
  const response = await requireSuccess(
    await fetch(calendarSubscriptionPath(configurationId), {
      method: "POST",
      headers: bookingApiV2Headers(token),
    }),
  );
  return parseOrThrow(CalendarSubscriptionCreatedSchema, (await response.json()) as unknown);
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
): Promise<CalendarSubscriptionStatus> {
  const response = await requireSuccess(
    await fetch(userCalendarSubscriptionPath, {
      headers: bookingApiV2Headers(token),
      signal,
    }),
  );
  return parseOrThrow(CalendarSubscriptionStatusSchema, (await response.json()) as unknown);
}

export async function createOrReplaceUserCalendarSubscription(token: string): Promise<CalendarSubscriptionCreated> {
  const response = await requireSuccess(
    await fetch(userCalendarSubscriptionPath, {
      method: "POST",
      headers: bookingApiV2Headers(token),
    }),
  );
  return parseOrThrow(CalendarSubscriptionCreatedSchema, (await response.json()) as unknown);
}

export async function revokeUserCalendarSubscription(token: string): Promise<void> {
  const response = await fetch(userCalendarSubscriptionPath, {
    method: "DELETE",
    headers: bookingApiV2Headers(token),
  });
  if (response.status !== 204) throw await parseApiV2Problem(response);
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
