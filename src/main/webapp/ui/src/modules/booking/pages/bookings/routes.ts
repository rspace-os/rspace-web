import { type AnyRoute, createRoute } from "@tanstack/react-router";
import { isPlainDate } from "@/modules/booking/domain/bookingTime";
import AddBookingPage from "./AddBookingPage";
import EditBookingPage from "./EditBookingPage";

export function bookingFormSearch(search: Record<string, unknown>) {
  return {
    ...(typeof search.date === "string" && isPlainDate(search.date) ? { date: search.date } : {}),
    ...(typeof search.target === "string" && /^IN\d+$/.test(search.target) ? { target: search.target } : {}),
  };
}

export function createAddBookingRoute<TParentRoute extends AnyRoute>(bookingRoute: TParentRoute) {
  return createRoute({
    getParentRoute: () => bookingRoute,
    path: "/calendar/bookings/add",
    validateSearch: bookingFormSearch,
    component: AddBookingPage,
  });
}

export function createEditBookingRoute<TParentRoute extends AnyRoute>(bookingRoute: TParentRoute) {
  return createRoute({
    getParentRoute: () => bookingRoute,
    path: "/calendar/bookings/$id",
    validateSearch: bookingFormSearch,
    component: EditBookingPage,
  });
}
