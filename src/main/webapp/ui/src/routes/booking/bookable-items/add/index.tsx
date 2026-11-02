import { createFileRoute } from "@tanstack/react-router";
import { bookingSettingsQuery } from "@/modules/booking/configuration/schedulingSettingsApi";
import AddBookableItemPage from "@/modules/booking/pages/bookable-items/AddBookableItemPage";
import { bookingRouteTargetQuery } from "@/modules/booking/pages/bookable-items/bookingConfigurationApi";
import { addBookableItemSearch } from "@/modules/booking/pages/bookable-items/search";
import { oauthTokenQueryOptions } from "@/modules/common/hooks/auth";

export const Route = createFileRoute("/booking/bookable-items/add/")({
  validateSearch: addBookableItemSearch,
  loaderDeps: ({ search }) => ({ target: search.target }),
  loader: ({ context, deps }) => {
    void context.queryClient
      .fetchQuery(oauthTokenQueryOptions(true))
      .then((token) =>
        Promise.all([
          context.queryClient.ensureQueryData(bookingSettingsQuery(token)),
          ...(deps.target === undefined
            ? []
            : [context.queryClient.ensureQueryData(bookingRouteTargetQuery(deps.target, token))]),
        ]),
      )
      .catch(() => undefined);
  },
  component: AddBookableItemPage,
});
