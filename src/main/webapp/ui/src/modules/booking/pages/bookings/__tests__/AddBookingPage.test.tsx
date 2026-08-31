import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  Outlet,
  RouterProvider,
} from "@tanstack/react-router";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { Suspense } from "react";
import { describe, expect, it, vi } from "vitest";
import { oauthTokenHandler } from "@/__tests__/mocks/oauthTokenMocks";
import { server } from "@/__tests__/mswServer";
import { bookingDisplayPreferencesQueryKey } from "@/modules/booking/domain/bookingDisplayPreferences";
import type { CurrentUser } from "@/modules/common/queries/currentUser";
import { inheritedBrowserBookingPreferences } from "../../preferences/bookingPreferencesFixtures";
import { createAddBookingRoute } from "../routes";

const currentUser: CurrentUser = {
  id: 1,
  username: "ada",
  email: "ada@example.com",
  firstName: "Ada",
  lastName: "Lovelace",
  homeFolderId: 2,
  workbenchId: 3,
  hasPiRole: false,
  hasSysAdminRole: false,
  profileImageUrl: null,
  profileImageApiUrl: null,
  orcid: { available: false, id: null },
  capabilities: { canUseInventory: false, canPublish: false, canViewSystem: false },
  livechat: { enabled: false, serverKey: null },
  session: {
    operatedAs: false,
    lastSession: null,
    canUseDevtools: false,
    canOverrideFeatureFlags: false,
    canChangeFeatureFlagBaselines: false,
  },
};

function currentUserHandler(hasSysAdminRole: boolean) {
  return http.get("/api/v2/users/me", () => HttpResponse.json({ ...currentUser, hasSysAdminRole }));
}

const optionDocument = {
  id: 7,
  target: {
    relationTo: "booking-instruments",
    globalId: "IN123",
    value: { id: 123, name: "Confocal microscope", deleted: false },
  },
  timezone: "Europe/Berlin",
  slotGranularityMinutes: 5,
  openingStart: "00:00",
  openingEnd: "24:00",
  bufferBeforeMinutes: 0,
  bufferAfterMinutes: 0,
  maxBookingDurationMinutes: 0,
  allowDoubleBooking: false,
};

const createdBooking = {
  id: 41,
  target: optionDocument.target,
  timezone: optionDocument.timezone,
  start: "2026-08-17T07:00:00Z",
  end: "2026-08-17T08:00:00Z",
  state: "CONFIRMED",
  kind: "BOOKING",
  privacy: "full",
  purpose: null,
  bookedBy: "Ada Lovelace (ada)",
  canEdit: true,
  createdAt: "2026-08-17T06:00:00Z",
  updatedAt: "2026-08-17T06:00:00Z",
};

function page(docs: readonly unknown[]) {
  return {
    docs,
    totalDocs: docs.length,
    limit: 20,
    page: 1,
    pagingCounter: 1,
    totalPages: docs.length ? 1 : 0,
    hasPrevPage: false,
    hasNextPage: false,
    prevPage: null,
    nextPage: null,
  };
}

function renderPage(hasSysAdminRole = false) {
  server.use(currentUserHandler(hasSysAdminRole));
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(bookingDisplayPreferencesQueryKey, inheritedBrowserBookingPreferences);
  const root = createRootRoute({ component: Outlet });
  const booking = createRoute({ getParentRoute: () => root, path: "/booking", component: Outlet });
  const calendar = createRoute({
    getParentRoute: () => booking,
    path: "/calendar",
    component: () => <h1>{"Calendar destination"}</h1>,
  });
  const router = createRouter({
    routeTree: root.addChildren([booking.addChildren([calendar, createAddBookingRoute(booking)])]),
    history: createMemoryHistory({
      initialEntries: ["/booking/calendar/bookings/add?target=IN123&date=2026-08-17"],
    }),
  });
  render(
    <QueryClientProvider client={queryClient}>
      <Suspense fallback={null}>
        <RouterProvider router={router as never} />
      </Suspense>
    </QueryClientProvider>,
  );
  return { queryClient, router };
}

async function fillWindow(user: ReturnType<typeof userEvent.setup>) {
  const start = await screen.findByRole("group", { name: "booking:bookings.form.start" });
  const end = screen.getByRole("group", { name: "booking:bookings.form.end" });
  await user.type(within(start).getByLabelText("booking:bookings.form.time"), "09:00");
  await user.type(within(end).getByLabelText("booking:bookings.form.time"), "10:00");
}

