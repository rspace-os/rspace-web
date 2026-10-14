import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  Outlet,
  type RouterHistory,
  RouterProvider,
} from "@tanstack/react-router";
import { Suspense } from "react";
import { MemoryHistoryNuqsAdapter as NuqsAdapter } from "@/__tests__/MemoryHistoryNuqsAdapter";
import BookableItemPage from "@/modules/booking/pages/bookable-items/BookableItemPage";
import BookableItemsPage from "@/modules/booking/pages/bookable-items/BookableItemsPage";
import { bookableItemSearch } from "@/modules/booking/pages/bookable-items/search";

export function BookableItemsStory({
  history = createMemoryHistory({ initialEntries: ["/booking/config/bookable-items"] }),
}: {
  history?: RouterHistory;
} = {}) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const rootRoute = createRootRoute({
    component: () => (
      <NuqsAdapter>
        <Outlet />
      </NuqsAdapter>
    ),
  });
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
    history,
  });

  return (
    <QueryClientProvider client={queryClient}>
      <Suspense fallback={null}>
        <RouterProvider router={router as never} />
      </Suspense>
    </QueryClientProvider>
  );
}
