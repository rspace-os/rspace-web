import { queryOptions, useQueryClient } from "@tanstack/react-query";
import * as v from "valibot";
import { bookingSubscriptionKeys } from "@/modules/booking/domain/bookingKeys";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { useApiV2Mutation } from "@/modules/common/queries/apiV2Mutation";
import { apiV2Root } from "@/modules/common/queries/apiV2QueryKeys";
import { requestBooking } from "./booking";

const bookingNotificationPreferenceEntries = {
  autoSubscribeOwnedItems: v.boolean(),
  notifyOwnActions: v.boolean(),
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

const BookingNotificationSubscriptionListSchema = v.strictObject({
  docs: v.array(BookingNotificationSubscriptionSchema),
});
const UpdatedCountSchema = v.strictObject({ updatedCount: v.pipe(v.number(), v.integer(), v.minValue(0)) });

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
  const body = v.parse(v.object(bookingNotificationPreferenceEntries), input);
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
  return useApiV2Mutation({
    meta: { invalidates: [bookingSubscriptionKeys.notifications(subjectId)] },
    mutationFn: (input: BookingNotificationPreferencesInput) => replaceBookingNotificationPreferences(input, token),
    onSuccess: async (preferences) => {
      queryClient.setQueryData(bookingNotificationPreferencesQuery(subjectId, token).queryKey, preferences);
    },
  });
}

export const bookingNotificationPreferencesQuery = (subjectId: number, token: string) =>
  queryOptions({
    queryKey: [...apiV2Root("users"), subjectId, "booking-notification-preferences"] as const,
    queryFn: ({ signal }) => fetchBookingNotificationPreferences(token, signal),
  });

export const bookingNotificationSubscriptionQuery = (subjectId: number, configurationId: number, token: string) =>
  queryOptions({
    queryKey: [...bookingSubscriptionKeys.notifications(subjectId), "item", configurationId] as const,
    queryFn: ({ signal }) => fetchBookingNotificationSubscription(configurationId, token, signal),
  });

export const bookingNotificationPreferencesQueryKey = (subjectId: number) =>
  bookingNotificationPreferencesQuery(subjectId, "").queryKey;
export const bookingNotificationSubscriptionsQueryKey = {
  all: bookingSubscriptionKeys.notifications,
  item: (subjectId: number, configurationId: number) =>
    bookingNotificationSubscriptionQuery(subjectId, configurationId, "").queryKey,
};
