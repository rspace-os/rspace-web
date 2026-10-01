import { createFileRoute } from "@tanstack/react-router";
import AddBookingPage from "@/modules/booking/pages/bookings/AddBookingPage";

export const Route = createFileRoute("/booking/calendar/bookings/add/")({
  validateSearch: bookingFormSearch,
  component: AddBookingPage,
});
