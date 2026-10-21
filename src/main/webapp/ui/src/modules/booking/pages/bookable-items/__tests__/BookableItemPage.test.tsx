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
import { screen, waitFor, within } from "@testing-library/react";
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
  it("renders identity, rules, role-sensitive actions, and event requests with one cutoff", async () => {
    const user = userEvent.setup();
    const eventFilters: string[] = [];
    server.use(
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionPage([configuration], 2))),
      http.get("/api/v2/bookings", ({ request }) => {
        eventFilters.push(new URL(request.url).searchParams.get("where") ?? "");
        return HttpResponse.json(collectionPage([], 10));
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
    await waitFor(() => expect(eventFilters).toHaveLength(4));

    await user.click(screen.getByRole("tab", { name: "booking:bookableItemDetails.tabs.details" }));
    expect(screen.getByText("08:00 AM–05:30 PM")).toBeVisible();
    expect(screen.getByText("10:00 AM–04:00 PM")).toBeVisible();
    expect(screen.getAllByText("booking:bookableItemDetails.openingHours.closed")).toHaveLength(5);
    expect(screen.getByText("booking:bookableItemDetails.unlimited")).toBeVisible();
    expect(screen.getByRole("tab", { name: "booking:bookableItemDetails.tabs.details" })).toHaveAttribute(
      "aria-selected",
      "true",
    );
    expect(screen.getByRole("tab", { name: "booking:bookableItemDetails.tabs.audit" })).toBeVisible();
    expect(screen.getByRole("button", { name: "booking:bookableItemDetails.edit" })).toBeVisible();
    const boundaries = eventFilters.map((where) => where.match(/end=(?:gt|le)=([^;]+)/)?.[1]);
    expect(boundaries[0]).toBeTruthy();
    expect(new Set(boundaries).size).toBe(1);
    await expectAccessible(container);
  });

  it("shows the inventory item's access as read-only", async () => {
    let bookingAccessRequests = 0;
    server.use(
      http.get("/api/v2/booking-configurations", () =>
        HttpResponse.json(
          collectionPage(
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
});
