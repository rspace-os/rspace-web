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
import bookingEnglish from "@/modules/common/i18n/locales/en-US/booking.json";
import commonEnglish from "@/modules/common/i18n/locales/en-US/common.json";
import { createAddBookingRoute, createEditBookingRoute } from "../../bookings/routes";
import { createCalendarRoute } from "../routes";

export const configuration = {
  id: 7,
  target: {
    relationTo: "instruments",
    value: { id: 123, name: "Confocal microscope", deleted: false },
    globalId: "IN123",
  },
  enabled: true,
  timezone: "Europe/Berlin",
};

export const secondConfiguration = {
  ...configuration,
  id: 8,
  target: {
    ...configuration.target,
    value: { id: 124, name: "Electron microscope", deleted: false },
    globalId: "IN124",
  },
  timezone: "America/New_York",
};

export const bookingSummary = {
  id: 41,
  target: configuration.target,
  timezone: "Europe/Berlin",
  start: "2026-08-17T07:00:00Z",
  end: "2026-08-17T08:00:00Z",
  state: "CONFIRMED",
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
    limit: 20,
    page,
    pagingCounter: (page - 1) * 20 + 1,
    totalPages,
    hasPrevPage: page > 1,
    hasNextPage: page < totalPages,
    prevPage: page > 1 ? page - 1 : null,
    nextPage: page < totalPages ? page + 1 : null,
  };
}

export const bookingConfigurationOpenApi = {
  paths: {
    "/api/v2/booking-configurations": {
      get: {
        parameters: [
          {
            name: "sort",
            "x-rspace-sort": {
              fields: ["id", "target", "timezone", "updatedAt"],
              default: ["id"],
              maximumFields: 5,
            },
          },
          {
            name: "where",
            schema: { type: "string", maxLength: 32768 },
            "x-rspace-filter": {
              maximumComparisons: 50,
              maximumLikeComparisons: 10,
              maximumNesting: 10,
              maximumArguments: 1000,
              selectors: {
                enabled: { operators: ["=="], wildcards: false },
                timezone: { operators: ["==", "=contains="], wildcards: true },
                updatedAt: { operators: ["==", "=gt=", "=lt="], wildcards: false },
                "target.name": { operators: ["==", "=contains="], wildcards: true },
              },
            },
          },
          { name: "limit", schema: { type: "integer", default: 20, maximum: 100 } },
          {
            name: "fields",
            "x-rspace-allowed-fields": {
              "booking-configurations": ["id", "target", "enabled", "timezone", "updatedAt"],
            },
          },
        ],
      },
    },
  },
};

export async function renderCalendar(initialEntry = "/booking/calendar?date=2026-08-17") {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const root = createRootRoute({ component: Outlet });
  const booking = createRoute({ getParentRoute: () => root, path: "/booking", component: Outlet });
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
