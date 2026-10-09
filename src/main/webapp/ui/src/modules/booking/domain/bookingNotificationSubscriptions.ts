import { useMutation, useQueryClient } from "@tanstack/react-query";
import * as v from "valibot";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { bookingApiV2Headers, bookingApiV2JsonHeaders } from "./apiV2";
import { parseApiV2Problem } from "./booking";

const bookingNotificationPreferenceEntries = {
  autoSubscribeOwnedItems: v.boolean(),
  /** The same preference as the My Profile "booking created" notification toggle. */
  notifyOnCreated: v.boolean(),
  /** The same preference as the My Profile "booking cancelled" notification toggle. */
  notifyOnCancelled: v.boolean(),
};

export const BookingNotificationPreferencesSchema = v.strictObject({
  ...bookingNotificationPreferenceEntries,
  /** Read-only: the global My Profile "Send notifications by email" preference. */
  emailDelivery: v.boolean(),
});

export type BookingNotificationPreferences = v.InferOutput<typeof BookingNotificationPreferencesSchema>;

export const BookingNotificationPreferencesInputSchema = v.strictObject(bookingNotificationPreferenceEntries);

export type BookingNotificationPreferencesInput = v.InferOutput<typeof BookingNotificationPreferencesInputSchema>;

export const BookingNotificationSubscriptionSchema = v.strictObject({
  configurationId: v.pipe(v.number(), v.integer()),
  enabled: v.boolean(),
  version: v.pipe(v.number(), v.integer(), v.minValue(-1)),
  createdEnabled: v.boolean(),
  cancelledEnabled: v.boolean(),
  emailEnabled: v.boolean(),
});

export type BookingNotificationSubscription = v.InferOutput<typeof BookingNotificationSubscriptionSchema>;

const BookingNotificationSubscriptionListSchema = v.array(BookingNotificationSubscriptionSchema);
const UpdatedCountSchema = v.strictObject({ updatedCount: v.pipe(v.number(), v.integer(), v.minValue(0)) });

export const bookingNotificationPreferencesQueryKey = (subjectId: number) =>
  ["api-v2", "users", subjectId, "booking-notification-preferences"] as const;

export const bookingNotificationSubscriptionsQueryKey = {
  all: (subjectId: number) => ["api-v2", "users", subjectId, "booking-notification-subscriptions"] as const,
  item: (subjectId: number, configurationId: number) =>
    [...bookingNotificationSubscriptionsQueryKey.all(subjectId), "item", configurationId] as const,
  list: (subjectId: number, configurationIds: readonly number[]) =>
    [...bookingNotificationSubscriptionsQueryKey.all(subjectId), "list", ...configurationIds] as const,
};

const preferencesPath = "/api/v2/users/me/booking-notification-preferences";
const subscriptionsPath = "/api/v2/users/me/booking-notification-subscriptions";

async function requireSuccess(response: Response): Promise<Response> {
  if (!response.ok) throw await parseApiV2Problem(response);
  return response;
}

export async function fetchBookingNotificationPreferences(
  token: string,
  signal?: AbortSignal,
): Promise<BookingNotificationPreferences> {
  const response = await requireSuccess(await fetch(preferencesPath, { headers: bookingApiV2Headers(token), signal }));
  return parseOrThrow(BookingNotificationPreferencesSchema, await response.json());
}

export async function replaceBookingNotificationPreferences(
  input: BookingNotificationPreferencesInput,
  token: string,
): Promise<BookingNotificationPreferences> {
  // The API rejects unknown fields, so send exactly the writable preferences.
  const body = v.parse(BookingNotificationPreferencesInputSchema, {
    autoSubscribeOwnedItems: input.autoSubscribeOwnedItems,
    notifyOnCreated: input.notifyOnCreated,
    notifyOnCancelled: input.notifyOnCancelled,
  });
  const response = await requireSuccess(
    await fetch(preferencesPath, {
      method: "PUT",
      headers: bookingApiV2JsonHeaders(token),
      body: JSON.stringify(body),
    }),
  );
  return parseOrThrow(BookingNotificationPreferencesSchema, await response.json());
}

export async function fetchBookingNotificationSubscription(
  configurationId: number,
  token: string,
  signal?: AbortSignal,
): Promise<BookingNotificationSubscription> {
  const response = await requireSuccess(
    await fetch(`/api/v2/booking-configurations/${configurationId}/notification-subscription`, {
      headers: bookingApiV2Headers(token),
      signal,
    }),
  );
  return parseOrThrow(BookingNotificationSubscriptionSchema, await response.json());
}

export async function replaceBookingNotificationSubscription(
  configurationId: number,
  enabled: boolean,
  version: number,
  token: string,
): Promise<BookingNotificationSubscription> {
  const response = await requireSuccess(
    await fetch(`/api/v2/booking-configurations/${configurationId}/notification-subscription`, {
      method: "PUT",
      headers: bookingApiV2JsonHeaders(token),
      body: JSON.stringify({ enabled, version }),
    }),
  );
  return parseOrThrow(BookingNotificationSubscriptionSchema, await response.json());
}

export async function lookupBookingNotificationSubscriptions(
  configurationIds: readonly number[],
  token: string,
  signal?: AbortSignal,
): Promise<BookingNotificationSubscription[]> {
  if (configurationIds.length === 0) return [];
  const response = await requireSuccess(
    await fetch(`${subscriptionsPath}/lookup`, {
      method: "POST",
      headers: bookingApiV2JsonHeaders(token),
      body: JSON.stringify({ configurationIds }),
      signal,
    }),
  );
  return parseOrThrow(BookingNotificationSubscriptionListSchema, await response.json());
}

export async function updateBookingNotificationSubscriptions(
  configurationIds: readonly number[],
  enabled: boolean,
  token: string,
): Promise<BookingNotificationSubscription[]> {
  const response = await requireSuccess(
    await fetch(subscriptionsPath, {
      method: "PUT",
      headers: bookingApiV2JsonHeaders(token),
      body: JSON.stringify({ configurationIds, enabled }),
    }),
  );
  return parseOrThrow(BookingNotificationSubscriptionListSchema, await response.json());
}

export async function unsubscribeFromAllBookingNotificationSubscriptions(token: string): Promise<number> {
  const response = await requireSuccess(
    await fetch(subscriptionsPath, { method: "DELETE", headers: bookingApiV2Headers(token) }),
  );
  return parseOrThrow(UpdatedCountSchema, await response.json()).updatedCount;
}

export function useReplaceBookingNotificationPreferences(subjectId: number) {
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: BookingNotificationPreferencesInput) => replaceBookingNotificationPreferences(input, token),
    onSuccess: async (preferences) => {
      queryClient.setQueryData(bookingNotificationPreferencesQueryKey(subjectId), preferences);
      // Item subscriptions report the created/cancelled toggles as their effective state.
      await queryClient.invalidateQueries({ queryKey: bookingNotificationSubscriptionsQueryKey.all(subjectId) });
    },
  });
}
