import { type QueryClient, useMutation, useQueryClient } from "@tanstack/react-query";
import type { ErrorType } from "@/modules/common/api/v2/apiV2Fetch";
import {
  getGetMyBookingNotificationSubscriptionQueryKey,
  getGetMyBookingNotificationSubscriptionQueryOptions,
  getMyBookingNotificationSubscription,
  replaceMyBookingNotificationSubscription,
} from "@/modules/common/api/v2/generated/booking-configurations/booking-configurations";
import type { PreferenceReplacement } from "@/modules/common/api/v2/generated/models/preferenceReplacement";
import type { Preferences } from "@/modules/common/api/v2/generated/models/preferences";
import type { Status } from "@/modules/common/api/v2/generated/models/status";
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
export async function fetchBookingNotificationPreferences(signal?: AbortSignal): Promise<Preferences> {
  return getMyBookingNotificationPreferences({ signal });
}
export async function replaceBookingNotificationPreferences(input: PreferenceReplacement): Promise<Preferences> {
  const { autoSubscribeOwnedItems, notifyOwnActions, notifyOnCreated, notifyOnCancelled } = input;
  return replaceMyBookingNotificationPreferences({
    autoSubscribeOwnedItems,
    notifyOwnActions,
    notifyOnCreated,
    notifyOnCancelled,
  });
}
export async function fetchBookingNotificationSubscription(
  configurationId: number,
  signal?: AbortSignal,
): Promise<Status> {
  return getMyBookingNotificationSubscription(configurationId, { signal });
}
export async function replaceBookingNotificationSubscription(
  configurationId: number,
  enabled: boolean,
  version: number,
): Promise<Status> {
  return replaceMyBookingNotificationSubscription(configurationId, { enabled, version });
}
export async function unsubscribeFromAllBookingNotificationSubscriptions(): Promise<number> {
  return requireBookingValue((await unsubscribeMyBookingNotifications()).updatedCount, "updatedCount");
}
export function useReplaceBookingNotificationPreferences(_subjectId: number) {
  const queryClient = useQueryClient();
  const options = getReplaceMyBookingNotificationPreferencesMutationOptions(queryClient);
  return useMutation<Preferences, ErrorType<unknown>, PreferenceReplacement>({
    mutationFn: replaceBookingNotificationPreferences,
    onSuccess: async (data, input, result, context) => {
      queryClient.setQueryData(getGetMyBookingNotificationPreferencesQueryKey(), data);
      await options.onSuccess?.(data, { data: input }, result, context);
      await invalidateBookingNotificationSubscriptions(queryClient);
    },
  });
}

/** Refreshes every cached read of the item notification-subscription operation. */
export async function invalidateBookingNotificationSubscriptions(queryClient: QueryClient): Promise<void> {
  const marker = 2026110731;
  const template = getGetMyBookingNotificationSubscriptionQueryKey(marker)[0];
  const pattern = new RegExp(
    `^${template
      .split(String(marker))
      .map((part) => part.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"))
      .join("\\d+")}$`,
  );
  await queryClient.invalidateQueries({
    predicate: ({ queryKey }) => typeof queryKey[0] === "string" && pattern.test(queryKey[0]),
  });
}
