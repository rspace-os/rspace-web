import "@/__tests__/__mocks__/matchMedia";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import type { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
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
        HttpResponse.json(collectionPage([{ ...configuration, updatedAt }], 2)),
      ),
      http.get("/api/v2/bookings", () => HttpResponse.json(collectionPage([], 10))),
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
        HttpResponse.json(collectionPage([{ ...configuration, timezone }], 2)),
      ),
      http.get("/api/v2/bookings", () => {
        eventRequests += 1;
        return HttpResponse.json(collectionPage([booking], 10));
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
