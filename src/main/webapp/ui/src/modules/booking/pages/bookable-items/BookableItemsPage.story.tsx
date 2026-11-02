import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  Outlet,
  RouterProvider,
} from "@tanstack/react-router";
import { NuqsAdapter } from "nuqs/adapters/react";
import { Suspense } from "react";
import BookableItemPage from "@/modules/booking/pages/bookable-items/BookableItemPage";
import BookableItemsPage from "@/modules/booking/pages/bookable-items/BookableItemsPage";
import { bookableItemSearch } from "@/modules/booking/pages/bookable-items/search";

export function BookableItemsStory() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const rootRoute = createRootRoute({ component: Outlet });
  const bookingRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: "/booking",
    component: Outlet,
  });
  const routeTree = rootRoute.addChildren([
    bookingRoute.addChildren([
      createRoute({
        getParentRoute: () => bookingRoute,
        path: "/config/bookable-items/",
        component: BookableItemsPage,
      }),
      createRoute({
        getParentRoute: () => bookingRoute,
        path: "/bookable-items/$globalId/{-$tab}/",
        validateSearch: bookableItemSearch,
        component: BookableItemPage,
      }),
    ]),
  ]);
  const router = createRouter({
    routeTree,
    history: createMemoryHistory({
      initialEntries: [`/booking/config/bookable-items${window.location.search}`],
    }),
  });

  return (
    <QueryClientProvider client={queryClient}>
      <NuqsAdapter>
        <Suspense fallback={null}>
          <RouterProvider router={router as never} />
        </Suspense>
      </NuqsAdapter>
    </QueryClientProvider>
  );
}
