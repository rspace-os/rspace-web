import { createFileRoute } from "@tanstack/react-router";
import { myBookingsTable } from "@/modules/booking/pages/my-bookings/bookingList";
import MyBookingsPage from "@/modules/booking/pages/my-bookings/MyBookingsPage";
import { myBookingsSearch } from "@/modules/booking/pages/my-bookings/search";
import { tableViewRouteOptions } from "@/modules/common/table-list/tableViewRoute";

export const Route = createFileRoute("/booking/my-bookings/")({
  validateSearch: myBookingsSearch,
  ...tableViewRouteOptions([myBookingsTable]),
  component: MyBookingsPage,
});
