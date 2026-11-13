import { useQuery, useSuspenseQuery } from "@tanstack/react-query";
import { useSearch } from "@tanstack/react-router";
import { bookingSettingsQuery } from "@/modules/booking/configuration/schedulingSettingsApi";
import { AddBookableItemForm } from "./AddBookableItemForm";
import { AddBookableItemSkeleton } from "./AddBookableItemSkeleton";
import { bookingRouteTargetQuery } from "./bookingConfigurationApi";

export function AddBookableItemContent() {
  const defaults = useSuspenseQuery(bookingSettingsQuery()).data;
  const search = useSearch({ from: "/booking/bookable-items/add/" });
  const routeTargetResults = useQuery(bookingRouteTargetQuery(search.target));
  if (search.target !== undefined && routeTargetResults.isPending && !routeTargetResults.isFetched) {
    return <AddBookableItemSkeleton />;
  }
  const initialTarget = routeTargetResults.data?.find((option) => option.globalId === search.target);
  return <AddBookableItemForm key={search.target ?? ""} defaults={defaults} initialTargetId={initialTarget?.id} />;
}
