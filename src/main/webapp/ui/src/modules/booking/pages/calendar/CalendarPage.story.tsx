import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createBrowserHistory, RouterProvider } from "@tanstack/react-router";
import { NuqsAdapter } from "nuqs/adapters/react";
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
  user = currentUser,
  preferences = inheritedBrowserBookingPreferences,
}: {
  user?: CurrentUser;
  preferences?: BookingDisplayPreferencesDocument;
} = {}) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(authQueryKeys.oauthToken(true), OAUTH_TOKEN);
  queryClient.setQueryData(bookingDisplayPreferencesQueryKey, preferences);
  queryClient.setQueryData(currentUserQueryKeys.me(), user);
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
