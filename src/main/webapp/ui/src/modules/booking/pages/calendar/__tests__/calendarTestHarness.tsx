import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  type AnyRouter,
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  Outlet,
  RouterProvider,
} from "@tanstack/react-router";
import { render } from "@testing-library/react";
import { NuqsAdapter } from "nuqs/adapters/react";
import { Suspense } from "react";
import { createRealI18nWrapper } from "@/__tests__/helpers/realI18n";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { bookingDisplayPreferencesQueryKey } from "@/modules/booking/domain/bookingDisplayPreferences";
import bookingEnglish from "@/modules/common/i18n/locales/en-US/booking.json";
import commonEnglish from "@/modules/common/i18n/locales/en-US/common.json";
import BookingPage from "../../BookingPage";
import { createAddBookingRoute, createBookingEventRouteTree } from "../../bookings/routes";
import { bookingTarget, ownBooking } from "../../mocks/bookingCalendarFixtures";
import { inheritedBrowserBookingPreferences } from "../../preferences/bookingPreferencesFixtures";
import { createCalendarRoute } from "../routes";

export { currentUser } from "../calendarFixtures";

export const noParentBooking: BookingListDocument = {
  ...ownBooking,
  id: 44,
  target: bookingTarget(125, "Mass spectrometer", null),
  start: "2026-08-20T08:00:00Z",
  end: "2026-08-20T09:00:00Z",
};

export async function renderBookingTestPage<TRouter extends AnyRouter>(
  router: TRouter,
  prepareQueryClient?: (client: QueryClient) => void,
) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(bookingDisplayPreferencesQueryKey, inheritedBrowserBookingPreferences);
  prepareQueryClient?.(queryClient);
  const wrapper = await createRealI18nWrapper({
    resources: { booking: bookingEnglish, common: commonEnglish },
    defaultNS: "common",
  });
  const result = render(
    <QueryClientProvider client={queryClient}>
      <NuqsAdapter>
        <Suspense fallback={null}>
          <RouterProvider router={router as never} />
        </Suspense>
      </NuqsAdapter>
    </QueryClientProvider>,
    { wrapper },
  );
  return { ...result, queryClient, router };
}

export async function renderCalendar(initialEntry = "/booking/calendar?date=2026-08-17") {
  window.history.replaceState(null, "", initialEntry);
  const root = createRootRoute({ component: Outlet });
  const booking = createRoute({ getParentRoute: () => root, path: "/booking", component: BookingPage });
  const router = createRouter({
    routeTree: root.addChildren([
      booking.addChildren([
        createCalendarRoute(booking),
        createAddBookingRoute(booking),
        createBookingEventRouteTree(booking),
      ]),
    ]),
    history: createMemoryHistory({ initialEntries: [initialEntry] }),
  });
  return renderBookingTestPage(router);
}
