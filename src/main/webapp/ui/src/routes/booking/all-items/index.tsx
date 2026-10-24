import { createFileRoute } from "@tanstack/react-router";
import AllBookableItemsPage from "@/modules/booking/pages/all-bookable-items/AllBookableItemsPage";
import { allBookableItemsSearch } from "@/modules/booking/pages/all-bookable-items/search";

export const Route = createFileRoute("/booking/all-items/")({
  validateSearch: allBookableItemsSearch,
  component: AllBookableItemsPage,
});
