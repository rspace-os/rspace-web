import { createFileRoute } from "@tanstack/react-router";
import BookableItemPage from "@/modules/booking/pages/bookable-items/BookableItemPage";
import { bookableItemSearch } from "@/modules/booking/pages/bookable-items/search";

export const Route = createFileRoute("/booking/bookable-items/$globalId/{-$tab}/")({
  validateSearch: bookableItemSearch,
  component: BookableItemPage,
});
