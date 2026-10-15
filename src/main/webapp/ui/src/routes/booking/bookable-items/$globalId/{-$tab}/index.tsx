import { createFileRoute } from "@tanstack/react-router";
import BookableItemPage from "@/modules/booking/pages/bookable-items/BookableItemPage";
import { bookingConfigurationTargetQuery } from "@/modules/booking/pages/bookable-items/bookingConfigurationApi";
import { bookableItemSearch } from "@/modules/booking/pages/bookable-items/search";
import { oauthTokenQueryOptions } from "@/modules/common/hooks/auth";

export const Route = createFileRoute("/booking/bookable-items/$globalId/{-$tab}/")({
  validateSearch: bookableItemSearch,
  loader: ({ context, params }) => {
    void context.queryClient
      .fetchQuery(oauthTokenQueryOptions(true))
      .then((token) => context.queryClient.ensureQueryData(bookingConfigurationTargetQuery(params.globalId, token)))
      .catch(() => undefined);
  },
  component: BookableItemPage,
});
