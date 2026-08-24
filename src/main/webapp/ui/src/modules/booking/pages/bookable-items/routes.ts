import { type AnyRoute, createRoute } from "@tanstack/react-router";
import AddBookableItemPage from "./AddBookableItemPage";
import BookableItemPage from "./BookableItemPage";
import BookableItemsPage from "./BookableItemsPage";
import BookingSettingsPage from "./BookingSettingsPage";

export function createBookableItemsRoute<TParentRoute extends AnyRoute>(bookingRoute: TParentRoute) {
  return createRoute({
    getParentRoute: () => bookingRoute,
    path: "/config/bookable-items",
    component: BookableItemsPage,
  });
}

export function createBookingSettingsRoute<TParentRoute extends AnyRoute>(bookingRoute: TParentRoute) {
  return createRoute({
    getParentRoute: () => bookingRoute,
    path: "/config/settings",
    component: BookingSettingsPage,
  });
}

export function createAddBookableItemRoute<TParentRoute extends AnyRoute>(bookingRoute: TParentRoute) {
  return createRoute({
    getParentRoute: () => bookingRoute,
    path: "/config/bookable-items/add",
    component: AddBookableItemPage,
  });
}

export function createBookableItemRoute<TParentRoute extends AnyRoute>(bookingRoute: TParentRoute) {
  return createRoute({
    getParentRoute: () => bookingRoute,
    path: "/config/bookable-items/$id",
    component: BookableItemPage,
  });
}
