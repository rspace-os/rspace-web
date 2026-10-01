import { useMutation, useQueryClient } from "@tanstack/react-query";
import * as v from "valibot";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { requestBooking } from "./booking";

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

const BookingNotificationSubscriptionListSchema = v.array(BookingNotificationSubscriptionSchema);
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
  input: BookingNotificationPreferencesInput,
  token: string,
): Promise<BookingNotificationPreferences> {
  // The API rejects unknown fields, so send exactly the writable preferences.
  const body = v.parse(BookingNotificationPreferencesInputSchema, {
    autoSubscribeOwnedItems: input.autoSubscribeOwnedItems,
    notifyOnCreated: input.notifyOnCreated,
    notifyOnCancelled: input.notifyOnCancelled,
  });
  return requestBooking(preferencesPath, token, BookingNotificationPreferencesSchema, {
    method: "PUT",
    body: JSON.stringify(body),
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
  return requestBooking(subscriptionsPath, token, BookingNotificationSubscriptionListSchema, {
    method: "PUT",
    body: JSON.stringify({ configurationIds, enabled }),
  });
}

export async function unsubscribeFromAllBookingNotificationSubscriptions(token: string): Promise<number> {
  return (await requestBooking(subscriptionsPath, token, UpdatedCountSchema, { method: "DELETE" })).updatedCount;
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
