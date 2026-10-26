import { createFileRoute } from "@tanstack/react-router";
import { BookingDetailsView } from "@/modules/booking/pages/bookings/BookingDetailsView";

export const Route = createFileRoute("/booking/calendar/bookings/$id/")({
  component: BookingDetailsView,
});
