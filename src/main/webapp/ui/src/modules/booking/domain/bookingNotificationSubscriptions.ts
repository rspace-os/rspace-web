import { useMutation, useQueryClient } from "@tanstack/react-query";
import * as v from "valibot";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { requestBooking } from "./booking";

export const BookingNotificationPreferencesSchema = v.strictObject({
  autoSubscribeOwnedItems: v.boolean(),
});

export type BookingNotificationPreferences = v.InferOutput<typeof BookingNotificationPreferencesSchema>;

export const BookingNotificationSubscriptionSchema = v.strictObject({
  configurationId: v.pipe(v.number(), v.integer()),
  /** Personal choice, false when version is -1; independent of effective delivery after profile preferences. */
  enabled: v.boolean(),
  /** Version to echo on PUT; -1 means no saved row. */
  version: v.pipe(v.number(), v.integer(), v.minValue(-1)),
  /** Read-only effective delivery after applying enabled and the My Profile booking-created preference. */
  createdEnabled: v.boolean(),
  /** Read-only effective delivery after applying enabled and the My Profile booking-cancelled preference. */
  cancelledEnabled: v.boolean(),
  /** Read-only effective email delivery after event and My Profile email preferences. */
  emailEnabled: v.boolean(),
});

export type BookingNotificationSubscription = v.InferOutput<typeof BookingNotificationSubscriptionSchema>;

const BookingNotificationSubscriptionListSchema = v.strictObject({
  docs: v.array(BookingNotificationSubscriptionSchema),
});
const UpdatedCountSchema = v.strictObject({ updatedCount: v.pipe(v.number(), v.integer(), v.minValue(0)) });

export const bookingNotificationPreferencesQueryKey = (subjectId: number) =>
  ["api-v2", "users", subjectId, "booking-notification-preferences"] as const;

export const bookingNotificationSubscriptionsQueryKey = {
  all: (subjectId: number) => ["api-v2", "users", subjectId, "booking-notification-subscriptions"] as const,
  item: (subjectId: number, configurationId: number) =>
    [...bookingNotificationSubscriptionsQueryKey.all(subjectId), "item", configurationId] as const,
};

const preferencesPath = "/api/v2/users/me/booking-notification-preferences";
const subscriptionsPath = "/api/v2/users/me/booking-notification-subscriptions";

export async function fetchBookingNotificationPreferences(
  token: string,
  signal?: AbortSignal,
): Promise<BookingNotificationPreferences> {
  return requestBooking(preferencesPath, token, BookingNotificationPreferencesSchema, { signal });
}

export async function replaceBookingNotificationPreferences(
  input: BookingNotificationPreferences,
  token: string,
): Promise<BookingNotificationPreferences> {
  return requestBooking(preferencesPath, token, BookingNotificationPreferencesSchema, {
    method: "PUT",
    body: JSON.stringify(v.parse(BookingNotificationPreferencesSchema, input)),
  });
}

export async function fetchBookingNotificationSubscription(
  configurationId: number,
  token: string,
  signal?: AbortSignal,
): Promise<BookingNotificationSubscription> {
  return requestBooking(
    `/api/v2/booking-configurations/${configurationId}/notification-subscription`,
    token,
    BookingNotificationSubscriptionSchema,
    { signal },
  );
}

export async function replaceBookingNotificationSubscription(
  configurationId: number,
  enabled: boolean,
  version: number,
  token: string,
): Promise<BookingNotificationSubscription> {
  return requestBooking(
    `/api/v2/booking-configurations/${configurationId}/notification-subscription`,
    token,
    BookingNotificationSubscriptionSchema,
    {
      method: "PUT",
      body: JSON.stringify({ enabled, version }),
    },
  );
}

export async function updateBookingNotificationSubscriptions(
  configurationIds: readonly number[],
  enabled: boolean,
  token: string,
): Promise<BookingNotificationSubscription[]> {
  return (
    await requestBooking(subscriptionsPath, token, BookingNotificationSubscriptionListSchema, {
      method: "PUT",
      body: JSON.stringify({ configurationIds, enabled }),
    })
  ).docs;
}

export async function unsubscribeFromAllBookingNotificationSubscriptions(token: string): Promise<number> {
  return (await requestBooking(subscriptionsPath, token, UpdatedCountSchema, { method: "DELETE" })).updatedCount;
}

export function useReplaceBookingNotificationPreferences(subjectId: number) {
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: BookingNotificationPreferences) => replaceBookingNotificationPreferences(input, token),
    onSuccess: (preferences) =>
      queryClient.setQueryData(bookingNotificationPreferencesQueryKey(subjectId), preferences),
  });
}
