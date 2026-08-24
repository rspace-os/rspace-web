import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { NuqsAdapter } from "nuqs/adapters/react";
import { Suspense } from "react";
import { OAUTH_TOKEN } from "@/__tests__/mocks/oauthTokenMocks";
import { apiV2CollectionMetadataFromOpenApi } from "@/modules/common/table-list/adapters/apiV2/apiV2CollectionMetadata";
import { MyBookingsRoutePage } from "./MyBookingsPage";
import { bookingsOpenApi } from "./mocks/bookingMocks";

const storySearch = new URLSearchParams({
  period: "upcoming",
  "my-bookings.q": "confocal",
  "my-bookings.where": "target.name=contains=scope",
  "my-bookings.columns": '{ "fields": ["target", "start", "end", "purpose", "timezone"] }',
  "my-bookings.sort": "-start",
});

export function MyBookingsPageStory() {
  if (window.location.pathname !== "/booking/my-bookings") {
    window.history.replaceState({}, "", `/booking/my-bookings?${storySearch}`);
  }
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(["rspace.common.auth", "oauthToken", "v2"], OAUTH_TOKEN);
  queryClient.setQueryData(
    ["api-v2", "openapi", "bookings"],
    apiV2CollectionMetadataFromOpenApi(bookingsOpenApi, "bookings"),
  );
  return (
    <QueryClientProvider client={queryClient}>
      <Suspense fallback={null}>
        <NuqsAdapter>
          <MyBookingsRoutePage requesterId={84} title="My Bookings" />
        </NuqsAdapter>
      </Suspense>
    </QueryClientProvider>
  );
}
