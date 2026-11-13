import { createFileRoute } from "@tanstack/react-router";
import AllBookableItemsPage from "@/modules/booking/pages/all-bookable-items/AllBookableItemsPage";

export const Route = createFileRoute("/booking/config/bookable-items/")({
  component: AllBookableItemsPage,
});
