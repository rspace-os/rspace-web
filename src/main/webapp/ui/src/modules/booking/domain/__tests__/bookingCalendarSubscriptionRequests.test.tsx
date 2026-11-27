import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import { CalendarSubscriptionPopover } from "@/modules/booking/pages/bookable-items/CalendarSubscriptionPopover";
import { getGetBookingCalendarSubscriptionQueryOptions } from "@/modules/common/api/v2/generated/booking-configurations/booking-configurations";
import type { BookingCalendarSubscriptionStatus } from "@/modules/common/api/v2/generated/models/bookingCalendarSubscriptionStatus";
import { getListMyBookableItemCalendarSubscriptionsQueryKey } from "@/modules/common/api/v2/generated/users/users";

// Exercise generated invalidation together with its real cache-owning popover.
it.each(["create", "rotate", "revoke"] as const)(
  "%s leaves item-status requests to the popover and invalidates the subscription list",
  async (action) => {
    const user = userEvent.setup();
    const fetchSpy = vi.spyOn(globalThis, "fetch");
    const path = "/api/v2/booking-configurations/7/calendar-subscription";
    let active = action !== "create";
    let version = 1;
    let gets = 0;
    const url = () => `https://rspace.example/public/booking/calendars/feed.ics?token=${String(version).repeat(43)}`;
    const response = () =>
      HttpResponse.json<BookingCalendarSubscriptionStatus>(
        { active, updatedAt: active ? "2026-08-27T12:00:00.000Z" : null, subscriptionUrl: active ? url() : null },
        { headers: { ETag: `"${version}"` } },
      );
    const countRequest = ({ request }: { request: Request }) => {
      if (request.method === "GET" && new URL(request.url).pathname === path) gets += 1;
    };
    server.events.on("request:start", countRequest);
    server.use(
      http.get(path, response),
      http.post(path, () => {
        active = true;
        version += 1;
        return response();
      }),
      http.post(`${path}/rotate`, ({ request }) => {
        expect(request.headers.get("If-Match")).toBe('"1"');
        version += 1;
        return response();
      }),
      http.delete(path, () => {
        active = false;
        version += 1;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const key = getGetBookingCalendarSubscriptionQueryOptions(7).queryKey;
    const listKey = getListMyBookableItemCalendarSubscriptionsQueryKey();
    client.setQueryData(listKey, { docs: [] });
    try {
      render(
        <QueryClientProvider client={client}>
          <CalendarSubscriptionPopover configurationId={7} />
        </QueryClientProvider>,
      );
      await user.click(
        screen.getByRole("button", { name: "booking:bookableItemDetails.calendarSubscription.trigger" }),
      );
      if (action === "create") {
        await user.click(
          await screen.findByRole("button", { name: "booking:bookableItemDetails.calendarSubscription.create" }),
        );
      } else {
        const name = action === "rotate" ? "replace" : "disconnect";
        await user.click(
          await screen.findByRole("button", { name: `booking:bookableItemDetails.calendarSubscription.${name}` }),
        );
        await user.click(
          screen.getByRole("button", { name: `booking:bookableItemDetails.calendarSubscription.${name}Confirm` }),
        );
      }
      if (action === "revoke") {
        await screen.findByRole("button", { name: "booking:bookableItemDetails.calendarSubscription.create" });
      } else {
        await waitFor(() =>
          expect(
            screen.getByRole("link", { name: "booking:bookableItemDetails.calendarSubscription.apple" }),
          ).toHaveAttribute("href", url().replace("https:", "webcal:")),
        );
      }
      await waitFor(() => expect(client.isFetching({ queryKey: key })).toBe(0));
      // MSW request events omit fetches canceled before dispatch, so count attempted fetches too.
      const statusRequests = fetchSpy.mock.calls.filter(([input, options]) => {
        const method = input instanceof Request ? input.method : (options?.method ?? "GET");
        const address = input instanceof Request ? input.url : input.toString();
        return method === "GET" && new URL(address, window.location.origin).pathname === path;
      });
      expect(statusRequests).toHaveLength(action === "revoke" ? 2 : 1);
      expect(gets).toBe(action === "revoke" ? 2 : 1);
      expect(client.getQueryData(key)?.headers.get("ETag")).toBe('"2"');
      expect(client.getQueryState(listKey)?.isInvalidated).toBe(true);
    } finally {
      server.events.removeListener("request:start", countRequest);
      client.clear();
      fetchSpy.mockRestore();
    }
  },
);
