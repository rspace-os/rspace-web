import { type AnyRoute, createRoute } from "@tanstack/react-router";
import { isPlainDate } from "@/modules/booking/domain/bookingTime";
import AddBookingPage from "./AddBookingPage";
import { BookingDetailsView, BookingEventPage } from "./BookingEventPage";
import BookingInlineEditForm from "./BookingInlineEditForm";

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

export function createBookingEventRouteTree<TParentRoute extends AnyRoute>(bookingRoute: TParentRoute) {
  const eventRoute = createRoute({
    getParentRoute: () => bookingRoute,
    path: "/calendar/bookings/$id",
    component: BookingEventPage,
  });
  const detailsRoute = createRoute({
    getParentRoute: () => eventRoute,
    path: "/",
    component: BookingDetailsView,
  });
  const editRoute = createRoute({
    getParentRoute: () => eventRoute,
    path: "/edit",
    component: BookingInlineEditForm,
  });
  return eventRoute.addChildren({ detailsRoute, editRoute });
}
