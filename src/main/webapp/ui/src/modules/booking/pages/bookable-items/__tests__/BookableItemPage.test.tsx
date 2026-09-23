import "@/__tests__/__mocks__/matchMedia";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  Outlet,
  RouterProvider,
} from "@tanstack/react-router";
import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { Suspense } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { server } from "@/__tests__/mswServer";
import { BookingCreationStoreProvider } from "@/modules/booking/creation/bookingCreationStore";
import { bookingDisplayPreferencesQueryKey } from "@/modules/booking/domain/bookingDisplayPreferences";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import bookingEn from "@/modules/common/i18n/locales/en-US/booking.json";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { inheritedBrowserBookingPreferences } from "../../preferences/bookingPreferencesFixtures";
import { BookableItemNotificationSubscription } from "../BookableItemNotificationSubscription";
import { bookerBookingAccess, ownerBookingAccess } from "../mocks/bookableItemsMocks";
import { createBookableItemRoute } from "../routes";

vi.mock("@/modules/common/hooks/auth", () => ({ useOauthTokenQuery: vi.fn() }));
vi.mock("@/modules/common/queries/currentUser", () => ({ useCurrentUserQuery: vi.fn() }));

const configuration = {
  id: 7,
  configurationVersion: 0,
  state: "ACTIVE",
  target: {
    relationTo: "booking-instruments",
    value: {
      id: 123,
      name: "Confocal microscope",
      deleted: false,
      parentContainerName: "Imaging lab",
      parentContainerGlobalId: "IC456",
    },
    globalId: "IN123",
  },
  enabled: true,
  timezone: "UTC",
  slotGranularityMinutes: 5,
  openingStart: "08:00",
  openingEnd: "17:30",
  bufferBeforeMinutes: 5,
  bufferAfterMinutes: 15,
  maxBookingDurationMinutes: 0,
  allowDoubleBooking: false,
  createdAt: "2026-08-17T00:00:00Z",
  createdByName: "Grace Hopper (grace)",
  updatedAt: null,
  ...ownerBookingAccess,
};
const booking = {
  id: 41,
  version: 0,
  target: configuration.target,
  timezone: "UTC",
  start: "2026-08-25T09:00:00Z",
  end: "2026-08-25T10:00:00Z",
  state: "CONFIRMED",
  privacy: "full",
  purpose: null,
  bookedBy: "Ada Lovelace (ada)",
  canEdit: false,
  canCancel: false,
  createdAt: "2026-08-17T00:00:00Z",
  updatedAt: "2026-08-17T00:00:00Z",
};

function envelope(docs: unknown[], limit: number) {
  return {
    docs,
    totalDocs: docs.length,
    limit,
    page: 1,
    pagingCounter: 1,
    totalPages: docs.length === 0 ? 0 : 1,
    hasPrevPage: false,
    hasNextPage: false,
    prevPage: null,
    nextPage: null,
  };
}

const mockedUseOauthTokenQuery = vi.mocked(useOauthTokenQuery);
const mockedUseCurrentUserQuery = vi.mocked(useCurrentUserQuery);
const { renderWithRealI18n } = await import("@/__tests__/helpers/realI18n");
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
  mockedUseOauthTokenQuery.mockReturnValue({ data: "token" } as ReturnType<typeof useOauthTokenQuery>);
  mockedUseCurrentUserQuery.mockReturnValue({
    data: { id: 1, hasSysAdminRole: true, session: { operatedAs: false } },
  } as ReturnType<typeof useCurrentUserQuery>);
});

function renderPage(initialEntry = "/booking/bookable-items/IN123") {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(bookingDisplayPreferencesQueryKey, inheritedBrowserBookingPreferences);
  const root = createRootRoute({ component: Outlet });
  const bookingRoute = createRoute({ getParentRoute: () => root, path: "/booking", component: Outlet });
  const router = createRouter({
    routeTree: root.addChildren([bookingRoute.addChildren([createBookableItemRoute(bookingRoute)])]),
    history: createMemoryHistory({ initialEntries: [initialEntry] }),
  });
  const result = render(
    <QueryClientProvider client={queryClient}>
      <BookingCreationStoreProvider>
        <Suspense fallback={null}>
          <RouterProvider router={router as never} />
        </Suspense>
      </BookingCreationStoreProvider>
    </QueryClientProvider>,
  );
  return { ...result, queryClient, router };
}

