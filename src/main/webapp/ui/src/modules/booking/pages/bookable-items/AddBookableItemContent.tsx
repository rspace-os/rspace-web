import { useQuery, useSuspenseQuery } from "@tanstack/react-query";
import { useSearch } from "@tanstack/react-router";
import { bookingSettingsQuery } from "@/modules/booking/configuration/schedulingSettingsApi";
import { requireBookingValue } from "@/modules/booking/domain/bookingMapping";
import { getSearchBookingConfigurationTargetsQueryOptions } from "@/modules/common/api/v2/generated/booking-configuration-targets/booking-configuration-targets";
import type { BookingConfigurationTarget } from "@/modules/common/api/v2/generated/models/bookingConfigurationTarget";
import { AddBookableItemForm } from "./AddBookableItemForm";
import { AddBookableItemSkeleton } from "./AddBookableItemSkeleton";

export function AddBookableItemContent() {
  const defaults = useSuspenseQuery(bookingSettingsQuery()).data;
  const search = useSearch({ from: "/booking/bookable-items/add/" });
  const routeTargetResults = useQuery(
    getSearchBookingConfigurationTargetsQueryOptions(
      { q: search.target ?? "", limit: 20 },
      {
        query: {
          enabled: search.target !== undefined,
          select: (response) => response.docs.map(requireEligibleBookingTarget),
        },
      },
    ),
  );
  if (search.target !== undefined && routeTargetResults.isPending && !routeTargetResults.isFetched) {
    return <AddBookableItemSkeleton />;
  }
  const initialTarget = routeTargetResults.data?.find((option) => option.globalId === search.target);
  return <AddBookableItemForm key={search.target ?? ""} defaults={defaults} initialTargetId={initialTarget?.id} />;
}

function requireEligibleBookingTarget(target: BookingConfigurationTarget) {
  if (target.deleted !== false) throw new Error("An eligible booking target must not be deleted");
  return {
    ...target,
    id: requireBookingValue(target.id, "id"),
    globalId: requireBookingValue(target.globalId, "globalId"),
    name: requireBookingValue(target.name, "name"),
  };
}
