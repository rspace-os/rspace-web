import "@/__tests__/__mocks__/matchMedia";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { renderWithRealI18n } from "@/__tests__/helpers/realI18n";
import { server } from "@/__tests__/mswServer";
import bookingEn from "@/modules/common/i18n/locales/en-US/booking.json";
import { BookableItemNotificationSubscription } from "../BookableItemNotificationSubscription";
import { bookerBookingAccess, collectionPage } from "../mocks/bookableItemsMocks";
import { configuration, renderPage, setupPage } from "./bookableItemPageTestUtils";

vi.mock("@/modules/common/hooks/auth", () => ({ useOauthTokenQuery: vi.fn() }));
vi.mock("@/modules/common/queries/currentUser", () => ({ useCurrentUserQuery: vi.fn() }));

let currentNotificationSubscription = {
  configurationId: 7,
  enabled: false,
  version: 0,
  createdEnabled: true,
  cancelledEnabled: true,
  emailEnabled: false,
};
let notificationSubscriptionRequest: unknown;

beforeEach(() => {
  setupPage();
  currentNotificationSubscription = {
    configurationId: 7,
    enabled: false,
    version: 0,
    createdEnabled: true,
    cancelledEnabled: true,
    emailEnabled: false,
  };
  notificationSubscriptionRequest = undefined;
  server.use(
    http.get("/api/v2/booking-configurations/7/notification-subscription", () =>
      HttpResponse.json(currentNotificationSubscription),
    ),
    http.put("/api/v2/booking-configurations/7/notification-subscription", async ({ request }) => {
      notificationSubscriptionRequest = await request.json();
      currentNotificationSubscription = { ...currentNotificationSubscription, enabled: true, version: 1 };
      return HttpResponse.json(currentNotificationSubscription);
    }),
  );
});

