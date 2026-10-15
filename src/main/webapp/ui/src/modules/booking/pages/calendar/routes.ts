import { type AnyRoute, createRoute } from "@tanstack/react-router";
import { bookingFormSearch } from "../bookingFormSearch";
import CalendarPage from "./CalendarPage";

export function createCalendarRoute<TParentRoute extends AnyRoute>(bookingRoute: TParentRoute) {
  return createRoute({
    getParentRoute: () => bookingRoute,
    path: "/calendar",
    validateSearch: bookingFormSearch,
    component: CalendarPage,
  });
}
