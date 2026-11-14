import { createFileRoute } from "@tanstack/react-router";
import BookableItemPage from "@/modules/booking/pages/bookable-items/BookableItemPage";
import { bookingConfigurationTargetQuery } from "@/modules/booking/pages/bookable-items/bookingConfigurationApi";
import { bookableItemSearch } from "@/modules/booking/pages/bookable-items/search";

export const Route = createFileRoute("/booking/bookable-items/$globalId/{-$tab}/")({
  validateSearch: bookableItemSearch,
  loader: ({ context, params }) => {
    void context.queryClient.ensureQueryData(bookingConfigurationTargetQuery(params.globalId)).catch(() => undefined);
  },
  component: BookableItemPage,
});
