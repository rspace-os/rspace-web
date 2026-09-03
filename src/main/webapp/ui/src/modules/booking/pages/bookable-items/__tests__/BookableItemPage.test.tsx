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
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { Suspense } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { server } from "@/__tests__/mswServer";
import { BookingCreationStoreProvider } from "@/modules/booking/creation/bookingCreationStore";
import { bookingDisplayPreferencesQueryKey } from "@/modules/booking/domain/bookingDisplayPreferences";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { inheritedBrowserBookingPreferences } from "../../preferences/bookingPreferencesFixtures";
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

beforeEach(() => {
  mockedUseOauthTokenQuery.mockReturnValue({ data: "token" } as ReturnType<typeof useOauthTokenQuery>);
  mockedUseCurrentUserQuery.mockReturnValue({
    data: { hasSysAdminRole: true, session: { operatedAs: false } },
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

    expect(await screen.findByRole("heading", { level: 1, name: "Confocal microscope" })).toBeVisible();
    expect(screen.getByText("IN123")).toBeVisible();
    expect(screen.queryByRole("link", { name: "booking:bookableItemDetails.viewInventory" })).not.toBeInTheDocument();
    expect(screen.queryByText("Imaging lab")).not.toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "booking:bookableItemDetails.calendarSubscription.trigger" }),
    ).toBeVisible();
    expect(screen.getByRole("button", { name: "booking:bookings.actions.newBooking" })).toBeVisible();
    expect(screen.getByRole("button", { name: "booking:bookableItems.actions.menu" })).toBeVisible();
    expect(screen.getAllByText("UTC").length).toBeGreaterThan(0);
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
    expect(screen.getByText("booking:bookableItemDetails.notAvailable")).toBeVisible();
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
        HttpResponse.json({ active: false, updatedAt: null, subscriptionUrl: null }),
      ),
      http.post("/api/v2/booking-configurations/7/calendar-subscription", () => {
        posts += 1;
        return HttpResponse.json({ active: true, updatedAt: null, subscriptionUrl: null });
      }),
    );
    const user = userEvent.setup();
    renderPage("/booking/bookable-items/IN123/details?edit=true");

    expect(await screen.findByText("booking:bookableItemDetails.archived")).toBeVisible();
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
    await user.click(await screen.findByRole("button", { name: "booking:bookableItemDetails.cancelEdit" }));

    expect(await screen.findByRole("button", { name: "booking:bookableItemDetails.edit" })).toHaveFocus();
    expect(screen.getByText("booking:bookableItemDetails.unlimited")).toBeVisible();
    expect(patched).toBe(false);
  });

  it("keeps calendar status lazy and exposes the trigger to an ordinary readable user", async () => {
    let statusRequests = 0;
    let createRequests = 0;
    vi.mocked(useCurrentUserQuery).mockReturnValue({
      data: { hasSysAdminRole: false },
    } as ReturnType<typeof useCurrentUserQuery>);
    server.use(
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(envelope([configuration], 2))),
      http.get("/api/v2/bookings", () => HttpResponse.json(envelope([], 10))),
      http.get("/api/v2/booking-configurations/7/calendar-subscription", () => {
        statusRequests += 1;
        return HttpResponse.json({ active: false, updatedAt: null, subscriptionUrl: null });
      }),
      http.post("/api/v2/booking-configurations/7/calendar-subscription", () => {
        createRequests += 1;
        return HttpResponse.json({
          active: true,
          updatedAt: "2026-08-27T12:00:00.000Z",
          subscriptionUrl: `https://rspace.example/public/booking/calendars/feed.ics?token=${"c".repeat(43)}`,
        });
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
    mockedUseCurrentUserQuery.mockReturnValue({ data: { hasSysAdminRole: false } } as ReturnType<
      typeof useCurrentUserQuery
    >);
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

    // TableList keeps both the table and card presentations mounted and picks
    // between them with container queries, which jsdom does not evaluate, so
    // every cell matches twice.
    expect((await screen.findAllByText("Morgan Ellis (morgan.ellis)"))[0]).toBeVisible();
    expect(screen.getAllByText("WRITE")[0]).toBeVisible();
    expect(screen.getAllByText("maxBookingDurationMinutes")[0]).toBeVisible();
    await waitFor(() => expect(auditRequests).toBe(1));
  });

  it("does not request events for an invalid lookup and offers a working retry", async () => {
    const user = userEvent.setup();
    let lookupFails = true;
    let eventRequests = 0;
    mockedUseCurrentUserQuery.mockReturnValue({ data: { hasSysAdminRole: false } } as ReturnType<
      typeof useCurrentUserQuery
    >);
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

  it("marks the configuration update timestamp as machine-readable time", async () => {
    const updatedAt = "2026-08-10T12:00:00Z";
    server.use(
      http.get("/api/v2/booking-configurations", () =>
        HttpResponse.json(envelope([{ ...configuration, updatedAt }], 2)),
      ),
      http.get("/api/v2/bookings", () => HttpResponse.json(envelope([], 10))),
    );
    renderPage("/booking/bookable-items/IN123/details");

    const updatedTime = (await screen.findAllByRole("time")).find(
      (element) => element.getAttribute("datetime") === updatedAt,
    );
    expect(updatedTime).toBeVisible();
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
