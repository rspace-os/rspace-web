import { useQuery } from "@tanstack/react-query";
import * as v from "valibot";
import { bookingApiV2Headers } from "@/modules/booking/domain/apiV2";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { retryUnlessClientError } from "../queryRetry";
import { BookingConfigurationRequestError } from "./bookingConfiguration";
import { searchBookingTargets } from "./bookingConfigurationTargets";

// Access to configurations and instruments changes rarely; the existing create, archive and delete
// invalidations of the key prefixes below refresh these sooner.
const ADMINISTRATION_ACCESS_STALE_TIME = 10 * 60 * 1000;

const ReadableCountSchema = v.object({ totalDocs: v.number() });

/** Counts the booking configurations the caller can read, with a one-row page of the list endpoint. */
export async function countReadableBookingConfigurations(token: string, signal?: AbortSignal): Promise<number> {
  const parameters = new URLSearchParams({ limit: "1", "fields[booking-configurations]": "id" });
  const response = await fetch(`/api/v2/booking-configurations?${parameters}`, {
    headers: bookingApiV2Headers(token),
    signal,
  });
  if (!response.ok) throw new BookingConfigurationRequestError(response.status);
  return parseOrThrow(ReadableCountSchema, (await response.json()) as unknown).totalDocs;
}

export const readableBookingConfigurationCountQueryKey = (authScope: number) =>
  ["api-v2", "booking-configurations", "readable-count", authScope] as const;

export const eligibleBookingTargetsQueryKey = (authScope: number) =>
  ["api-v2", "booking-configuration-targets", "browse", authScope] as const;

/** Instruments the caller could set up for booking now: the Add form's blank browse. */
export function useEligibleBookingTargets() {
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const { data: currentUser } = useCurrentUserQuery();
  return useQuery({
    queryKey: eligibleBookingTargetsQueryKey(currentUser.id),
    queryFn: ({ signal }) => searchBookingTargets("", token, signal),
    staleTime: ADMINISTRATION_ACCESS_STALE_TIME,
    retry: retryUnlessClientError,
  });
}

function useReadableBookingConfigurationCount() {
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const { data: currentUser } = useCurrentUserQuery();
  return useQuery({
    queryKey: readableBookingConfigurationCountQueryKey(currentUser.id),
    queryFn: ({ signal }) => countReadableBookingConfigurations(token, signal),
    staleTime: ADMINISTRATION_ACCESS_STALE_TIME,
    retry: retryUnlessClientError,
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
