import { type AnyRoute, createRoute } from "@tanstack/react-router";
import { isPlainDate } from "@/modules/booking/domain/bookingTime";
import CalendarPage from "./CalendarPage";

export function createCalendarRoute<TParentRoute extends AnyRoute>(bookingRoute: TParentRoute) {
  return createRoute({
    getParentRoute: () => bookingRoute,
    path: "/calendar",
    validateSearch: (search: Record<string, unknown>): { date?: string; target?: string } => ({
      ...(typeof search.date === "string" && isPlainDate(search.date) ? { date: search.date } : {}),
      ...(typeof search.target === "string" && /^IN\d+$/.test(search.target) ? { target: search.target } : {}),
    }),
    component: CalendarPage,
  });
}
