import { createFileRoute } from "@tanstack/react-router";
import MyBookingsPage from "@/modules/booking/pages/my-bookings/MyBookingsPage";
import { myBookingsSearch } from "@/modules/booking/pages/my-bookings/search";

export const Route = createFileRoute("/booking/my-bookings/")({
  validateSearch: myBookingsSearch,
  component: MyBookingsPage,
});