describe("AddBookingPage", () => {
  it("keeps the picker active when the URL target is unavailable", async () => {
    server.use(
      oauthTokenHandler(),
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(page([]))),
    );
    renderPage();

    expect(await screen.findByRole("alert")).toHaveTextContent("booking:bookings.errors.targetUnavailable");
    expect(screen.getByRole("combobox", { name: "booking:bookings.form.item" })).toBeEnabled();
    expect(screen.queryByText("booking:bookings.form.timezone")).not.toBeInTheDocument();
  });

  it("resolves Calendar search, creates a booking, invalidates, and returns", async () => {
    const user = userEvent.setup();
    let body: unknown;
    server.use(
      oauthTokenHandler(),
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(page([optionDocument]))),
      http.post("/api/v2/bookings", async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(createdBooking, { status: 201 });
      }),
    );
    const { queryClient, router } = renderPage();
    const invalidate = vi.spyOn(queryClient, "invalidateQueries");

    expect(await screen.findByText("booking:bookings.form.timezone")).toBeVisible();
    expect(screen.getAllByRole("combobox")).toHaveLength(1);
    await fillWindow(user);
    await user.click(screen.getByRole("button", { name: "booking:bookings.form.submit" }));

    expect(await screen.findByRole("heading", { name: "Calendar destination" })).toBeVisible();
    expect(body).toEqual({
      target: { relationTo: "booking-instruments", value: 123 },
      start: "2026-08-17T07:00:00Z",
      end: "2026-08-17T08:00:00Z",
      purpose: null,
      kind: "BOOKING",
    });
    expect(router.state.location.search).toMatchObject({ date: "2026-08-17", target: "IN123" });
    expect(invalidate).not.toHaveBeenCalledWith({ queryKey: ["api-v2", "bookings"] });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ["api-v2", "bookings", 41] });
  });

  it("retains input and maps an overlap conflict to localized text", async () => {
    const user = userEvent.setup();
    server.use(
      oauthTokenHandler(),
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(page([optionDocument]))),
      http.post("/api/v2/bookings", () =>
        HttpResponse.json(
          { status: 409, code: "errors.api.v2.booking.overlap", detail: "private server detail" },
          { status: 409 },
        ),
      ),
    );
    renderPage();
    await fillWindow(user);

    await user.click(screen.getByRole("button", { name: "booking:bookings.form.submit" }));

    expect(await screen.findByText("booking:bookings.errors.overlap")).toBeVisible();
    expect(screen.queryByText("private server detail")).not.toBeInTheDocument();
    const start = screen.getByRole("group", { name: "booking:bookings.form.start" });
    expect(within(start).getByLabelText("booking:bookings.form.time")).toHaveValue("09:00");
    const submit = screen.getByRole("button", { name: "booking:bookings.form.submit" });
    expect(submit).toBeDisabled();

    await user.clear(within(start).getByLabelText("booking:bookings.form.time"));
    await user.type(within(start).getByLabelText("booking:bookings.form.time"), "09:05");

    expect(screen.queryByText("booking:bookings.errors.overlap")).not.toBeInTheDocument();
    expect(submit).not.toBeDisabled();
  });

  it("keeps the full-page add route booking-only for a sysadmin", async () => {
    const user = userEvent.setup();
    let body: unknown;
    server.use(
      oauthTokenHandler(),
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(page([optionDocument]))),
      http.post("/api/v2/bookings", async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(createdBooking, { status: 201 });
      }),
    );
    renderPage(true);

    expect(await screen.findByText("booking:bookings.form.timezone")).toBeVisible();
    expect(screen.queryByRole("group", { name: "booking:bookings.form.type" })).not.toBeInTheDocument();
    expect(screen.queryByRole("radio", { name: "booking:bookings.form.typeBlockout" })).not.toBeInTheDocument();

    await fillWindow(user);
    await user.click(screen.getByRole("button", { name: "booking:bookings.form.submit" }));

    expect(await screen.findByRole("heading", { name: "Calendar destination" })).toBeVisible();
    expect(body).toEqual({
      target: { relationTo: "booking-instruments", value: 123 },
      start: "2026-08-17T07:00:00Z",
      end: "2026-08-17T08:00:00Z",
      purpose: null,
      kind: "BOOKING",
    });
  });

  it("hides the booking type from users who are not sysadmins", async () => {
    server.use(
      oauthTokenHandler(),
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(page([optionDocument]))),
    );
    renderPage();

    expect(await screen.findByText("booking:bookings.form.timezone")).toBeVisible();
    expect(screen.queryByRole("group", { name: "booking:bookings.form.type" })).not.toBeInTheDocument();
    expect(screen.queryByRole("radio", { name: "booking:bookings.form.typeBlockout" })).not.toBeInTheDocument();
  });

  it("maps a server-side maximum-duration rejection to localized text", async () => {
    const user = userEvent.setup();
    server.use(
      oauthTokenHandler(),
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(page([optionDocument]))),
      http.post("/api/v2/bookings", () =>
        HttpResponse.json(
          { status: 400, code: "errors.api.v2.booking.maximumDuration", detail: "private server detail" },
          { status: 400 },
        ),
      ),
    );
    renderPage();
    await fillWindow(user);

    await user.click(screen.getByRole("button", { name: "booking:bookings.form.submit" }));

    expect(await screen.findByText("booking:bookings.errors.maximumDuration")).toBeVisible();
    expect(screen.queryByText("private server detail")).not.toBeInTheDocument();
  });
});