describe("BookableItemPage", () => {
  it("keeps the draft's original version through background and conflict refreshes", async () => {
    const user = userEvent.setup();
    let current = configuration;
    const versions: (string | null)[] = [];
    server.use(
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionPage([current], 2))),
      http.patch("/api/v2/booking-configurations/7", ({ request }) => {
        versions.push(request.headers.get("If-Match"));
        return HttpResponse.json({ status: 412 }, { status: 412 });
      }),
    );
    const { queryClient } = renderPage("/booking/bookable-items/IN123/details?edit=true");
    const maximum = await screen.findByRole("spinbutton", {
      name: "booking:bookableItemDetails.fields.maximumDuration",
    });
    expect(screen.queryByRole("combobox", { name: "booking:bookableItems.fields.timezone" })).not.toBeInTheDocument();
    await user.clear(maximum);
    await user.type(maximum, "60");
    current = {
      ...configuration,
      configurationVersion: 1,
      openingHours: configuration.openingHours.map((entry) => ({ ...entry, end: "20:00" })),
    };
    await act(() => queryClient.refetchQueries({ queryKey: ["api-v2", "booking-configurations", "target", "IN123"] }));
    const save = screen.getByRole("button", { name: "booking:bookableItems.actions.save" });
    await user.click(save);
    expect(await screen.findByRole("alert")).toHaveTextContent("booking:bookableItems.staleEdit");
    await waitFor(() => expect(save).toBeEnabled());
    await user.click(save);
    await waitFor(() => expect(versions).toHaveLength(2));

    expect(versions).toEqual(['"0"', '"0"']);
    expect(maximum).toHaveValue(60);
  });

  it.each([
    { status: 408, failure: "a request timeout" },
    { status: 429, failure: "rate limiting" },
    { status: 503, failure: "a server failure" },
    { status: null, failure: "a network failure" },
  ])("keeps a dirty draft through $failure and a successful retry during a background refresh", async ({ status }) => {
    const user = userEvent.setup();
    let saved: unknown;
    server.use(
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionPage([configuration], 2))),
      http.patch("/api/v2/booking-configurations/7", async ({ request }) => {
        saved = await request.json();
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const { queryClient } = renderPage("/booking/bookable-items/IN123/details?edit=true");
    const maximum = await screen.findByRole("spinbutton", {
      name: "booking:bookableItemDetails.fields.maximumDuration",
    });
    await user.clear(maximum);
    await user.type(maximum, "60");
    server.use(
      http.get("/api/v2/booking-configurations", () =>
        status === null ? HttpResponse.error() : new HttpResponse(null, { status }),
      ),
    );

    await act(() => queryClient.refetchQueries({ queryKey: ["api-v2", "booking-configurations", "target", "IN123"] }));

    expect(screen.getByRole("spinbutton", { name: "booking:bookableItemDetails.fields.maximumDuration" })).toHaveValue(
      60,
    );
    const notice = await screen.findByRole("alert");
    expect(notice).toMatchTextContent("booking:bookableItemDetails.backgroundError");
    await expectAccessible(notice);
    server.use(http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionPage([configuration], 2))));
    await user.click(within(notice).getByRole("button", { name: "common:actions.retry" }));

    await waitFor(() => expect(screen.queryByRole("alert")).not.toBeInTheDocument());
    expect(maximum).toBeInTheDocument();
    expect(maximum).toHaveValue(60);
    await user.click(screen.getByRole("button", { name: "booking:bookableItems.actions.save" }));
    await waitFor(() => expect(saved).toMatchObject({ maxBookingDurationMinutes: 60 }));
  });

  it.each([403, 404])("conceals the previous resource while navigating to a delayed %s response", async (status) => {
    const response = Promise.withResolvers<void>();
    server.use(
      http.get("/api/v2/booking-configurations", async ({ request }) => {
        if (new URL(request.url).searchParams.get("where")?.includes("IN124")) {
          await response.promise;
          return new HttpResponse(null, { status });
        }
        return HttpResponse.json(collectionPage([configuration], 2));
      }),
      http.get("/api/v2/bookings", () => HttpResponse.json(collectionPage([], 10))),
    );
    const { router } = renderPage();
    try {
      expect(await screen.findByRole("heading", { name: "Confocal microscope" })).toBeVisible();
      await act(async () =>
        router.navigate({
          to: "/booking/bookable-items/$globalId/{-$tab}",
          params: { globalId: "IN124", tab: "details" },
        }),
      );
      expect(screen.getByRole("main")).toHaveAttribute("aria-busy", "true");
      expect(screen.queryByText("Confocal microscope")).not.toBeInTheDocument();
      expect(screen.queryByText("Grace Hopper (grace)")).not.toBeInTheDocument();
      expect(screen.queryByRole("tab")).not.toBeInTheDocument();
      expect(screen.queryByRole("button")).not.toBeInTheDocument();
      await act(async () => response.resolve());
      expect(await screen.findByText("booking:bookableItemDetails.error.title")).toBeVisible();
      expect(screen.getByRole("main")).not.toHaveAttribute("aria-busy");
      expect(screen.queryByText("Confocal microscope")).not.toBeInTheDocument();
    } finally {
      response.resolve();
    }
  });

  it.each([
    { status: 401, reason: "the session expires" },
    { status: 403, reason: "access is denied" },
    { status: 404, reason: "the resource is missing" },
    { status: null, reason: "the collection no longer contains the resource" },
  ])("keeps authorized content during refetch but removes it when $reason", async ({ status }) => {
    server.use(
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionPage([configuration], 2))),
      http.get("/api/v2/bookings", () => HttpResponse.json(collectionPage([], 10))),
    );
    const { queryClient } = renderPage();
    expect(await screen.findByRole("heading", { name: "Confocal microscope" })).toBeVisible();
    const response = Promise.withResolvers<void>();
    const requested = Promise.withResolvers<void>();
    server.use(
      http.get("/api/v2/booking-configurations", async () => {
        requested.resolve();
        await response.promise;
        return status === null ? HttpResponse.json(collectionPage([], 2)) : new HttpResponse(null, { status });
      }),
    );
    try {
      await act(async () => {
        void queryClient.refetchQueries({ queryKey: ["api-v2", "booking-configurations", "target", "IN123"] });
        await requested.promise;
      });
      expect(screen.getByRole("heading", { name: "Confocal microscope" })).toBeVisible();
      expect(screen.getByRole("main")).not.toHaveAttribute("aria-busy", "true");
      await act(async () => response.resolve());
      expect(await screen.findByText("booking:bookableItemDetails.error.title")).toBeVisible();
      expect(screen.queryByText("Confocal microscope")).not.toBeInTheDocument();
      expect(screen.queryByRole("tab")).not.toBeInTheDocument();
    } finally {
      response.resolve();
    }
  });

  it("confirms navigation to another resource even when the dirty editor survives tab changes", async () => {
    const user = userEvent.setup();
    server.use(
      http.get("/api/v2/booking-configurations/7/audit", () => new HttpResponse(null, { status: 503 })),
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionPage([configuration], 2))),
      http.get("/api/v2/bookings", () => HttpResponse.json(collectionPage([], 10))),
    );
    const { router } = renderPage("/booking/bookable-items/IN123/details?edit=true");
    const duration = await screen.findByRole("spinbutton", {
      name: "booking:bookableItemDetails.fields.maximumDuration",
    });
    await user.clear(duration);
    await user.type(duration, "60");
    await user.click(screen.getByRole("tab", { name: "booking:bookableItemDetails.tabs.bookings" }));
    await waitFor(() => expect(router.state.location.pathname).toBe("/booking/bookable-items/IN123"));
    await act(async () => {
      void router.navigate({
        to: "/booking/bookable-items/$globalId/{-$tab}",
        params: { globalId: "IN124", tab: "details" },
        search: { edit: true },
      });
    });
    const dialog = await screen.findByRole("alertdialog");
    await user.click(within(dialog).getByRole("button", { name: "common:actions.cancel" }));
    expect(router.state.location.pathname).toBe("/booking/bookable-items/IN123");
    await user.click(screen.getByRole("tab", { name: "booking:bookableItemDetails.tabs.details" }));
    expect(screen.getByRole("spinbutton", { name: "booking:bookableItemDetails.fields.maximumDuration" })).toHaveValue(
      60,
    );
  });

  it("saves an owner notification choice and clears saved state after edits", async () => {
    const user = userEvent.setup();
    server.use(
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionPage([configuration], 2))),
      http.get("/api/v2/bookings", () => HttpResponse.json(collectionPage([], 10))),
    );
    renderPage();

    expect(
      await screen.findByRole("radiogroup", { name: "booking:notificationSubscriptions.item.label" }),
    ).toBeVisible();
    expect(screen.queryByText("booking:notificationSubscriptions.item.description")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: "booking:notificationSubscriptions.item.profileLink" }),
    ).not.toBeInTheDocument();
    await user.click(await screen.findByRole("radio", { name: "booking:notificationSubscriptions.options.on" }));
    await user.click(screen.getByRole("button", { name: "booking:preferences.actions.save" }));

    await waitFor(() => expect(notificationSubscriptionRequest).toEqual({ enabled: true, version: 0 }));
    expect(await screen.findByRole("radio", { name: "booking:notificationSubscriptions.options.on" })).toBeChecked();
    const savedButton = await screen.findByRole("button", { name: "booking:preferences.actions.saved" });
    expect(savedButton).toBeDisabled();
    expect(screen.queryByText("booking:notificationSubscriptions.item.rspaceOnly")).not.toBeInTheDocument();
    expect(screen.queryByText("booking:notificationSubscriptions.item.emailDisabled")).not.toBeInTheDocument();
    expect(screen.queryByText("booking:notificationSubscriptions.item.newBookings")).not.toBeInTheDocument();

    await user.click(screen.getByRole("radio", { name: "booking:notificationSubscriptions.options.off" }));
    expect(screen.queryByRole("button", { name: "booking:preferences.actions.saved" })).not.toBeInTheDocument();
    const saveButton = screen.getByRole("button", { name: "booking:preferences.actions.save" });
    expect(saveButton).toBeEnabled();
    await user.click(screen.getByRole("button", { name: "booking:bookableItemDetails.cancelEdit" }));

    expect(screen.getByRole("radio", { name: "booking:notificationSubscriptions.options.on" })).toBeChecked();
    expect(screen.getByRole("button", { name: "booking:preferences.actions.save" })).toBeDisabled();
    expect(screen.queryByRole("button", { name: "booking:preferences.actions.saved" })).not.toBeInTheDocument();
    expect(notificationSubscriptionRequest).toEqual({ enabled: true, version: 0 });
  });

  it("cancels an unsaved owner notification choice without writing it", async () => {
    const user = userEvent.setup();
    server.use(
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionPage([configuration], 2))),
      http.get("/api/v2/bookings", () => HttpResponse.json(collectionPage([], 10))),
    );
    renderPage();

    await screen.findByRole("radiogroup", { name: "booking:notificationSubscriptions.item.label" });
    expect(screen.getByRole("button", { name: "booking:preferences.actions.save" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "booking:bookableItemDetails.cancelEdit" })).toBeDisabled();

    await user.click(screen.getByRole("radio", { name: "booking:notificationSubscriptions.options.on" }));
    expect(screen.getByRole("button", { name: "booking:preferences.actions.save" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "booking:bookableItemDetails.cancelEdit" })).toBeEnabled();
    await user.click(screen.getByRole("button", { name: "booking:bookableItemDetails.cancelEdit" }));

    expect(screen.getByRole("radio", { name: "booking:notificationSubscriptions.options.off" })).toBeChecked();
    expect(screen.getByRole("button", { name: "booking:preferences.actions.save" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "booking:bookableItemDetails.cancelEdit" })).toBeDisabled();
    expect(notificationSubscriptionRequest).toBeUndefined();
  });

  it("lets a viewer subscribe and unsubscribe from their own instrument notifications", async () => {
    const user = userEvent.setup();
    let subscription = {
      ...currentNotificationSubscription,
      version: -1,
    };
    const requests: unknown[] = [];
    server.use(
      http.get("/api/v2/booking-configurations", () =>
        HttpResponse.json(
          collectionPage(
            [
              {
                ...configuration,
                ...bookerBookingAccess,
                effectiveRole: "VIEWER",
                capabilities: {
                  ...bookerBookingAccess.capabilities,
                  canCreateBooking: false,
                  canManageOwnBookings: false,
                  canSubscribeCalendar: false,
                  canManageNotificationSubscription: true,
                },
              },
            ],
            2,
          ),
        ),
      ),
      http.get("/api/v2/bookings", () => HttpResponse.json(collectionPage([], 10))),
      http.get("/api/v2/booking-configurations/7/notification-subscription", () => HttpResponse.json(subscription)),
      http.put("/api/v2/booking-configurations/7/notification-subscription", async ({ request }) => {
        const body = (await request.json()) as { enabled: boolean; version: number };
        requests.push(body);
        subscription = {
          ...subscription,
          enabled: body.enabled,
          version: body.version < 0 ? 0 : body.version + 1,
        };
        return HttpResponse.json(subscription);
      }),
    );
    renderPage();

    const options = await screen.findByRole("radiogroup", { name: "booking:notificationSubscriptions.item.label" });
    expect(within(options).getByRole("radio", { name: "booking:notificationSubscriptions.options.off" })).toBeChecked();

    await user.click(screen.getByRole("radio", { name: "booking:notificationSubscriptions.options.on" }));
    await user.click(screen.getByRole("button", { name: "booking:preferences.actions.save" }));
    await waitFor(() => expect(requests).toEqual([{ enabled: true, version: -1 }]));

    await user.click(await screen.findByRole("radio", { name: "booking:notificationSubscriptions.options.off" }));
    await user.click(screen.getByRole("button", { name: "booking:preferences.actions.save" }));
    await waitFor(() =>
      expect(requests).toEqual([
        { enabled: true, version: -1 },
        { enabled: false, version: 0 },
      ]),
    );
  });

  it("gives the item notification options an accessible English group name", async () => {
    await renderWithRealI18n(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <BookableItemNotificationSubscription
          configurationId={7}
          globalId="IN123"
          canManageNotificationSubscription={true}
        />
      </QueryClientProvider>,
      { resources: { booking: bookingEn }, defaultNS: "booking" },
    );

    expect(await screen.findByRole("radiogroup", { name: "Receive booking notifications" })).toBeVisible();
    expect(screen.getByText("Notifications")).toBeVisible();
  });

  it("reloads the latest item choice after a subscription conflict", async () => {
    const user = userEvent.setup();
    let reads = 0;
    server.use(
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionPage([configuration], 2))),
      http.get("/api/v2/bookings", () => HttpResponse.json(collectionPage([], 10))),
      http.get("/api/v2/booking-configurations/7/notification-subscription", () => {
        reads += 1;
        return HttpResponse.json(
          reads === 1
            ? currentNotificationSubscription
            : { ...currentNotificationSubscription, enabled: true, version: 1 },
        );
      }),
      http.put("/api/v2/booking-configurations/7/notification-subscription", () =>
        HttpResponse.json(
          { status: 409, code: "errors.api.v2.bookingNotifications.subscriptionConflict" },
          { status: 409 },
        ),
      ),
    );
    renderPage();

    await user.click(await screen.findByRole("radio", { name: "booking:notificationSubscriptions.options.on" }));
    await user.click(screen.getByRole("button", { name: "booking:preferences.actions.save" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("booking:notificationSubscriptions.item.conflict");
    await waitFor(() => expect(reads).toBeGreaterThan(1));
    expect(screen.getByRole("radio", { name: "booking:notificationSubscriptions.options.on" })).toBeChecked();
  });

  it("does not render the notification editor without the subscription capability", async () => {
    server.use(
      http.get("/api/v2/booking-configurations", () =>
        HttpResponse.json(
          collectionPage(
            [
              {
                ...configuration,
                capabilities: { ...configuration.capabilities, canManageNotificationSubscription: false },
              },
            ],
            2,
          ),
        ),
      ),
      http.get("/api/v2/bookings", () => HttpResponse.json(collectionPage([], 10))),
    );
    renderPage();

    expect(await screen.findByRole("heading", { name: "Confocal microscope" })).toBeVisible();
    expect(screen.queryByText("booking:notificationSubscriptions.item.title")).not.toBeInTheDocument();
  });
});
