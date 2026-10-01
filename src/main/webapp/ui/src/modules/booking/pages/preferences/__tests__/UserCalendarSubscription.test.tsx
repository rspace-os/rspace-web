import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { userCalendarSubscriptionQueryKey } from "../../bookable-items/bookableItemCalendarSubscription";
import { UserCalendarSubscription } from "../UserCalendarSubscription";

const path = "/api/v2/users/me/booking-calendar-subscription";
const updatedAt = "2026-08-27T12:00:00.000Z";

function urlFor(character: string): string {
  return `https://rspace.example/public/booking/calendars/feed.ics?token=${character.repeat(43)}`;
}

function renderSubscription() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return {
    ...render(
      <QueryClientProvider client={queryClient}>
        <UserCalendarSubscription token="oauth" />
      </QueryClientProvider>,
    ),
    queryClient,
  };
}

describe("UserCalendarSubscription", () => {
  it("keeps a rotated link when an older status request returns afterward", async () => {
    const user = userEvent.setup();
    const staleResponse = Promise.withResolvers<Response>();
    const staleReadStarted = Promise.withResolvers<void>();
    const staleReadReturned = Promise.withResolvers<void>();
    let gets = 0;
    server.use(
      http.get(path, async () => {
        gets += 1;
        if (gets === 2) {
          staleReadStarted.resolve();
          const response = await staleResponse.promise;
          staleReadReturned.resolve();
          return response;
        }
        return HttpResponse.json(
          { active: true, updatedAt, subscriptionUrl: urlFor("b") },
          { headers: { ETag: '"current"' } },
        );
      }),
      http.post(`${path}/rotate`, ({ request }) => {
        expect(request.headers.get("If-Match")).toBe('"current"');
        return HttpResponse.json(
          { active: true, updatedAt, subscriptionUrl: urlFor("r") },
          { headers: { ETag: '"rotated"' } },
        );
      }),
    );
    const { queryClient } = renderSubscription();
    const linkField = await screen.findByRole("textbox", {
      name: "booking:preferences.calendarSubscription.copyPrompt",
    });
    expect(linkField).toHaveValue(urlFor("b"));

    const statusRefresh = queryClient.refetchQueries({ queryKey: userCalendarSubscriptionQueryKey, exact: true });
    await staleReadStarted.promise;
    await user.click(screen.getByRole("button", { name: "booking:preferences.calendarSubscription.replace" }));
    await user.click(
      screen.getByRole("button", { name: "booking:preferences.calendarSubscription.replaceDialog.confirm" }),
    );
    await waitFor(() => expect(linkField).toHaveValue(urlFor("r")));

    staleResponse.resolve(
      HttpResponse.json({ active: true, updatedAt, subscriptionUrl: urlFor("b") }, { headers: { ETag: '"current"' } }),
    );
    await Promise.all([statusRefresh, staleReadReturned.promise]);
    expect(queryClient.getQueryData(userCalendarSubscriptionQueryKey)).toMatchObject({
      subscriptionUrl: urlFor("r"),
      etag: '"rotated"',
    });
    expect(screen.getByRole("textbox", { name: "booking:preferences.calendarSubscription.copyPrompt" })).toHaveValue(
      urlFor("r"),
    );
  });
});
