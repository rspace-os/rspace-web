import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { type AnyRouter, createMemoryHistory, RouterProvider } from "@tanstack/react-router";
import { render } from "@testing-library/react";
import { Suspense } from "react";
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
      <Suspense fallback={null}>
        <RouterProvider router={router as never} />
      </Suspense>
    </QueryClientProvider>,
    { wrapper },
  );
  return { ...result, queryClient, router };
}

export async function renderCalendar(initialEntry = "/booking/calendar?date=2026-08-17") {
  const router = createCalendarRouter(createMemoryHistory({ initialEntries: [initialEntry] }));
  return renderBookingTestPage(router);
}
