import "@/__tests__/__mocks__/matchMedia";
import { act, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { server } from "@/__tests__/mswServer";

import { collectionPage } from "../mocks/bookableItemsMocks";
import { configuration, renderPage, setupPage } from "./bookableItemPageTestUtils";

vi.mock("@/modules/common/hooks/auth", () => ({ useOauthTokenQuery: vi.fn() }));
vi.mock("@/modules/common/queries/currentUser", () => ({ useCurrentUserQuery: vi.fn() }));

beforeEach(() => setupPage());

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
    expect(notice).toHaveTextContent("booking:bookableItemDetails.backgroundError");
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
});
