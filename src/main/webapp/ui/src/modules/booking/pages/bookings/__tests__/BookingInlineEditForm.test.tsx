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
import { describe, expect, it } from "vitest";
import { OAUTH_TOKEN, oauthTokenHandler } from "@/__tests__/mocks/oauthTokenMocks";
import { server } from "@/__tests__/mswServer";
import { bookingDisplayPreferencesQueryKey } from "@/modules/booking/domain/bookingDisplayPreferences";
import { bookableItemFixtures } from "../../bookable-items/mocks/bookableItemsMocks";
import { inheritedBrowserBookingPreferences } from "../../preferences/bookingPreferencesFixtures";
import { createBookingEventTestRouteTree } from "./bookingEventTestHarness";

const document = {
  id: 41,
  version: 7,
  target: {
    relationTo: "booking-instruments",
    value: { id: 123, name: "Confocal microscope", deleted: false },
    globalId: "IN123",
  },
  canViewConfiguration: true,
  timezone: "Europe/Berlin",
  start: "2026-10-19T08:00:00Z",
  end: "2026-10-19T10:00:00Z",
  state: "CONFIRMED",
  kind: "BOOKING",
  privacy: "full",
  purpose: "Cell imaging",
  bookedBy: "Ada Lovelace (ada)",
  createdBy: "Grace Hopper (grace)",
  canEdit: true,
  canCancel: true,
  createdAt: "2026-08-01T09:00:00Z",
  updatedAt: "2026-08-02T10:00:00Z",
} as const;

const configurationResponse = {
  docs: [bookableItemFixtures[0]],
  totalDocs: 1,
  limit: 2,
  page: 1,
  pagingCounter: 1,
  totalPages: 1,
  hasPrevPage: false,
  hasNextPage: false,
  prevPage: null,
  nextPage: null,
};

function renderEdit() {
  server.use(oauthTokenHandler(true));
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(["rspace.common.auth", "oauthToken", "v2"], OAUTH_TOKEN);
  queryClient.setQueryData(bookingDisplayPreferencesQueryKey, inheritedBrowserBookingPreferences);
  const root = createRootRoute({ component: Outlet });
  const booking = createRoute({ getParentRoute: () => root, path: "/booking", component: Outlet });
  const myBookings = createRoute({ getParentRoute: () => booking, path: "/my-bookings", component: Outlet });
  const item = createRoute({
    getParentRoute: () => booking,
    path: "/bookable-items/$globalId/{-$tab}",
    component: Outlet,
  });
  const router = createRouter({
    routeTree: root.addChildren([booking.addChildren([myBookings, item, createBookingEventTestRouteTree(booking)])]),
    history: createMemoryHistory({ initialEntries: ["/booking/calendar/bookings/41/edit"] }),
  });
  return {
    ...render(
      <QueryClientProvider client={queryClient}>
        <Suspense fallback={null}>
          <RouterProvider router={router as never} />
        </Suspense>
      </QueryClientProvider>,
    ),
    router,
    queryClient,
  };
}

