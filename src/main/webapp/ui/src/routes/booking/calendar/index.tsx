import { createFileRoute } from "@tanstack/react-router";
import { bookingFormSearch } from "@/modules/booking/pages/bookingFormSearch";
import CalendarPage from "@/modules/booking/pages/calendar/CalendarPage";

export const Route = createFileRoute("/booking/calendar/")({
  validateSearch: bookingFormSearch,
  component: CalendarPage,
});
