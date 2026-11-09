import { createFileRoute } from "@tanstack/react-router";
import AddBookableItemPage from "@/modules/booking/pages/bookable-items/AddBookableItemPage";
import { addBookableItemSearch } from "@/modules/booking/pages/bookable-items/search";

export const Route = createFileRoute("/booking/bookable-items/add/")({
  validateSearch: addBookableItemSearch,
  component: AddBookableItemPage,
});
