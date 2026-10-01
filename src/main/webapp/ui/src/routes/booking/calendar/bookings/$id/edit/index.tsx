import { createFileRoute } from "@tanstack/react-router";
import BookingInlineEditForm from "@/modules/booking/pages/bookings/BookingInlineEditForm";

export const Route = createFileRoute("/booking/calendar/bookings/$id/edit/")({
  component: BookingInlineEditForm,
});
