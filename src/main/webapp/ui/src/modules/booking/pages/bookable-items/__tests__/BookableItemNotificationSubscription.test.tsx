import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { Suspense } from "react";
import { describe, expect, it } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { oauthTokenHandler } from "@/__tests__/mocks/oauthTokenMocks";
import { server } from "@/__tests__/mswServer";
import { bookingNotificationSubscriptionsQueryKey } from "@/modules/booking/domain/bookingNotificationSubscriptions";
import { currentUser } from "@/modules/booking/pages/calendar/calendarFixtures";
import { BookableItemNotificationSubscription } from "../BookableItemNotificationSubscription";

function renderSubscription() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return {
    ...render(
      <QueryClientProvider client={queryClient}>
        <Suspense fallback={null}>
          <BookableItemNotificationSubscription configurationId={7} globalId="IN7" canManageNotificationSubscription />
        </Suspense>
      </QueryClientProvider>,
    ),
    queryClient,
  };
}

describe("BookableItemNotificationSubscription", () => {
  it("announces a saved setting through the live region mounted before the mutation", async () => {
    const user = userEvent.setup();
    let subscription = {
      configurationId: 7,
      enabled: false,
      version: 1,
      createdEnabled: true,
      cancelledEnabled: true,
      emailEnabled: false,
    };
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/booking-configurations/7/notification-subscription", () => HttpResponse.json(subscription)),
      http.put("/api/v2/booking-configurations/7/notification-subscription", async ({ request }) => {
        const body = (await request.json()) as { enabled: boolean; version: number };
        subscription = { ...subscription, enabled: body.enabled, version: body.version + 1 };
        return HttpResponse.json(subscription);
      }),
    );
    const { container } = renderSubscription();

    const on = await screen.findByRole("radio", { name: "booking:notificationSubscriptions.options.on" });
    const status = screen.getByRole("status");
    expect(status).toBeEmptyDOMElement();
    expect(status).toHaveClass("sr-only");
    await user.click(on);
    await user.click(screen.getByRole("button", { name: "booking:preferences.actions.save" }));

    const savedButton = await screen.findByRole("button", { name: "booking:preferences.actions.saved" });
    expect(savedButton).toHaveAttribute("aria-disabled", "true");
    expect(savedButton).toHaveFocus();
    expect(status).toHaveTextContent("booking:preferences.actions.saved");
    expect(screen.getByRole("status")).toBe(status);
    await expectAccessible(container);
  });

  it("syncs clean refreshes while preserving an edited choice and focus through save", async () => {
    const user = userEvent.setup();
    let subscription = {
      configurationId: 7,
      enabled: false,
      version: 1,
      createdEnabled: true,
      cancelledEnabled: true,
      emailEnabled: false,
    };
    let saved: unknown;
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/booking-configurations/7/notification-subscription", () => HttpResponse.json(subscription)),
      http.put("/api/v2/booking-configurations/7/notification-subscription", async ({ request }) => {
        saved = await request.json();
        const body = saved as { enabled: boolean; version: number };
        subscription = { ...subscription, enabled: body.enabled, version: body.version + 1 };
        return HttpResponse.json(subscription);
      }),
    );
    const { queryClient } = renderSubscription();
    const queryKey = bookingNotificationSubscriptionsQueryKey.item(currentUser.id, 7);
    const on = await screen.findByRole("radio", { name: "booking:notificationSubscriptions.options.on" });
    const off = screen.getByRole("radio", { name: "booking:notificationSubscriptions.options.off" });

    act(() => {
      queryClient.setQueryData(queryKey, { ...subscription, enabled: true, version: 2 });
    });
    await waitFor(() => expect(on).toBeChecked());

    await user.click(off);
    act(() => {
      queryClient.setQueryData(queryKey, { ...subscription, enabled: true, version: 3 });
    });
    await waitFor(() => expect(off).toBeChecked());
    await user.click(screen.getByRole("button", { name: "booking:preferences.actions.save" }));

    const savedButton = await screen.findByRole("button", { name: "booking:preferences.actions.saved" });
    expect(savedButton).toHaveFocus();
    expect(saved).toEqual({ enabled: false, version: 3 });
    expect(on).not.toBeChecked();
  });
});
