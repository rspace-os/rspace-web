import { matchQuery, QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createMemoryHistory, createRootRoute, createRouter, RouterProvider } from "@tanstack/react-router";
import { delay, HttpResponse, http } from "msw";
import { Suspense, useState } from "react";
import type { EditableBooking } from "@/modules/booking/creation/BookingForm";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { getListBookingsQueryKey, useListBookings } from "@/modules/common/api/v2/generated/bookings/bookings";
import type { BookingsRead } from "@/modules/common/api/v2/generated/models/bookingsRead";
import type { BookingsUpdate } from "@/modules/common/api/v2/generated/models/bookingsUpdate";
import type { ListBookingConfigurations200 } from "@/modules/common/api/v2/generated/models/listBookingConfigurations200";
import type { ListBookings200 } from "@/modules/common/api/v2/generated/models/listBookings200";
import { getGetMyBookingPreferencesQueryKey } from "@/modules/common/api/v2/generated/users/users";
import { bookableItemFixtures, collectionPage } from "../../bookable-items/mocks/bookableItemsMocks";
import { bookingTarget, collectionResponse, ownBooking } from "../../mocks/bookingCalendarFixtures";
import { inheritedBrowserBookingPreferences } from "../../preferences/bookingPreferencesFixtures";
import { InlineBookingEditor } from "../InlineBookingEditor";

export function inlineBookingSaveFixture(EditorComponent = InlineBookingEditor) {
  const event: BookingListDocument & EditableBooking = {
    ...ownBooking,
    target: bookingTarget(123, "Confocal microscope"),
    kind: "BOOKING",
    start: "2030-10-07T10:00:00Z",
    end: "2030-10-07T11:00:00Z",
    privacy: "full",
    state: "CONFIRMED",
    canEdit: true,
  };
  const abortedAfterSave: string[] = [];
  const requests = {
    saved: false,
    listRefreshes: 0,
    overlapChecks: 0,
    overlapRequestsAfterSave: 0,
    overlapQueryStartsAfterSave: 0,
    abortedAfterSave,
  };
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(getGetMyBookingPreferencesQueryKey(), inheritedBrowserBookingPreferences);
  const listParams = { where: "id==41" };
  queryClient.getQueryCache().subscribe((change) => {
    if (
      requests.saved &&
      change.type === "updated" &&
      change.action.type === "fetch" &&
      matchQuery({ queryKey: getListBookingsQueryKey() }, change.query) &&
      !matchQuery({ queryKey: getListBookingsQueryKey(listParams) }, change.query)
    ) {
      requests.overlapQueryStartsAfterSave += 1;
    }
  });
  const handlers = [
    http.get<never, never, ListBookingConfigurations200>("/api/v2/booking-configurations", () =>
      HttpResponse.json({ ...collectionPage([bookableItemFixtures[0]], 2), docs: [bookableItemFixtures[0]] }),
    ),
    http.get<never, never, ListBookings200>("/api/v2/bookings", async ({ request }) => {
      const isList = new URL(request.url).searchParams.get("where") === listParams.where;
      if (isList) {
        if (requests.saved) requests.listRefreshes += 1;
      } else {
        requests.overlapChecks += 1;
        if (requests.saved) requests.overlapRequestsAfterSave += 1;
      }
      request.signal.addEventListener("abort", () => {
        if (requests.saved) requests.abortedAfterSave.push(request.url);
      });
      await delay(50);
      return HttpResponse.json({
        ...collectionResponse([ownBooking]),
        docs: [{ ...ownBooking, version: requests.saved ? 8 : ownBooking.version }],
      });
    }),
    http.patch<never, BookingsUpdate, BookingsRead>("/api/v2/bookings/41", () => {
      requests.saved = true;
      return HttpResponse.json({
        ...ownBooking,
        version: 8,
        start: event.start,
        end: "2030-10-07T11:15:00Z",
      });
    }),
  ];
  const closedText = "Editor closed";
  function Editor() {
    const [open, setOpen] = useState(true);
    const list = useListBookings(listParams, { query: { staleTime: Infinity } });
    return (
      <>
        <output aria-label="Booking version">{list.data?.docs[0]?.version}</output>
        {open ? <EditorComponent event={event} timezone="UTC" onClose={() => setOpen(false)} /> : <p>{closedText}</p>}
      </>
    );
  }
  const root = createRootRoute({ component: Editor });
  const router = createRouter({ routeTree: root, history: createMemoryHistory({ initialEntries: ["/"] }) });
  function View() {
    return (
      <QueryClientProvider client={queryClient}>
        <Suspense fallback={null}>
          <RouterProvider router={router} />
        </Suspense>
      </QueryClientProvider>
    );
  }
  return { queryClient, router, requests, handlers, View };
}
