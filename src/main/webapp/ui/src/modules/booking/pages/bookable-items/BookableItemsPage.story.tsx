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
import { createBookableItemRoute, createBookableItemsRoute, createEditBookableItemRoute } from "./routes";

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
      createBookableItemsRoute(bookingRoute),
      createBookableItemRoute(bookingRoute),
      createEditBookableItemRoute(bookingRoute),
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
