import { type AnyRoute, createRoute } from "@tanstack/react-router";
import { createLoader, parseAsBoolean } from "nuqs";
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
    path: "/bookable-items/add",
    validateSearch: (search: Record<string, unknown>): { target?: string } => ({
      ...(typeof search.target === "string" && /^IN\d+$/.test(search.target) ? { target: search.target } : {}),
    }),
    component: AddBookableItemPage,
  });
}

export const bookableItemEditParser = parseAsBoolean
  .withDefault(false)
  .withOptions({ history: "replace", clearOnDefault: true });

const loadBookableItemSearch = createLoader({ edit: bookableItemEditParser });

export function bookableItemSearch(search: Record<string, unknown>): { edit?: boolean } {
  const loaded = loadBookableItemSearch({
    edit: typeof search.edit === "string" || typeof search.edit === "boolean" ? String(search.edit) : undefined,
  });
  return loaded.edit ? { edit: true } : {};
}

export function createBookableItemRoute<TParentRoute extends AnyRoute>(bookingRoute: TParentRoute) {
  return createRoute({
    getParentRoute: () => bookingRoute,
    path: "/bookable-items/$globalId/{-$tab}",
    validateSearch: bookableItemSearch,
    component: BookableItemPage,
  });
}
