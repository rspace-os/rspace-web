import type { Meta, StoryObj } from "@storybook/tanstack-react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  Outlet,
  RouterProvider,
} from "@tanstack/react-router";
import { bookingQueryOptions } from "@/modules/booking/domain/booking";
import { getListBookingConfigurationsQueryKey } from "@/modules/common/api/v2/generated/booking-configurations/booking-configurations";
import type { ListBookingConfigurations200 } from "@/modules/common/api/v2/generated/models/listBookingConfigurations200";
import { getGetMyBookingPreferencesQueryKey } from "@/modules/common/api/v2/generated/users/users";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { bookableItemFixtures } from "../bookable-items/mocks/bookableItemsMocks";
import { inheritedBrowserBookingPreferences } from "../preferences/bookingPreferencesFixtures";
import { createBookingEventTestRouteTree } from "./__tests__/bookingEventTestHarness";

const booking = {
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
  canViewConfiguration: true,
  timezone: "Europe/Berlin",
  start: "2026-10-19T08:00:00Z",
  end: "2026-10-19T10:00:00Z",
  state: "CONFIRMED",
  kind: "BOOKING",
  privacy: "full",
  purpose: "Cell imaging",
  bookedBy: "Ada Lovelace (ada)",
  createdBy: "Grace Hopper (grace)",
  canEdit: true,
  canCancel: true,
  createdAt: "2026-08-01T09:00:00Z",
  updatedAt: "2026-08-02T10:00:00Z",
} as const;

function BookingEventPageStory({ edit = false }: { edit?: boolean }) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: Number.POSITIVE_INFINITY } },
  });
  queryClient.setQueryData(getGetMyBookingPreferencesQueryKey(), inheritedBrowserBookingPreferences);
  queryClient.setQueryData(bookingQueryOptions(booking.id).queryKey, booking);
  queryClient.setQueryData(
    getListBookingConfigurationsQueryKey({ where: `target==${booking.target.globalId}`, depth: 2, limit: 2 }),
    { docs: [bookableItemFixtures[0]], limit: 2, page: 1 } satisfies ListBookingConfigurations200,
  );
  const root = createRootRoute({ component: Outlet });
  const bookingRoot = createRoute({ getParentRoute: () => root, path: "/booking", component: Outlet });
  const myBookings = createRoute({ getParentRoute: () => bookingRoot, path: "/my-bookings", component: Outlet });
  const item = createRoute({
    getParentRoute: () => bookingRoot,
    path: "/bookable-items/$globalId/{-$tab}",
    component: Outlet,
  });
  const router = createRouter({
    routeTree: root.addChildren([
      bookingRoot.addChildren([myBookings, item, createBookingEventTestRouteTree(bookingRoot)]),
    ]),
    history: createMemoryHistory({
      initialEntries: [`/booking/calendar/bookings/${booking.id}${edit ? "/edit" : ""}`],
    }),
  });

  return (
    <QueryClientProvider client={queryClient}>
      <I18nRoot namespaces={["booking", "common"]}>
        <RouterProvider router={router as never} />
      </I18nRoot>
    </QueryClientProvider>
  );
}

const meta = {
  title: "Booking/Pages/Booking Event",
  component: BookingEventPageStory,
  parameters: { layout: "fullscreen" },
} satisfies Meta<typeof BookingEventPageStory>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Confirmed: Story = {};

export const DirectEdit: Story = { args: { edit: true } };
