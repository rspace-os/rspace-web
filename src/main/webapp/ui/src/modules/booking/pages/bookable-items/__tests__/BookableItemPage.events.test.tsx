import { beforeAll as beforeBookingCatalog } from "vitest";
import appI18n from "@/modules/common/i18n";

beforeBookingCatalog(async () => {
  appI18n.addResourceBundle(
    "en-US",
    "booking",
    (await import("@/modules/common/i18n/locales/en-US/booking.json")).default,
  );
});

import "@/__tests__/__mocks__/matchMedia";
import { QueryClient } from "@tanstack/react-query";
import { act, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import { bookingDisplayPreferencesQueryKey } from "@/modules/booking/domain/bookingDisplayPreferences";
import type { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { inheritedBrowserBookingPreferences } from "../../preferences/bookingPreferencesFixtures";
import { collectionPage } from "../mocks/bookableItemsMocks";
import { configuration, mockedUseCurrentUserQuery, renderPage, setupPage } from "./bookableItemPageTestUtils";

vi.mock("@/modules/common/hooks/auth", () => ({ useOauthTokenQuery: vi.fn() }));
vi.mock("@/modules/common/queries/currentUser", () => ({ useCurrentUserQuery: vi.fn() }));

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

beforeEach(() => setupPage());

describe("BookableItemPage", () => {
  it("loads the audit trail only once its tab is opened", async () => {
    const user = userEvent.setup();
    let auditRequests = 0;
    server.use(
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionPage([configuration], 2))),
      http.get("/api/v2/bookings", () => HttpResponse.json(collectionPage([], 10))),
      http.get("/api/v2/booking-configurations/7/audit", () => {
        auditRequests += 1;
        return HttpResponse.json({
          ...collectionPage(
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
    expect(within(item).getByText("common:values.userWithUsername")).toBeVisible();
    expect(within(item).getByText("booking:bookableItemDetails.audit.actions.changed")).toBeVisible();
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
        lookupFails ? HttpResponse.json(collectionPage([], 2)) : HttpResponse.json(collectionPage([configuration], 2)),
      ),
      http.get("/api/v2/bookings", () => {
        eventRequests += 1;
        return HttpResponse.json(collectionPage([], 10));
      }),
    );
    renderPage();

    expect(await screen.findByText("booking:bookableItemDetails.notFound.title")).toBeVisible();
    expect(eventRequests).toBe(0);
    lookupFails = false;
    await user.click(screen.getByRole("button", { name: "common:actions.retry" }));

    expect(await screen.findByText("Confocal microscope")).toBeVisible();
    expect(screen.queryByRole("link", { name: "booking:bookableItemDetails.edit" })).not.toBeInTheDocument();
    await waitFor(() => expect(eventRequests).toBe(4));
  });

  it.each([
    ["an unreadable or unconfigured item", () => HttpResponse.json(collectionPage([], 2))],
    ["a forbidden lookup", () => new HttpResponse(null, { status: 403 })],
    ["a missing lookup", () => new HttpResponse(null, { status: 404 })],
  ])("shows the not-found state at once, without retrying, for %s", async (_label, respond) => {
    let lookups = 0;
    server.use(
      http.get("/api/v2/booking-configurations", () => {
        lookups += 1;
        return respond();
      }),
    );
    // The application's QueryClient defaults, which retry failed queries three times with backoff.
    renderPage("/booking/bookable-items/IN999", new QueryClient());

    expect(await screen.findByText("booking:bookableItemDetails.notFound.title", {}, { timeout: 500 })).toBeVisible();
    expect(screen.getByText("booking:bookableItemDetails.notFound.description")).toBeVisible();
    expect(screen.getByRole("main")).not.toHaveAttribute("aria-busy");
    await new Promise((resolve) => setTimeout(resolve, 1200));
    expect(lookups).toBe(1);
  });

  it("retries a server error before giving up on the item", async () => {
    let lookups = 0;
    server.use(
      http.get("/api/v2/booking-configurations", () => {
        lookups += 1;
        return lookups === 1
          ? new HttpResponse(null, { status: 503 })
          : HttpResponse.json(collectionPage([configuration], 2));
      }),
      http.get("/api/v2/bookings", () => HttpResponse.json(collectionPage([], 10))),
    );
    renderPage(undefined, new QueryClient());

    expect(await screen.findByRole("heading", { name: "Confocal microscope" }, { timeout: 4000 })).toBeVisible();
    expect(lookups).toBe(2);
    expect(screen.queryByText("booking:bookableItemDetails.notFound.title")).not.toBeInTheDocument();
  });

  it("reformats existing events after a display timezone refresh without refetching events", async () => {
    let eventRequests = 0;
    server.use(
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionPage([configuration], 2))),
      http.get("/api/v2/bookings", () => {
        eventRequests += 1;
        return HttpResponse.json(collectionPage([booking], 10));
      }),
    );
    const { queryClient } = renderPage();
    act(() => {
      queryClient.setQueryData(bookingDisplayPreferencesQueryKey, {
        ...inheritedBrowserBookingPreferences,
        timezoneMode: "CUSTOM",
        customTimezone: "UTC",
        overridden: true,
      });
    });

    const times = await screen.findAllByRole("time");
    const utcText = times[0].textContent;
    expect(eventRequests).toBe(4);
    act(() => {
      queryClient.setQueryData(bookingDisplayPreferencesQueryKey, {
        ...inheritedBrowserBookingPreferences,
        timezoneMode: "CUSTOM",
        customTimezone: "Europe/Berlin",
        overridden: true,
      });
    });

    await waitFor(() => expect(screen.getAllByRole("time")[0]).not.toHaveTextContent(utcText ?? ""));
    expect(eventRequests).toBe(4);
  });
});