describe("BookingInlineEditForm", () => {
  it("saves changed fields against the frozen version and returns to the mounted readout", async () => {
    let current = {
      ...document,
      version: document.version as number,
      purpose: document.purpose as string,
    };
    let reads = 0;
    let patch: Request | undefined;
    server.use(
      http.get("/api/v2/bookings/41", () => {
        reads += 1;
        return HttpResponse.json(current);
      }),
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(configurationResponse)),
      http.patch("/api/v2/bookings/41", async ({ request }) => {
        patch = request.clone();
        current = { ...current, version: 8, purpose: "Updated imaging" };
        return HttpResponse.json(current);
      }),
    );
    const { router } = renderEdit();
    const user = userEvent.setup();

    const purpose = await screen.findByDisplayValue("Cell imaging");
    const itemInformation = await screen.findByRole("complementary", {
      name: "booking:bookings.itemInformation.title",
    });
    expect(within(itemInformation).getByText("Confocal microscope")).toBeVisible();
    expect(within(itemInformation).getByText("booking:bookableItemDetails.fields.openingHours")).toBeVisible();
    expect(screen.getByRole("heading", { name: "booking:bookings.details.edit.title" })).toHaveFocus();
    await user.clear(purpose);
    await user.type(purpose, "Updated imaging");
    await user.click(screen.getByRole("button", { name: "booking:bookings.form.save" }));

    await waitFor(() => expect(router.state.location.pathname).toBe("/booking/calendar/bookings/41"));
    expect(await screen.findByText("Updated imaging")).toBeVisible();
    expect(patch?.headers.get("If-Match")).toBe('"7"');
    expect(await patch?.json()).toEqual({ purpose: "Updated imaging" });
    expect(reads).toBe(2);
  });

  it("keeps the draft and permanently blocks save after a conflict without refetching", async () => {
    let reads = 0;
    server.use(
      http.get("/api/v2/bookings/41", () => {
        reads += 1;
        return HttpResponse.json(document);
      }),
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(configurationResponse)),
      http.patch("/api/v2/bookings/41", () =>
        HttpResponse.json(
          { status: 412, code: "errors.api.v2.booking.concurrentModification", detail: "stale" },
          { status: 412 },
        ),
      ),
    );
    renderEdit();
    const user = userEvent.setup();
    const purpose = await screen.findByDisplayValue("Cell imaging");
    await user.clear(purpose);
    await user.type(purpose, "Keep this draft");
    await user.click(screen.getByRole("button", { name: "booking:bookings.form.save" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("booking:bookings.details.edit.conflict");
    expect(alert).toHaveFocus();
    expect(screen.getByDisplayValue("Keep this draft")).toBeVisible();
    expect(screen.getByRole("button", { name: "booking:bookings.form.save" })).toBeDisabled();
    expect(reads).toBe(1);
  });

  it.each([
    [409, "errors.api.v2.booking.overlap", "booking:bookings.errors.overlap"],
    [500, "errors.api.v2.internal", "booking:bookings.errors.outcomeUncertain"],
  ])("retains a %i save error until the draft changes", async (status, code, message) => {
    server.use(
      http.get("/api/v2/bookings/41", () => HttpResponse.json(document)),
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(configurationResponse)),
      http.patch("/api/v2/bookings/41", () => HttpResponse.json({ status, code, detail: "save failed" }, { status })),
    );
    renderEdit();
    const user = userEvent.setup();
    const purpose = await screen.findByDisplayValue("Cell imaging");
    await user.type(purpose, " updated");
    const save = screen.getByRole("button", { name: "booking:bookings.form.save" });
    await user.click(save);
    await waitFor(() => expect(save).toHaveAttribute("aria-busy", "false"));
    expect(screen.getByRole("alert")).toHaveTextContent(message);
    if (status === 409) expect(save).toBeDisabled();
    await user.type(purpose, " again");
    await waitFor(() => expect(screen.queryByRole("alert")).not.toBeInTheDocument());
    expect(save).toBeEnabled();
  });

  it.each(["network", "server"])(
    "reconciles a committed edit after a lost %s response without replacing the draft",
    async (failure) => {
      const user = userEvent.setup();
      let committed = false;
      let finishRead: (() => void) | undefined;
      const reading = new Promise<void>((resolve) => {
        finishRead = resolve;
      });
      const updated = {
        ...document,
        version: 8,
        start: "2026-10-19T09:00:00Z",
        end: "2026-10-19T11:00:00Z",
        purpose: "Server value",
      };
      server.use(
        http.get("/api/v2/bookings/41", async () => {
          if (committed) await reading;
          return HttpResponse.json(committed ? updated : document);
        }),
        http.get("/api/v2/booking-configurations", () => HttpResponse.json(configurationResponse)),
        http.patch("/api/v2/bookings/41", () => {
          committed = true;
          return failure === "network" ? HttpResponse.error() : HttpResponse.json({ status: 503 }, { status: 503 });
        }),
      );
      const { router, queryClient } = renderEdit();
      const purpose = await screen.findByDisplayValue("Cell imaging");
      await user.clear(purpose);
      await user.type(purpose, "Keep this draft");
      const start = within(screen.getByRole("group", { name: "booking:bookings.form.start" })).getByLabelText(
        "booking:bookings.form.time",
      );
      const originalTime = start.getAttribute("value");
      const save = screen.getByRole("button", { name: "booking:bookings.form.save" });
      await user.click(save);
      await waitFor(() => expect(committed).toBe(true));

      expect(save).toBeDisabled();
      expect(screen.queryByRole("alert")).not.toBeInTheDocument();
      expect(purpose).toHaveValue("Keep this draft");
      finishRead?.();

      expect(await screen.findByRole("alert")).toHaveTextContent("booking:bookings.errors.outcomeUncertain");
      expect(queryClient.getQueryData(["api-v2", "bookings", 41])).toMatchObject({
        version: 8,
        start: updated.start,
        end: updated.end,
      });
      expect(purpose).toHaveValue("Keep this draft");
      expect(start).toHaveValue(originalTime);
      expect(router.state.location.pathname).toBe("/booking/calendar/bookings/41/edit");
    },
  );

  it("normalises a non-editable direct edit URL without rendering a form", async () => {
    server.use(http.get("/api/v2/bookings/41", () => HttpResponse.json({ ...document, canEdit: false })));
    const { router } = renderEdit();

    await waitFor(() => expect(router.state.location.pathname).toBe("/booking/calendar/bookings/41"));
    expect(screen.queryByRole("button", { name: "booking:bookings.form.save" })).not.toBeInTheDocument();
  });
});
