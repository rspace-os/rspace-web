import { type AnyRoute, createRoute } from "@tanstack/react-router";
import AddBookableItemPage from "./AddBookableItemPage";
import BookableItemPage from "./BookableItemPage";
import BookableItemsPage from "./BookableItemsPage";

export function createBookableItemsRoute<TParentRoute extends AnyRoute>(bookingRoute: TParentRoute) {
  return createRoute({
    getParentRoute: () => bookingRoute,
    path: "/config/bookable-items",
    component: BookableItemsPage,
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
