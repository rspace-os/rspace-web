import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createMemoryHistory, type RouterHistory, RouterProvider } from "@tanstack/react-router";
import { Suspense, useEffect } from "react";
import { OAUTH_TOKEN } from "@/__tests__/mocks/oauthTokenMocks";
import {
  type BookingDisplayPreferencesDocument,
  bookingDisplayPreferencesQueryKey,
} from "@/modules/booking/domain/bookingDisplayPreferences";
import { queryKeys as authQueryKeys } from "@/modules/common/hooks/auth";
import type { CurrentUser } from "@/modules/common/queries/currentUser";
import { currentUserQueryKeys } from "@/modules/common/queries/currentUser";
import { currentUser } from "../mocks/currentUserFixture";
import { inheritedBrowserBookingPreferences } from "../preferences/bookingPreferencesFixtures";
import { createCalendarRouter } from "./calendarRouter";

export function CalendarPageStory({
  history = createMemoryHistory({ initialEntries: ["/booking/calendar?date=2026-08-17"] }),
  user = currentUser,
  preferences = inheritedBrowserBookingPreferences,
}: {
  history?: RouterHistory;
  user?: CurrentUser;
  preferences?: BookingDisplayPreferencesDocument;
} = {}) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(authQueryKeys.oauthToken(true), OAUTH_TOKEN);
  queryClient.setQueryData(bookingDisplayPreferencesQueryKey, preferences);
  queryClient.setQueryData(currentUserQueryKeys.me(), user);
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
