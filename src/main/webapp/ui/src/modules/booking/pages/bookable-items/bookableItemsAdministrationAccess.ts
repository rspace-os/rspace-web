import { queryOptions, useQuery } from "@tanstack/react-query";
import * as v from "valibot";
import { bookingConfigurationKeys } from "@/modules/booking/domain/bookingKeys";
import { eligibleBookingTargetsQuery } from "@/modules/booking/pages/bookable-items/bookingConfigurationTargets";
import { apiV2Headers } from "@/modules/common/api/apiV2Headers";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { retryUnlessClientError } from "@/modules/common/queries/queryRetry";
import { fetchWithUiToken } from "@/modules/common/utils/fetchWithUiToken";
import { BookingConfigurationRequestError } from "./bookingConfigurationApi";

/** Instruments the caller could set up for booking now: the Add form's blank browse. */
export function useEligibleBookingTargets() {
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const { data: currentUser } = useCurrentUserQuery();
  return useQuery({
    ...eligibleBookingTargetsQuery(currentUser.id, token),
  });
}

function useReadableBookingConfigurationCount() {
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const { data: currentUser } = useCurrentUserQuery();
  return useQuery({
    ...readableBookingConfigurationCountQuery(currentUser.id, token),
  });
}

/**
 * Administration > Bookable Items is only useful to a user who can read at least one booking
 * configuration or set up an eligible instrument. False while either signal is still loading or failed.
 */
export function useCanOpenBookableItemsAdministration(): boolean {
  const readable = useReadableBookingConfigurationCount();
  const eligible = useEligibleBookingTargets();
  return (readable.data ?? 0) > 0 || (eligible.data?.length ?? 0) > 0;
}

const ReadableCountSchema = v.object({ totalDocs: v.number() });
async function countReadableBookingConfigurations(token: string, signal: AbortSignal): Promise<number> {
  const parameters = new URLSearchParams({ max: "1" });
  const response = await fetchWithUiToken(`/api/v2/booking-configurations/count?${parameters}`, {
    headers: apiV2Headers(token),
    signal,
  });
  if (!response.ok) throw new BookingConfigurationRequestError(response.status);
  return parseOrThrow(ReadableCountSchema, (await response.json()) as unknown).totalDocs;
}

export const readableBookingConfigurationCountQuery = (scope: number, token: string) =>
  queryOptions({
    queryKey: [...bookingConfigurationKeys.all, "readable-count", scope] as const,
    queryFn: ({ signal }) => countReadableBookingConfigurations(token, signal),
    retry: retryUnlessClientError,
    staleTime: 10 * 60 * 1000,
  });
