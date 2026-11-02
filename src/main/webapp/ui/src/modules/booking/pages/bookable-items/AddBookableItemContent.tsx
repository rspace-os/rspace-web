import { useQuery, useSuspenseQuery } from "@tanstack/react-query";
import { useSearch } from "@tanstack/react-router";
import { bookingSettingsQuery } from "@/modules/booking/configuration/schedulingSettingsApi";
import { bookingRouteTargetQuery } from "@/modules/booking/pages/bookable-items/bookingConfigurationApi";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { AddBookableItemForm } from "./AddBookableItemForm";
import { AddBookableItemSkeleton } from "./AddBookableItemSkeleton";

export function AddBookableItemContent() {
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const defaults = useSuspenseQuery({
    ...bookingSettingsQuery(token),
  }).data;
  const search = useSearch({ from: "/booking/bookable-items/add/" });
  const routeTargetResults = useQuery({
    ...bookingRouteTargetQuery(search.target, token),
  });
  if (search.target !== undefined && routeTargetResults.isPending && !routeTargetResults.isFetched) {
    return <AddBookableItemSkeleton />;
  }
  const initialTarget = routeTargetResults.data?.find((option) => option.globalId === search.target);
  return (
    <AddBookableItemForm
      key={search.target ?? ""}
      defaults={defaults}
      token={token}
      initialTargetId={initialTarget?.id}
      initialTargetName={initialTarget?.name}
    />
  );
}
