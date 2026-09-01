import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
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
import { createAddBookingRoute, createEditBookingRoute } from "../../bookings/routes";
import { inheritedBrowserBookingPreferences } from "../../preferences/bookingPreferencesFixtures";
import { createCalendarRoute } from "../routes";

export { currentUser } from "../calendarFixtures";

const target = (id: number, name: string, location?: { name: string; globalId: string } | null) => ({
  relationTo: "booking-instruments" as const,
  globalId: `IN${id}`,
  value: {
    id,
    name,
    deleted: false,
    parentContainerName: location === null ? null : (location?.name ?? `${name} room`),
    parentContainerGlobalId: location === null ? null : (location?.globalId ?? `IC${id}`),
  },
});

const timestamps = {
  version: 0,
  kind: "BOOKING" as const,
  createdAt: "2026-08-01T09:00:00Z",
  updatedAt: "2026-08-01T09:00:00Z",
  canCancel: false,
};

export const ownBooking: BookingListDocument = {
  id: 41,
  target: target(123, "Confocal microscope"),
  canViewConfiguration: true,
  requesterId: 1,
  timezone: "Europe/Berlin",
  start: "2026-08-17T08:00:00Z",
  end: "2026-08-17T10:00:00Z",
  state: "CONFIRMED",
  purpose: "Cell imaging",
  bookedBy: "Ada Lovelace (ada)",
  privacy: "full",
  canEdit: true,
  ...timestamps,
};

export const otherBooking: BookingListDocument = {
  ...ownBooking,
  id: 42,
  target: target(124, "Electron microscope", { name: "Workbench", globalId: "BE124" }),
  requesterId: 2,
  start: "2026-08-19T12:00:00Z",
  end: "2026-08-19T13:30:00Z",
  purpose: "Cryo-grid screening",
  bookedBy: "Grace Hopper (grace)",
  canEdit: false,
};

export const busyBooking: BookingListDocument = {
  ...otherBooking,
  id: 43,
  requesterId: 3,
  start: "2026-08-21T09:00:00Z",
  end: "2026-08-21T11:00:00Z",
  purpose: null,
  bookedBy: null,
  privacy: "busy",
  canEdit: false,
};

export const noParentBooking: BookingListDocument = {
  ...ownBooking,
  id: 44,
  target: target(125, "Mass spectrometer", null),
  start: "2026-08-20T08:00:00Z",
  end: "2026-08-20T09:00:00Z",
};

export const deletedParentBooking: BookingListDocument = {
  ...ownBooking,
  id: 45,
  target: target(126, "Microplate reader", null),
  start: "2026-08-20T10:00:00Z",
  end: "2026-08-20T11:00:00Z",
};

export function collectionResponse(
  docs: readonly Record<string, unknown>[],
  options: { page?: number; totalDocs?: number; totalPages?: number } = {},
) {
  const page = options.page ?? 1;
  const totalDocs = options.totalDocs ?? docs.length;
  const totalPages = options.totalPages ?? (totalDocs ? 1 : 0);
  return {
    docs,
    totalDocs,
    limit: 100,
    page,
    pagingCounter: (page - 1) * 100 + 1,
    totalPages,
    hasPrevPage: page > 1,
    hasNextPage: page < totalPages,
    prevPage: page > 1 ? page - 1 : null,
    nextPage: page < totalPages ? page + 1 : null,
  };
}

export async function renderCalendar(initialEntry = "/booking/calendar?date=2026-08-17") {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(bookingDisplayPreferencesQueryKey, inheritedBrowserBookingPreferences);
  const root = createRootRoute({ component: Outlet });
  const booking = createRoute({ getParentRoute: () => root, path: "/booking", component: BookingPage });
  const router = createRouter({
    routeTree: root.addChildren([
      booking.addChildren([
        createCalendarRoute(booking),
        createAddBookingRoute(booking),
        createEditBookingRoute(booking),
      ]),
    ]),
    history: createMemoryHistory({ initialEntries: [initialEntry] }),
  });
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
