import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { type AnyRouter, createBrowserHistory, RouterProvider } from "@tanstack/react-router";
import { render } from "@testing-library/react";
import { NuqsTestingAdapter, type UrlUpdateEvent } from "nuqs/adapters/testing";
import { Suspense } from "react";
import { onTestFinished } from "vitest";
import { createRealI18nWrapper } from "@/__tests__/helpers/realI18n";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { bookingDisplayPreferencesQueryKey } from "@/modules/booking/domain/bookingDisplayPreferences";
import bookingEnglish from "@/modules/common/i18n/locales/en-US/booking.json";
import commonEnglish from "@/modules/common/i18n/locales/en-US/common.json";
import { bookingTarget, ownBooking } from "../../mocks/bookingCalendarFixtures";
import { inheritedBrowserBookingPreferences } from "../../preferences/bookingPreferencesFixtures";
import { createCalendarRouter } from "../calendarRouter";

export const noParentBooking: BookingListDocument = {
  ...ownBooking,
  id: 44,
  target: bookingTarget(125, "Mass spectrometer", null),
  start: "2026-08-20T08:00:00Z",
  end: "2026-08-20T09:00:00Z",
};

export async function renderBookingTestPage<TRouter extends AnyRouter>(
  router: TRouter,
  options: {
    queryClient?: QueryClient;
    prepareQueryClient?: (client: QueryClient) => void;
    realI18n?: boolean;
    testingNuqs?: { searchParams: string; hasMemory?: boolean; onUrlUpdate?: (event: UrlUpdateEvent) => void };
  } = {},
) {
  const queryClient = options.queryClient ?? new QueryClient({ defaultOptions: { queries: { retry: false } } });
  if (!options.queryClient)
    queryClient.setQueryData(bookingDisplayPreferencesQueryKey, inheritedBrowserBookingPreferences);
  options.prepareQueryClient?.(queryClient);
  const wrapper =
    options.realI18n === false
      ? undefined
      : await createRealI18nWrapper({
          resources: { booking: bookingEnglish, common: commonEnglish },
          defaultNS: "common",
        });
  const routerTree = (
    <Suspense fallback={null}>
      <RouterProvider router={router as never} />
    </Suspense>
  );
  const result = render(
    <QueryClientProvider client={queryClient}>
      {options.testingNuqs ? (
        <NuqsTestingAdapter {...options.testingNuqs}>{routerTree}</NuqsTestingAdapter>
      ) : (
        routerTree
      )}
    </QueryClientProvider>,
    { wrapper },
  );
  return { ...result, queryClient, router };
}

export async function renderCalendar(initialEntry = "/booking/calendar?date=2026-08-17") {
  window.history.replaceState(null, "", initialEntry);
  const router = createCalendarRouter(createBrowserHistory());
  onTestFinished(() => router.history.destroy());
  return renderBookingTestPage(router);
}
