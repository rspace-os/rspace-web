import { type AnyRoute, createRoute } from "@tanstack/react-router";
import { bookingFormSearch } from "../bookingFormSearch";
import AddBookableItemPage from "./AddBookableItemPage";
import BookableItemPage from "./BookableItemPage";
import BookableItemsPage from "./BookableItemsPage";
import BookingSettingsRoutePage from "./BookingSettingsRoutePage";

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
    // Guards in the component: the router has no current-user context to check in beforeLoad.
    component: BookingSettingsRoutePage,
  });
}

export function createAddBookableItemRoute<TParentRoute extends AnyRoute>(bookingRoute: TParentRoute) {
  return createRoute({
    getParentRoute: () => bookingRoute,
    path: "/bookable-items/add",
    validateSearch: (search: Record<string, unknown>): { target?: string } => {
      const { target } = bookingFormSearch(search);
      return target === undefined ? {} : { target };
    },
    component: AddBookableItemPage,
  });
}

export function bookableItemSearch(search: Record<string, unknown>): { edit?: boolean } {
  return search.edit === true || search.edit === "true" ? { edit: true } : {};
}

export function createBookableItemRoute<TParentRoute extends AnyRoute>(bookingRoute: TParentRoute) {
  return createRoute({
    getParentRoute: () => bookingRoute,
    path: "/bookable-items/$globalId/{-$tab}",
    validateSearch: bookableItemSearch,
    component: BookableItemPage,
  });
}
