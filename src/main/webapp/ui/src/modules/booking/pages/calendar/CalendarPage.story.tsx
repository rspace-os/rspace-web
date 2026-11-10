import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createBrowserHistory, RouterProvider } from "@tanstack/react-router";
import { NuqsAdapter } from "nuqs/adapters/react";
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
  user = currentUser,
  preferences = mapBookingDisplayPreferences(inheritedBrowserBookingPreferences),
}: {
  user?: ApiV2CurrentUser;
  preferences?: BookingDisplayPreferencesDocument;
} = {}) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(getGetMyBookingPreferencesQueryKey(), preferences);
  queryClient.setQueryData(getGetCurrentUserQueryKey(), user);
  const router = createCalendarRouter(createBrowserHistory());
  useEffect(
    () => () => {
      router.history.destroy();
      queryClient.clear();
    },
    [router, queryClient],
  );

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
