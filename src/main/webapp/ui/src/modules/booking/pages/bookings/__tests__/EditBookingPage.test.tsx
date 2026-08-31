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
import { inheritedBrowserBookingPreferences } from "../../preferences/bookingPreferencesFixtures";
import { createEditBookingRoute } from "../routes";

const booking = {
  id: 41,
  target: {
    relationTo: "instruments",
    globalId: "IN123",
    value: { id: 123, name: "Confocal microscope", deleted: false },
  },
  timezone: "Europe/Berlin",
  start: "2026-10-25T01:30:00Z",
  end: "2026-10-25T02:30:00Z",
  state: "CONFIRMED",
  privacy: "full",
  purpose: "Imaging",
  bookedBy: "Ada Lovelace (ada)",
  canEdit: true,
  createdAt: "2026-08-17T06:00:00Z",
  updatedAt: "2026-08-17T06:00:00Z",
} as const;

const configuration = {
  id: 7,
  target: booking.target,
  timezone: booking.timezone,
  slotGranularityMinutes: 5,
  openingStart: "08:00",
  openingEnd: "18:00",
  bufferBeforeMinutes: 0,
  bufferAfterMinutes: 0,
  maxBookingDurationMinutes: 0,
  allowDoubleBooking: false,
};

function renderPage(initialEntry = "/booking/calendar/bookings/41?target=IN123&date=2026-10-25") {
  server.use(
    http.get("/api/v2/booking-configurations", () =>
      HttpResponse.json({
        docs: [configuration],
        totalDocs: 1,
        limit: 20,
        page: 1,
        pagingCounter: 1,
        totalPages: 1,
        hasPrevPage: false,
        hasNextPage: false,
        prevPage: null,
        nextPage: null,
      }),
    ),
  );
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(bookingDisplayPreferencesQueryKey, inheritedBrowserBookingPreferences);
  const root = createRootRoute({ component: Outlet });
  const bookingRoute = createRoute({ getParentRoute: () => root, path: "/booking", component: Outlet });
  const calendar = createRoute({
    getParentRoute: () => bookingRoute,
    path: "/calendar",
    component: () => <h1>{"Calendar destination"}</h1>,
  });
  const router = createRouter({
    routeTree: root.addChildren([bookingRoute.addChildren([calendar, createEditBookingRoute(bookingRoute)])]),
    history: createMemoryHistory({ initialEntries: [initialEntry] }),
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

describe("EditBookingPage", () => {
  it("allows a purpose-only edit when current opening hours reject the unchanged interval", async () => {
    const user = userEvent.setup();
    let body: unknown;
    let selectedFields: string | null = null;
    server.use(
      oauthTokenHandler(),
      http.get("/api/v2/bookings/41", ({ request }) => {
        selectedFields = new URL(request.url).searchParams.get("fields[bookings]");
        return HttpResponse.json(booking);
      }),
      http.patch("/api/v2/bookings/41", async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({ ...booking, purpose: null });
      }),
    );
    const { queryClient, router } = renderPage();
    const invalidate = vi.spyOn(queryClient, "invalidateQueries");

    expect(await screen.findByText("Confocal microscope")).toBeVisible();
    expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
    expect(screen.getByRole("radio", { name: "booking:bookings.form.laterOccurrence" })).toBeChecked();
    expect(screen.queryByText("booking:bookings.errors.openingHours")).not.toBeInTheDocument();
    const purpose = screen.getByLabelText("booking:bookings.form.purpose");
    await user.clear(purpose);
    await user.click(screen.getByRole("button", { name: "booking:bookings.form.save" }));

    expect(await screen.findByRole("heading", { name: "Calendar destination" })).toBeVisible();
    expect(selectedFields).toBe(
      "id,target,timezone,start,end,state,kind,purpose,bookedBy,createdBy,privacy,canEdit,createdAt,updatedAt",
    );
    expect(body).toEqual({ purpose: null });
    expect(router.state.location.search).toMatchObject({ date: "2026-10-25", target: "IN123" });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ["api-v2", "bookings"] });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ["api-v2", "bookings", 41] });
  });

  it.each([
    [
      "busy",
      { ...booking, privacy: "busy", purpose: null, bookedBy: null, canEdit: false },
      "booking:bookings.errors.forbidden",
    ],
    ["cancelled", { ...booking, state: "CANCELLED" }, "booking:bookings.errors.noLongerEditable"],
  ])("does not render editable controls for a %s booking", async (_name, document, message) => {
    server.use(
      oauthTokenHandler(),
      http.get("/api/v2/bookings/41", () => HttpResponse.json(document)),
    );
    renderPage();

    expect(await screen.findByRole("alert")).toHaveTextContent(message);
    expect(screen.queryByRole("button", { name: "booking:bookings.form.save" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "booking:bookings.actions.delete" })).not.toBeInTheDocument();
  });

  it("cancels with the shared dialog and returns to the booking date and target", async () => {
    const user = userEvent.setup();
    let body: unknown;
    server.use(
      oauthTokenHandler(),
      http.get("/api/v2/bookings/41", () => HttpResponse.json(booking)),
      http.patch("/api/v2/bookings/41", async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({ ...booking, state: "CANCELLED" });
      }),
    );
    const { router } = renderPage("/booking/calendar/bookings/41");

    await user.click(await screen.findByRole("button", { name: "booking:bookings.actions.delete" }));
    await user.click(screen.getByRole("button", { name: "common:actions.delete" }));

    expect(await screen.findByRole("heading", { name: "Calendar destination" })).toBeVisible();
    expect(body).toEqual({ state: "CANCELLED" });
    expect(router.state.location.search).toMatchObject({ date: "2026-10-25", target: "IN123" });
  });

  it("refetches a stale booking and replaces the form after a state-transition conflict", async () => {
    const user = userEvent.setup();
    let reads = 0;
    server.use(
      oauthTokenHandler(),
      http.get("/api/v2/bookings/41", () => {
        reads += 1;
        return HttpResponse.json(reads === 1 ? booking : { ...booking, state: "CANCELLED" });
      }),
      http.patch("/api/v2/bookings/41", () =>
        HttpResponse.json(
          { status: 409, code: "errors.api.v2.booking.state.transition", detail: "stale" },
          { status: 409 },
        ),
      ),
    );
    renderPage();
    const start = await screen.findByRole("group", { name: "booking:bookings.form.start" });
    expect(within(start).getByLabelText("booking:bookings.form.time")).toHaveValue("02:30");
    await user.type(screen.getByLabelText("booking:bookings.form.purpose"), " changed");

    await user.click(screen.getByRole("button", { name: "booking:bookings.form.save" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("booking:bookings.errors.noLongerEditable");
    expect(reads).toBeGreaterThan(1);
  });

  it("maps a server-side maximum-duration rejection to localized text", async () => {
    const user = userEvent.setup();
    server.use(
      oauthTokenHandler(),
      http.get("/api/v2/bookings/41", () => HttpResponse.json(booking)),
      http.patch("/api/v2/bookings/41", () =>
        HttpResponse.json(
          { status: 400, code: "errors.api.v2.booking.maximumDuration", detail: "private server detail" },
          { status: 400 },
        ),
      ),
    );
    renderPage();
    await user.type(await screen.findByLabelText("booking:bookings.form.purpose"), " changed");

    await user.click(screen.getByRole("button", { name: "booking:bookings.form.save" }));

    expect(await screen.findByText("booking:bookings.errors.maximumDuration")).toBeVisible();
    expect(screen.queryByText("private server detail")).not.toBeInTheDocument();
  });
});
