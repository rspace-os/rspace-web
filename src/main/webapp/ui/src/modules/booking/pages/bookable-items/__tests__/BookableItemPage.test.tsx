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
  it("renders identity, rules, and role-sensitive actions", async () => {
    const user = userEvent.setup();
    server.use(http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionPage([configuration], 2))));
    const { container } = renderPage();

    expect(await screen.findByRole("heading", { level: 1, name: "Confocal microscope" })).toBeVisible();
    expect(screen.getByText("IN123")).toBeVisible();
    const facts = screen.getByRole("complementary", { name: "booking:bookableItemDetails.about" });
    expect(within(facts).getByRole("link", { name: "Imaging lab" })).toHaveAttribute("href", "/globalId/IC456");
    expect(within(facts).getByText("Grace Hopper (grace)")).toBeVisible();
    expect(within(facts).getByText("booking:bookableItemDetails.fields.createdAt")).toBeVisible();
    expect(screen.getByRole("button", { name: "booking:bookableItems.actions.menu" })).toBeVisible();
    expect(screen.getAllByText("UTC").length).toBeGreaterThan(0);
    expect(screen.getByRole("tab", { name: "booking:bookableItemDetails.tabs.details" })).toHaveAttribute(
      "aria-selected",
      "true",
    );

    await user.click(screen.getByRole("tab", { name: "booking:bookableItemDetails.tabs.details" }));
    expect(screen.getByText("08:00–17:30")).toBeVisible();
    expect(screen.getByText("10:00–16:00")).toBeVisible();
    expect(screen.getAllByText("booking:bookableItemDetails.openingHours.closed")).toHaveLength(5);
    expect(screen.getByText("booking:bookableItemDetails.unlimited")).toBeVisible();
    expect(screen.getByText("booking:bookableItemDetails.notAvailable")).toBeVisible();
    expect(screen.getByRole("tab", { name: "booking:bookableItemDetails.tabs.details" })).toHaveAttribute(
      "aria-selected",
      "true",
    );
    expect(screen.getByRole("tab", { name: "booking:bookableItemDetails.tabs.audit" })).toBeVisible();
    expect(screen.getByRole("button", { name: "booking:bookableItemDetails.edit" })).toBeVisible();
    await expectAccessible(container);
  });

  it("confirms a tab change before discarding staged permissions", async () => {
    const assignment = (id: number, role: string) => ({
      grantee: { kind: "USER", id, key: `user:${id}`, name: `Person ${id}`, available: true },
      role,
    });
    server.use(
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionPage([configuration], 2))),
      http.get("/api/v2/booking-configurations/7/access", () =>
        HttpResponse.json({
          scheme: "booking",
          version: 0,
          assignments: [assignment(1, "OWNER"), assignment(2, "BOOKER")],
          caller: {
            granteeKey: "user:1",
            capabilities: { canManageAssignments: true, canManageOwners: true, canLeave: false },
          },
        }),
      ),
    );
    const { router } = renderPage("/booking/bookable-items/IN123/access");
    const user = userEvent.setup();
    const table = await screen.findByRole("table");
    await user.click(within(table).getByRole("button", { name: "common:resourceAccess.removeNamed" }));
    const details = screen.getByRole("tab", { name: "booking:bookableItemDetails.tabs.details" });
    await user.click(details);
    const dialog = await screen.findByRole("alertdialog");
    expect(router.state.location.pathname).toBe("/booking/bookable-items/IN123/access");
    await user.click(within(dialog).getByRole("button", { name: "common:actions.cancel" }));
    expect(within(table).getByText("common:resourceAccess.staged.removed")).toBeInTheDocument();
    await user.click(details);
    await user.click(
      within(await screen.findByRole("alertdialog")).getByRole("button", { name: "common:dirtyNavigation.leave" }),
    );
    await waitFor(() => expect(router.state.location.pathname).toBe("/booking/bookable-items/IN123/details"));
  });
});
