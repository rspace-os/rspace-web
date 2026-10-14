import { useQuery, useSuspenseQuery } from "@tanstack/react-query";
import { useSearch } from "@tanstack/react-router";
import { loadBookingSettings } from "@/modules/booking/configuration/schedulingSettings";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { AddBookableItemForm, searchBookingTargets } from "./AddBookableItemForm";
import { AddBookableItemSkeleton } from "./AddBookableItemSkeleton";

export function AddBookableItemContent() {
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const defaults = useSuspenseQuery({
    queryKey: ["api-v2", "booking-settings"],
    queryFn: ({ signal }) => loadBookingSettings(token, signal),
  }).data;
  const search = useSearch({ from: "/booking/bookable-items/add" });
  const routeTargetResults = useQuery({
    queryKey: ["api-v2", "booking-configuration-targets", "route-target", search.target],
    queryFn: ({ signal }) => searchBookingTargets(search.target ?? "", token, signal),
    enabled: search.target !== undefined,
  });
  if (search.target !== undefined && routeTargetResults.isPending) return <AddBookableItemSkeleton />;
  const initialTarget = routeTargetResults.data?.find((option) => option.globalId === search.target);
  return (
    <AddBookableItemForm
      key={search.target ?? ""}
      defaults={defaults}
      token={token}
      initialTargetId={initialTarget?.id}
    />
  );
}
