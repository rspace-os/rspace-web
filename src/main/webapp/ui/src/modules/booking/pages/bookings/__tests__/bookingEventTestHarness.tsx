import { QueryClient } from "@tanstack/react-query";
import {
  type AnyRoute,
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  Outlet,
} from "@tanstack/react-router";
import { OAUTH_TOKEN } from "@/__tests__/mocks/oauthTokenMocks";
import { bookingDisplayPreferencesQueryKey } from "@/modules/booking/domain/bookingDisplayPreferences";
import { bookableItemFixtures } from "@/modules/booking/pages/bookable-items/mocks/bookableItemsMocks";
import { inheritedBrowserBookingPreferences } from "@/modules/booking/pages/preferences/bookingPreferencesFixtures";
import { BookingDetailsView } from "../BookingDetailsView";
import { BookingEventPage } from "../BookingEventPage";
import BookingInlineEditForm from "../BookingInlineEditForm";

export const bookingEventFixture = {
  id: 41,
  version: 0,
  target: {
    relationTo: "instruments",
    value: {
      id: 123,
      name: "Confocal microscope",
      deleted: false,
      parentContainer: {
        relationTo: "containers",
        globalId: "IC456",
        value: { id: 456, globalId: "IC456", name: "Imaging lab" },
      },
    },
    globalId: "IN123",
  },
  location: {
    relationTo: "containers",
    value: { id: 456, globalId: "IC456", name: "Imaging lab" },
    globalId: "IC456",
  },
  canViewConfiguration: true,
  timezone: "Europe/Berlin",
  start: "2026-08-17T08:00:00Z",
  end: "2026-08-17T10:00:00Z",
  state: "CONFIRMED",
  kind: "BOOKING",
  privacy: "full",
  cancellationReason: null,
  purpose: "Cell imaging",
  bookedBy: "Ada Lovelace (ada)",
  createdBy: "Grace Hopper (grace)",
  canEdit: true,
  canCancel: true,
  createdAt: "2026-08-01T09:00:00Z",
  updatedAt: "2026-08-02T10:00:00Z",
} as const;

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

export function createBookingEventTestRouter(initialEntry = "/booking/calendar/bookings/41") {
  const root = createRootRoute({ component: Outlet });
  const booking = createRoute({ getParentRoute: () => root, path: "/booking", component: Outlet });
  const myBookings = createRoute({ getParentRoute: () => booking, path: "/my-bookings/", component: Outlet });
  const item = createRoute({
    getParentRoute: () => booking,
    path: "/bookable-items/$globalId/{-$tab}/",
    component: Outlet,
  });
  return createRouter({
    routeTree: root.addChildren([booking.addChildren([myBookings, item, createBookingEventTestRouteTree(booking)])]),
    history: createMemoryHistory({ initialEntries: [initialEntry] }),
  });
}

export function createBookingEventStoryQueryClient() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: Number.POSITIVE_INFINITY } },
  });
  queryClient.setQueryData(["rspace.common.auth", "oauthToken", "v2"], OAUTH_TOKEN);
  queryClient.setQueryData(bookingDisplayPreferencesQueryKey, inheritedBrowserBookingPreferences);
  queryClient.setQueryData(["api-v2", "bookings", bookingEventFixture.id], bookingEventFixture);
  queryClient.setQueryData(
    ["api-v2", "booking-configurations", "picker", "target", bookingEventFixture.target.globalId],
    bookableItemFixtures[0],
  );
  return queryClient;
}
