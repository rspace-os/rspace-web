import { type AnyRoute, createRoute } from "@tanstack/react-router";
import { isPlainDate } from "@/modules/booking/domain/bookingTime";
import CalendarPage from "./CalendarPage";

export function createCalendarRoute<TParentRoute extends AnyRoute>(bookingRoute: TParentRoute) {
  return createRoute({
    getParentRoute: () => bookingRoute,
    path: "/calendar",
    validateSearch: (search: Record<string, unknown>): { date?: string; target?: string } => ({
      date: typeof search.date === "string" && isPlainDate(search.date) ? search.date : undefined,
      ...(typeof search.target === "string" && /^IN\d+$/.test(search.target) ? { target: search.target } : {}),
    }),
    component: CalendarPage,
  });
}
