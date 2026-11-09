import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { ErrorType } from "@/modules/common/api/v2/apiV2Fetch";
import {
  getGetMyBookingNotificationSubscriptionQueryOptions,
  getMyBookingNotificationSubscription,
  replaceMyBookingNotificationSubscription,
} from "@/modules/common/api/v2/generated/booking-configurations/booking-configurations";
import type { BookingNotificationSubscriptionControllerPreferenceReplacement } from "@/modules/common/api/v2/generated/models/bookingNotificationSubscriptionControllerPreferenceReplacement";
import type { BookingNotificationSubscriptionManagerPreferences } from "@/modules/common/api/v2/generated/models/bookingNotificationSubscriptionManagerPreferences";
import {
  getGetMyBookingNotificationPreferencesQueryKey,
  getGetMyBookingNotificationPreferencesSuspenseQueryOptions,
  getMyBookingNotificationPreferences,
  getReplaceMyBookingNotificationPreferencesMutationOptions,
  replaceMyBookingNotificationPreferences,
  unsubscribeMyBookingNotifications,
} from "@/modules/common/api/v2/generated/users/users";
import { requireBookingValue } from "./bookingMapping";

export function bookingNotificationPreferencesQuery(_subjectId: number) {
  return getGetMyBookingNotificationPreferencesSuspenseQueryOptions();
}
export function bookingNotificationSubscriptionQuery(_subjectId: number, configurationId: number) {
  return getGetMyBookingNotificationSubscriptionQueryOptions(configurationId);
}
export async function fetchBookingNotificationPreferences(
  signal?: AbortSignal,
): Promise<BookingNotificationSubscriptionManagerPreferences> {
  return getMyBookingNotificationPreferences({ signal });
}
export async function replaceBookingNotificationPreferences(
  input: BookingNotificationSubscriptionControllerPreferenceReplacement,
): Promise<BookingNotificationSubscriptionManagerPreferences> {
  return replaceMyBookingNotificationPreferences(input);
}
export async function fetchBookingNotificationSubscription(configurationId: number, signal?: AbortSignal) {
  return getMyBookingNotificationSubscription(configurationId, { signal });
}
export async function replaceBookingNotificationSubscription(configurationId: number, enabled: boolean, etag: string) {
  return replaceMyBookingNotificationSubscription(configurationId, { enabled }, { "If-Match": etag });
}
export async function unsubscribeFromAllBookingNotificationSubscriptions(): Promise<number> {
  return requireBookingValue((await unsubscribeMyBookingNotifications()).updatedCount, "updatedCount");
}
export function useReplaceBookingNotificationPreferences(_subjectId: number) {
  const queryClient = useQueryClient();
  const options = getReplaceMyBookingNotificationPreferencesMutationOptions(queryClient);
  return useMutation<
    BookingNotificationSubscriptionManagerPreferences,
    ErrorType<unknown>,
    BookingNotificationSubscriptionControllerPreferenceReplacement
  >({
    mutationFn: replaceBookingNotificationPreferences,
    onSuccess: async (data, input, result, context) => {
      queryClient.setQueryData(getGetMyBookingNotificationPreferencesQueryKey(), data);
      await options.onSuccess?.(data, { data: input }, result, context);
    },
  });
}
