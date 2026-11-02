import { type AnyRoute, createRoute } from "@tanstack/react-router";
import { BookingDetailsView } from "@/modules/booking/pages/bookings/BookingDetailsView";
import { BookingEventPage } from "@/modules/booking/pages/bookings/BookingEventPage";
import BookingInlineEditForm from "@/modules/booking/pages/bookings/BookingInlineEditForm";

export function createBookingEventTestRouteTree<TParent extends AnyRoute>(booking: TParent) {
  const event = createRoute({
    getParentRoute: () => booking,
    path: "/calendar/bookings/$id",
    component: BookingEventPage,
  });
  return event.addChildren([
    createRoute({ getParentRoute: () => event, path: "/", component: BookingDetailsView }),
    createRoute({ getParentRoute: () => event, path: "/edit/", component: BookingInlineEditForm }),
  ]);
}
