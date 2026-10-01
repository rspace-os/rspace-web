import { createFileRoute } from "@tanstack/react-router";
import { isPlainDate } from "@/modules/booking/domain/bookingTime";
import CalendarPage from "@/modules/booking/pages/calendar/CalendarPage";

export const Route = createFileRoute("/booking/calendar/")({
  validateSearch: (
    search: Record<string, unknown>,
  ): {
    date?: string;
    target?: string;
  } => ({
    date: typeof search.date === "string" && isPlainDate(search.date) ? search.date : undefined,
    ...(typeof search.target === "string" && /^IN\d+$/.test(search.target) ? { target: search.target } : {}),
  }),
  component: CalendarPage,
});
