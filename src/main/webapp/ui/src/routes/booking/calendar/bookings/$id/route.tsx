import { createFileRoute } from "@tanstack/react-router";
import { BookingEventPage } from "@/modules/booking/pages/bookings/BookingEventPage";

export const Route = createFileRoute("/booking/calendar/bookings/$id")({
  component: BookingEventPage,
});