describe("BookableItemPage", () => {
  it("keeps the draft's original version through background and conflict refreshes", async () => {
    const user = userEvent.setup();
    let current = configuration;
    const versions: (string | null)[] = [];
    server.use(
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(envelope([current], 2))),
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
    current = { ...configuration, configurationVersion: 1, openingEnd: "20:00" };
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

  it.each([403, 404])("conceals the previous resource while navigating to a delayed %s response", async (status) => {
    const response = Promise.withResolvers<void>();
    server.use(
      http.get("/api/v2/booking-configurations", async ({ request }) => {
        if (new URL(request.url).searchParams.get("where")?.includes("IN124")) {
          await response.promise;
          return new HttpResponse(null, { status });
        }
        return HttpResponse.json(envelope([configuration], 2));
      }),
      http.get("/api/v2/bookings", () => HttpResponse.json(envelope([], 10))),
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

  it("keeps authorized content during refetch but removes it when access is denied", async () => {
    server.use(
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(envelope([configuration], 2))),
      http.get("/api/v2/bookings", () => HttpResponse.json(envelope([], 10))),
    );
    const { queryClient } = renderPage();
    expect(await screen.findByRole("heading", { name: "Confocal microscope" })).toBeVisible();
    const response = Promise.withResolvers<void>();
    const requested = Promise.withResolvers<void>();
    server.use(
      http.get("/api/v2/booking-configurations", async () => {
        requested.resolve();
        await response.promise;
        return new HttpResponse(null, { status: 403 });
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
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(envelope([configuration], 2))),
      http.get("/api/v2/bookings", () => HttpResponse.json(envelope([], 10))),
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

  it("renders identity, rules, role-sensitive actions, and event requests with one cutoff", async () => {
    const user = userEvent.setup();
    const eventFilters: string[] = [];
    server.use(
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(envelope([configuration], 2))),
      http.get("/api/v2/bookings", ({ request }) => {
        eventFilters.push(new URL(request.url).searchParams.get("where") ?? "");
        return HttpResponse.json(envelope([], 10));
      }),
    );
    const { container } = renderPage();

    const heading = await screen.findByRole("heading", { level: 1, name: "Confocal microscope" });
    expect(heading).toHaveClass("text-2xl", "font-semibold");
    expect(screen.getByText("IN123")).toBeVisible();
    expect(screen.queryByRole("link", { name: "booking:bookableItemDetails.viewInventory" })).not.toBeInTheDocument();
    const facts = screen.getByRole("complementary", { name: "booking:bookableItemDetails.about" });
    const tabList = screen.getByRole("tablist");
    expect(heading.closest("section")?.parentElement).toBe(tabList.parentElement);
    expect(facts.parentElement?.parentElement?.parentElement).toBe(tabList.parentElement);
    expect(tabList.compareDocumentPosition(facts) & Node.DOCUMENT_POSITION_FOLLOWING).not.toBe(0);
    expect(within(facts).getByRole("link", { name: "Imaging lab" })).toHaveAttribute("href", "/globalId/IC456");
    expect(within(facts).getByText("Grace Hopper (grace)")).toBeVisible();
    expect(within(facts).getByText("booking:bookableItemDetails.fields.createdAt")).toBeVisible();
    expect(
      screen.getByRole("button", { name: "booking:bookableItemDetails.calendarSubscription.trigger" }),
    ).toBeVisible();
    expect(screen.getByRole("button", { name: "booking:bookings.actions.newBooking" })).toBeVisible();
    expect(screen.getByRole("button", { name: "booking:bookableItems.actions.menu" })).toBeVisible();
    expect(within(heading.closest("section") as HTMLElement).queryByText("UTC")).not.toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "booking:bookableItemDetails.tabs.bookings" })).toHaveAttribute(
      "aria-selected",
      "true",
    );
    expect(screen.getByText("booking:bookableItemDetails.upcoming")).toBeVisible();
    expect(screen.getByText("booking:bookableItemDetails.past")).toBeVisible();
    await waitFor(() => expect(eventFilters).toHaveLength(2));

    await user.click(screen.getByRole("tab", { name: "booking:bookableItemDetails.tabs.details" }));
    expect(screen.getByText("08:00–17:30")).toBeVisible();
    expect(screen.getByText("booking:bookableItemDetails.unlimited")).toBeVisible();
    expect(screen.getByRole("tab", { name: "booking:bookableItemDetails.tabs.details" })).toHaveAttribute(
      "aria-selected",
      "true",
    );
    expect(screen.getByRole("tab", { name: "booking:bookableItemDetails.tabs.audit" })).toBeVisible();
    expect(screen.getByRole("button", { name: "booking:bookableItemDetails.edit" })).toBeVisible();
    const boundaries = eventFilters.map((where) => where.match(/end=(?:gt|le)=([^;]+)/)?.[1]);
    expect(boundaries[0]).toBeTruthy();
    expect(boundaries[0]).toBe(boundaries[1]);
    await expectAccessible(container);
  });

  it("saves an owner notification choice and clears saved state after edits", async () => {
    const user = userEvent.setup();
    server.use(
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(envelope([configuration], 2))),
      http.get("/api/v2/bookings", () => HttpResponse.json(envelope([], 10))),
    );
    renderPage();

    expect(
      await screen.findByRole("radiogroup", { name: "booking:notificationSubscriptions.item.label" }),
    ).toBeVisible();
    expect(screen.getByText("booking:notificationSubscriptions.item.description")).toBeVisible();
    expect(screen.getByRole("link", { name: "booking:notificationSubscriptions.item.profileLink" })).toHaveAttribute(
      "href",
      "/userform#prefContainer",
    );
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
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(envelope([configuration], 2))),
      http.get("/api/v2/bookings", () => HttpResponse.json(envelope([], 10))),
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
          envelope(
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
      http.get("/api/v2/bookings", () => HttpResponse.json(envelope([], 10))),
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
  });

  it("reloads the latest item choice after a subscription conflict", async () => {
    const user = userEvent.setup();
    let reads = 0;
    server.use(
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(envelope([configuration], 2))),
      http.get("/api/v2/bookings", () => HttpResponse.json(envelope([], 10))),
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
          envelope(
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
      http.get("/api/v2/bookings", () => HttpResponse.json(envelope([], 10))),
    );
    renderPage();

    expect(await screen.findByRole("heading", { name: "Confocal microscope" })).toBeVisible();
    expect(screen.queryByText("booking:notificationSubscriptions.item.title")).not.toBeInTheDocument();
  });

  it("shows the inventory item's access as read-only", async () => {
    let bookingAccessRequests = 0;
    server.use(
      http.get("/api/v2/booking-configurations", () =>
        HttpResponse.json(
          envelope(
            [
              {
                ...configuration,
                capabilities: { ...configuration.capabilities, canViewAccess: false },
              },
            ],
            2,
          ),
        ),
      ),
      http.get("/api/inventory/v1/instruments/123", () =>
        HttpResponse.json({
          sharingMode: "WHITELIST",
          sharedWith: [
            { group: { id: 11, name: "Imaging lab" }, shared: true, itemOwnerGroup: true },
            { group: { id: 12, name: "Microscopy collaborators" }, shared: false, itemOwnerGroup: false },
          ],
        }),
      ),
      http.get("/api/v2/booking-configurations/7/access", () => {
        bookingAccessRequests += 1;
        return HttpResponse.json({});
      }),
    );
    const { router } = renderPage("/booking/bookable-items/IN123/access");
    const table = await screen.findByRole("table");
    expect(within(table).getByText("Microscopy collaborators")).toBeInTheDocument();
    expect(
      within(table)
        .getAllByRole("checkbox")
        .every((checkbox) => checkbox.getAttribute("aria-disabled") === "true"),
    ).toBe(true);
    const inventoryPermissions = screen.getByRole("radiogroup", { name: "inventory:fields.accessPermissions.label" });
    expect(
      within(inventoryPermissions)
        .getAllByRole("radio")
        .every((radio) => radio.getAttribute("aria-disabled") === "true"),
    ).toBe(true);
    expect(bookingAccessRequests).toBe(0);
    const details = screen.getByRole("tab", { name: "booking:bookableItemDetails.tabs.details" });
    await userEvent.setup().click(details);
    await waitFor(() => expect(router.state.location.pathname).toBe("/booking/bookable-items/IN123/details"));
  });

  it("archives an owned configuration after confirmation", async () => {
    let deleteRequest: Request | undefined;
    server.use(
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(envelope([configuration], 2))),
      http.get("/api/v2/bookings", () => HttpResponse.json(envelope([], 10))),
      http.delete("/api/v2/booking-configurations/7", ({ request }) => {
        deleteRequest = request;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    const { router } = renderPage();

    await user.click(await screen.findByRole("button", { name: "booking:bookableItems.actions.menu" }));
    await user.click(await screen.findByRole("menuitem", { name: "booking:bookableItems.actions.archive" }));
    const dialog = screen.getByRole("alertdialog", { name: "booking:bookableItemDetails.archiveDialog.title" });
    await expectAccessible(dialog);
    await user.click(within(dialog).getByRole("button", { name: "booking:bookableItemDetails.archiveDialog.confirm" }));

    await waitFor(() => expect(deleteRequest).toBeDefined());
    expect(deleteRequest?.headers.get("Authorization")).toBe("Bearer token");
    expect(deleteRequest?.headers.get("If-Match")).toBe('"0"');
    expect(router.state.location.pathname).toBe("/booking/bookable-items/IN123");
  });

  it("keeps archived configurations on the canonical route with read-only controls", async () => {
    const archived = { ...configuration, state: "ARCHIVED" as const };
    let posts = 0;
    server.use(
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(envelope([archived], 2))),
      http.get("/api/v2/bookings", () => HttpResponse.json(envelope([], 10))),
      http.get("/api/v2/booking-configurations/7/calendar-subscription", () =>
        HttpResponse.json(
          { active: false, updatedAt: null, subscriptionUrl: null },
          { headers: { ETag: '"inactive"' } },
        ),
      ),
      http.post("/api/v2/booking-configurations/7/calendar-subscription", () => {
        posts += 1;
        return HttpResponse.json({ active: true, updatedAt: null, subscriptionUrl: null });
      }),
    );
    const user = userEvent.setup();
    renderPage("/booking/bookable-items/IN123/details?edit=true");

    expect(await screen.findByText("booking:bookableItemDetails.archived")).toBeVisible();
    expect(screen.queryByText("booking:bookableItemDetails.enabled")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "booking:bookableItemDetails.edit" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "booking:bookings.actions.newBooking" })).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "booking:bookableItemDetails.calendarSubscription.trigger" }));
    expect(
      await screen.findByText("booking:bookableItemDetails.calendarSubscription.archivedUnavailable"),
    ).toBeVisible();
    expect(posts).toBe(0);
  });

  it("restores an archived configuration with a state-only conditional PATCH", async () => {
    const archived = { ...configuration, configurationVersion: 2, state: "ARCHIVED" as const };
    let current = archived as typeof archived | typeof configuration;
    let patchRequest: Request | undefined;
    server.use(
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(envelope([current], 2))),
      http.get("/api/v2/bookings", () => HttpResponse.json(envelope([], 10))),
      http.patch("/api/v2/booking-configurations/7", async ({ request }) => {
        patchRequest = request;
        current = { ...configuration, configurationVersion: 3 };
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "booking:bookableItems.actions.menu" }));
    await user.click(await screen.findByRole("menuitem", { name: "booking:bookableItems.actions.restore" }));

    await waitFor(() => expect(patchRequest).toBeDefined());
    expect(patchRequest?.headers.get("Authorization")).toBe("Bearer token");
    expect(patchRequest?.headers.get("Content-Type")).toBe("application/json");
    expect(patchRequest?.headers.get("If-Match")).toBe('"2"');
    await expect(patchRequest?.json()).resolves.toEqual({ state: "ACTIVE" });
    expect(await screen.findByRole("button", { name: "booking:bookings.actions.newBooking" })).toBeVisible();
    expect(screen.getByRole("button", { name: "booking:bookableItems.actions.menu" })).toHaveFocus();
  });

  it("requires the exact item name before directly deleting an active configuration", async () => {
    const active = { ...configuration, configurationVersion: 2 };
    let deleteRequest: Request | undefined;
    server.use(
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(envelope([active], 2))),
      http.get("/api/v2/bookings", () => HttpResponse.json(envelope([], 10))),
      http.delete("/api/v2/booking-configurations/7", ({ request }) => {
        deleteRequest = request;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    const { router } = renderPage();

    await user.click(await screen.findByRole("button", { name: "booking:bookableItems.actions.menu" }));
    await user.click(await screen.findByRole("menuitem", { name: "booking:bookableItems.actions.deletePermanently" }));
    const dialog = screen.getByRole("alertdialog", {
      name: "booking:bookableItemDetails.permanentDeleteDialog.title",
    });
    const confirm = within(dialog).getByRole("button", {
      name: "booking:bookableItemDetails.permanentDeleteDialog.confirm",
    });
    const name = within(dialog).getByLabelText("booking:bookableItemDetails.permanentDeleteDialog.confirmationLabel");
    expect(confirm).toBeDisabled();
    await user.type(name, "confocal microscope");
    expect(confirm).toBeDisabled();
    await user.clear(name);
    await user.type(name, "Confocal microscope");
    expect(confirm).toBeEnabled();
    await expectAccessible(dialog);
    await user.click(confirm);

    await waitFor(() => expect(deleteRequest).toBeDefined());
    expect(new URL(deleteRequest?.url ?? "http://localhost").searchParams.get("permanent")).toBe("true");
    expect(deleteRequest?.headers.get("If-Match")).toBe('"2"');
    expect(deleteRequest?.headers.get("Authorization")).toBe("Bearer token");
    await waitFor(() => expect(router.state.location.pathname).toBe("/booking/config/bookable-items"));
  });

  it("does not offer permanent deletion while a sysadmin is operating as another user", async () => {
    mockedUseCurrentUserQuery.mockReturnValue({
      data: { hasSysAdminRole: true, session: { operatedAs: true } },
    } as ReturnType<typeof useCurrentUserQuery>);
    const archived = { ...configuration, state: "ARCHIVED" as const };
    server.use(
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(envelope([archived], 2))),
      http.get("/api/v2/bookings", () => HttpResponse.json(envelope([], 10))),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "booking:bookableItems.actions.menu" }));
    expect(await screen.findByRole("menuitem", { name: "booking:bookableItems.actions.restore" })).toBeVisible();
    expect(
      screen.queryByRole("menuitem", { name: "booking:bookableItems.actions.deletePermanently" }),
    ).not.toBeInTheDocument();
  });

  it("saves booking rules in place and returns to the read-out", async () => {
    const user = userEvent.setup();
    const patches: unknown[] = [];
    server.use(
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(envelope([configuration], 2))),
      http.get("/api/v2/bookings", () => HttpResponse.json(envelope([], 10))),
      http.patch("/api/v2/booking-configurations/7", async ({ request }) => {
        patches.push(await request.json());
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderPage();

    await user.click(await screen.findByRole("tab", { name: "booking:bookableItemDetails.tabs.details" }));
    await user.click(await screen.findByRole("button", { name: "booking:bookableItemDetails.edit" }));
    const maximumDuration = await screen.findByLabelText("booking:bookableItemDetails.fields.maximumDuration");
    await user.clear(maximumDuration);
    await user.type(maximumDuration, "60");
    await user.click(screen.getByRole("button", { name: "booking:bookableItems.actions.save" }));

    await waitFor(() => expect(patches).toHaveLength(1));
    expect(patches[0]).toMatchObject({ maxBookingDurationMinutes: 60 });
    expect(patches[0]).not.toHaveProperty("timezone");
    const editButton = await screen.findByRole("button", { name: "booking:bookableItemDetails.edit" });
    expect(editButton).toHaveFocus();
  });

  it("cancels an edit without sending a request", async () => {
    const user = userEvent.setup();
    let patched = false;
    server.use(
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(envelope([configuration], 2))),
      http.get("/api/v2/bookings", () => HttpResponse.json(envelope([], 10))),
      http.patch("/api/v2/booking-configurations/7", () => {
        patched = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderPage();

    await user.click(await screen.findByRole("tab", { name: "booking:bookableItemDetails.tabs.details" }));
    await user.click(await screen.findByRole("button", { name: "booking:bookableItemDetails.edit" }));
    await user.click(
      within(screen.getByRole("tabpanel")).getByRole("button", { name: "booking:bookableItemDetails.cancelEdit" }),
    );

    expect(await screen.findByRole("button", { name: "booking:bookableItemDetails.edit" })).toHaveFocus();
    expect(screen.getByText("booking:bookableItemDetails.unlimited")).toBeVisible();
    expect(patched).toBe(false);
  });

  it("keeps calendar status lazy and exposes the trigger to an ordinary readable user", async () => {
    let statusRequests = 0;
    let createRequests = 0;
    vi.mocked(useCurrentUserQuery).mockReturnValue({
      data: { hasSysAdminRole: false, session: { operatedAs: false } },
    } as ReturnType<typeof useCurrentUserQuery>);
    server.use(
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(envelope([configuration], 2))),
      http.get("/api/v2/bookings", () => HttpResponse.json(envelope([], 10))),
      http.get("/api/v2/booking-configurations/7/calendar-subscription", () => {
        statusRequests += 1;
        return HttpResponse.json(
          { active: false, updatedAt: null, subscriptionUrl: null },
          { headers: { ETag: '"inactive"' } },
        );
      }),
      http.post("/api/v2/booking-configurations/7/calendar-subscription", () => {
        createRequests += 1;
        return HttpResponse.json(
          {
            active: true,
            updatedAt: "2026-08-27T12:00:00.000Z",
            subscriptionUrl: `https://rspace.example/public/booking/calendars/feed.ics?token=${"c".repeat(43)}`,
          },
          { headers: { ETag: '"current"' } },
        );
      }),
    );
    const user = userEvent.setup();
    renderPage();
    const trigger = await screen.findByRole("button", {
      name: "booking:bookableItemDetails.calendarSubscription.trigger",
    });
    expect(statusRequests).toBe(0);
    await user.click(trigger);
    await screen.findByRole("textbox", { name: "booking:bookableItemDetails.calendarSubscription.copyPrompt" });
    expect(statusRequests).toBe(1);
    expect(createRequests).toBe(1);
  });

  it("keeps the editor and draft open when PATCH fails", async () => {
    const user = userEvent.setup();
    server.use(
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(envelope([configuration], 2))),
      http.get("/api/v2/bookings", () => HttpResponse.json(envelope([], 10))),
      http.patch("/api/v2/booking-configurations/7", () => HttpResponse.json({}, { status: 500 })),
    );
    renderPage("/booking/bookable-items/IN123/details?edit=true");

    const maximumDuration = await screen.findByLabelText("booking:bookableItemDetails.fields.maximumDuration");
    await user.clear(maximumDuration);
    await user.type(maximumDuration, "60");
    const saveButton = screen.getByRole("button", { name: "booking:bookableItems.actions.save" });
    await user.click(saveButton);

    expect(await screen.findByRole("alert")).toHaveTextContent("booking:bookableItems.editError");
    expect(maximumDuration).toHaveValue(60);
    expect(saveButton).toHaveFocus();
  });

  it("keeps a caller without edit capability in view mode on the direct edit URL", async () => {
    mockedUseCurrentUserQuery.mockReturnValue({
      data: { hasSysAdminRole: false, session: { operatedAs: false } },
    } as ReturnType<typeof useCurrentUserQuery>);
    server.use(
      http.get("/api/v2/booking-configurations", () =>
        HttpResponse.json(envelope([{ ...configuration, ...bookerBookingAccess }], 2)),
      ),
      http.get("/api/v2/bookings", () => HttpResponse.json(envelope([], 10))),
    );
    renderPage("/booking/bookable-items/IN123/details?edit=true");

    expect(await screen.findByText("booking:bookableItemDetails.unlimited")).toBeVisible();
    expect(screen.queryByRole("button", { name: "booking:bookableItems.actions.save" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "booking:bookableItemDetails.edit" })).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "booking:bookableItemDetails.actions.archive" }),
    ).not.toBeInTheDocument();
  });

  it("loads the audit trail only once its tab is opened", async () => {
    const user = userEvent.setup();
    let auditRequests = 0;
    server.use(
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(envelope([configuration], 2))),
      http.get("/api/v2/bookings", () => HttpResponse.json(envelope([], 10))),
      http.get("/api/v2/booking-configurations/7/audit", () => {
        auditRequests += 1;
        return HttpResponse.json({
          ...envelope(
            [
              {
                eventId: "a".repeat(64),
                timestamp: "2026-08-25T10:42:18Z",
                username: "morgan.ellis",
                fullName: "Morgan Ellis",
                domain: "RECORD",
                action: "WRITE",
                description: "Updated booking configuration IN123",
                payload: { enabled: true, maxBookingDurationMinutes: 240 },
              },
            ],
            20,
          ),
          snapshotDate: "2026-08-25",
          snapshotFingerprint: "b".repeat(64),
        });
      }),
    );
    renderPage();

    await screen.findByRole("tab", { name: "booking:bookableItemDetails.tabs.audit" });
    expect(auditRequests).toBe(0);

    await user.click(screen.getByRole("tab", { name: "booking:bookableItemDetails.tabs.audit" }));

    const item = await screen.findByRole("article", { name: "Updated booking configuration IN123" });
    expect(within(item).getByText("Morgan Ellis (morgan.ellis)")).toBeVisible();
    expect(within(item).getByText("WRITE")).toBeVisible();
    expect(
      within(item).queryByText("booking:bookableItemDetails.audit.values.maximumDuration"),
    ).not.toBeInTheDocument();
    await user.click(within(item).getByRole("button", { name: "common:actions.expand" }));
    expect(within(item).getByText("booking:bookableItemDetails.audit.values.maximumDuration")).toBeVisible();
    await waitFor(() => expect(auditRequests).toBe(1));
  });

  it("does not request events for an invalid lookup and offers a working retry", async () => {
    const user = userEvent.setup();
    let lookupFails = true;
    let eventRequests = 0;
    mockedUseCurrentUserQuery.mockReturnValue({
      data: { hasSysAdminRole: false, session: { operatedAs: false } },
    } as ReturnType<typeof useCurrentUserQuery>);
    server.use(
      http.get("/api/v2/booking-configurations", () =>
        lookupFails ? HttpResponse.json(envelope([], 2)) : HttpResponse.json(envelope([configuration], 2)),
      ),
      http.get("/api/v2/bookings", () => {
        eventRequests += 1;
        return HttpResponse.json(envelope([], 10));
      }),
    );
    renderPage();

    expect(await screen.findByText("booking:bookableItemDetails.error.title")).toBeVisible();
    expect(eventRequests).toBe(0);
    lookupFails = false;
    await user.click(screen.getByRole("button", { name: "common:actions.retry" }));

    expect(await screen.findByText("Confocal microscope")).toBeVisible();
    expect(screen.queryByRole("link", { name: "booking:bookableItemDetails.edit" })).not.toBeInTheDocument();
    await waitFor(() => expect(eventRequests).toBe(2));
  });

  it("does not show the configuration update timestamp in booking rules", async () => {
    const updatedAt = "2026-08-10T12:00:00Z";
    server.use(
      http.get("/api/v2/booking-configurations", () =>
        HttpResponse.json(envelope([{ ...configuration, updatedAt }], 2)),
      ),
      http.get("/api/v2/bookings", () => HttpResponse.json(envelope([], 10))),
    );
    renderPage("/booking/bookable-items/IN123/details");

    await screen.findByText("booking:bookableItemDetails.rules");
    expect(screen.queryByText("booking:bookableItemDetails.fields.updatedAt")).not.toBeInTheDocument();
  });

  it("reformats existing events after a configuration timezone refresh without refetching events", async () => {
    let timezone = "UTC";
    let eventRequests = 0;
    server.use(
      http.get("/api/v2/booking-configurations", () =>
        HttpResponse.json(envelope([{ ...configuration, timezone }], 2)),
      ),
      http.get("/api/v2/bookings", () => {
        eventRequests += 1;
        return HttpResponse.json(envelope([booking], 10));
      }),
    );
    const { queryClient } = renderPage();

    const times = await screen.findAllByRole("time");
    const utcText = times[0].textContent;
    expect(eventRequests).toBe(2);
    timezone = "Europe/Berlin";
    await queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-configurations", "target", "IN123"] });

    await waitFor(() => expect(screen.getAllByRole("time")[0]).not.toHaveTextContent(utcText ?? ""));
    expect(eventRequests).toBe(2);
  });
});
