import { createFileRoute } from "@tanstack/react-router";
import BookableItemsPage from "@/modules/booking/pages/bookable-items/BookableItemsPage";

export const Route = createFileRoute("/booking/config/bookable-items/")({
  component: BookableItemsPage,
});
