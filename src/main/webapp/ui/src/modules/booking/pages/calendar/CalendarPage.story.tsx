import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createMemoryHistory, type RouterHistory, RouterProvider } from "@tanstack/react-router";
import { Suspense, useEffect } from "react";
import { currentUser } from "@/__tests__/helpers/currentUserFixture";
import {
  type BookingDisplayPreferencesDocument,
  mapBookingDisplayPreferences,
} from "@/modules/booking/domain/bookingDisplayPreferences";
import type { ApiV2CurrentUser } from "@/modules/common/api/v2/generated/models/apiV2CurrentUser";
import {
  getGetCurrentUserQueryKey,
  getGetMyBookingPreferencesQueryKey,
} from "@/modules/common/api/v2/generated/users/users";

import { inheritedBrowserBookingPreferences } from "../preferences/bookingPreferencesFixtures";
import { createCalendarRouter } from "./calendarRouter";

export function CalendarPageStory({
  history = createMemoryHistory({ initialEntries: ["/booking/calendar?date=2026-08-17"] }),
  user = currentUser,
  preferences = mapBookingDisplayPreferences(inheritedBrowserBookingPreferences),
}: {
  history?: RouterHistory;
  user?: ApiV2CurrentUser;
  preferences?: BookingDisplayPreferencesDocument;
} = {}) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(getGetMyBookingPreferencesQueryKey(), preferences);
  queryClient.setQueryData(getGetCurrentUserQueryKey(), user);
  const router = createCalendarRouter(history);
  useEffect(
    () => () => {
      router.history.destroy();
      queryClient.clear();
    },
    [router, queryClient],
  );

  return (
    <QueryClientProvider client={queryClient}>
      <Suspense fallback={null}>
        <RouterProvider router={router as never} />
      </Suspense>
    </QueryClientProvider>
  );
}

export function CalendarAvailabilityStory({
  history = createMemoryHistory({ initialEntries: ["/booking/calendar?layout=availability&date=2026-08-17"] }),
  containerWidth = 1500,
}: {
  history?: RouterHistory;
  containerWidth?: number;
} = {}) {
  return (
    <div style={{ width: containerWidth, maxWidth: "100%" }}>
      <CalendarPageStory history={history} />
    </div>
  );
}
