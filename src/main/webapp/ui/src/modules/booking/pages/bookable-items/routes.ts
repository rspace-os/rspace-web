import { type AnyRoute, createRoute } from "@tanstack/react-router";
import { createLoader, parseAsBoolean, parseAsStringLiteral } from "nuqs";
import AddBookableItemPage from "./AddBookableItemPage";
import ArchivedBookableItemPage from "./ArchivedBookableItemPage";
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

// Tab and edit mode live in the URL so both are linkable, back-button-able and
// survive a reload. `history: "replace"` keeps the two from filling the stack.
export const bookableItemTabParser = parseAsStringLiteral(["bookings", "details", "audit", "access"] as const)
  .withDefault("bookings")
  .withOptions({ history: "replace", clearOnDefault: true });
export const bookableItemEditParser = parseAsBoolean
  .withDefault(false)
  .withOptions({ history: "replace", clearOnDefault: true });

const loadBookableItemSearch = createLoader({ tab: bookableItemTabParser, edit: bookableItemEditParser });

// Both keys stay optional in the output. A required key would force every
// existing `<Link to="/booking/bookable-items/$globalId">` to pass `search`,
// and `clearOnDefault` already means the defaults never appear in the URL.
export function bookableItemSearch(search: Record<string, unknown>): {
  tab?: "bookings" | "details" | "audit" | "access";
  edit?: boolean;
} {
  const loaded = loadBookableItemSearch({
    tab: typeof search.tab === "string" ? search.tab : undefined,
    edit: typeof search.edit === "string" || typeof search.edit === "boolean" ? String(search.edit) : undefined,
  });
  return {
    ...(loaded.tab === "bookings" ? {} : { tab: loaded.tab }),
    ...(loaded.edit ? { edit: true } : {}),
  };
}

export function createBookableItemRoute<TParentRoute extends AnyRoute>(bookingRoute: TParentRoute) {
  return createRoute({
    getParentRoute: () => bookingRoute,
    path: "/bookable-items/$globalId",
    validateSearch: bookableItemSearch,
    component: BookableItemPage,
  });
}

export function createArchivedBookableItemRoute<TParentRoute extends AnyRoute>(bookingRoute: TParentRoute) {
  return createRoute({
    getParentRoute: () => bookingRoute,
    path: "/bookable-items/archived/$id",
    component: ArchivedBookableItemPage,
  });
}
