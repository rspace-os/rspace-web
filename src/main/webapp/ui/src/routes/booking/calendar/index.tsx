import { createFileRoute } from "@tanstack/react-router";
import CalendarPage from "@/modules/booking/pages/calendar/CalendarPage";
import { calendarSearch } from "@/modules/booking/pages/calendar/search";

export const Route = createFileRoute("/booking/calendar/")({
  validateSearch: calendarSearch,
  component: CalendarPage,
});
