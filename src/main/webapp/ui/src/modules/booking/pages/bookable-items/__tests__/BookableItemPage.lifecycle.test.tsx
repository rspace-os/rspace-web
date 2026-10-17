import "@/__tests__/__mocks__/matchMedia";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { server } from "@/__tests__/mswServer";

import type { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { collectionPage } from "../mocks/bookableItemsMocks";
import { configuration, mockedUseCurrentUserQuery, renderPage, setupPage } from "./bookableItemPageTestUtils";

vi.mock("@/modules/common/hooks/auth", () => ({ useOauthTokenQuery: vi.fn() }));
vi.mock("@/modules/common/queries/currentUser", () => ({ useCurrentUserQuery: vi.fn() }));

beforeEach(() => setupPage());

describe("BookableItemPage", () => {
  it("archives an owned configuration after confirmation", async () => {
    let deleteRequest: Request | undefined;
    server.use(
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionPage([configuration], 2))),
      http.get("/api/v2/bookings", () => HttpResponse.json(collectionPage([], 10))),
      http.delete("/api/v2/booking-configurations/7", ({ request }) => {
        deleteRequest = request;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    const catalogueQueryKey = ["api-v2", "booking-catalogue"] as const;
    const { queryClient, router } = renderPage();
    queryClient.setQueryData(catalogueQueryKey, []);

    await user.click(await screen.findByRole("button", { name: "booking:bookableItems.actions.menu" }));
    await user.click(await screen.findByRole("menuitem", { name: "booking:bookableItems.actions.archive" }));
    const dialog = screen.getByRole("alertdialog", { name: "booking:bookableItemDetails.archiveDialog.title" });
    await expectAccessible(dialog);
    await user.click(within(dialog).getByRole("button", { name: "booking:bookableItemDetails.archiveDialog.confirm" }));

    await waitFor(() => expect(deleteRequest).toBeDefined());
    expect(deleteRequest?.headers.get("Authorization")).toBe("Bearer token");
    expect(deleteRequest?.headers.get("If-Match")).toBe('"0"');
    expect(router.state.location.pathname).toBe("/booking/bookable-items/IN123");
    await waitFor(() => expect(queryClient.getQueryState(catalogueQueryKey)?.isInvalidated).toBe(true));
  });

  it("restores an archived configuration with a state-only conditional PATCH", async () => {
    const archived = { ...configuration, configurationVersion: 2, state: "ARCHIVED" as const };
    let current = archived as typeof archived | typeof configuration;
    let patchRequest: Request | undefined;
    server.use(
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionPage([current], 2))),
      http.get("/api/v2/bookings", () => HttpResponse.json(collectionPage([], 10))),
      http.patch("/api/v2/booking-configurations/7", async ({ request }) => {
        patchRequest = request;
        current = { ...configuration, configurationVersion: 3 };
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    const catalogueQueryKey = ["api-v2", "booking-catalogue"] as const;
    const { queryClient } = renderPage();
    queryClient.setQueryData(catalogueQueryKey, []);

    await user.click(await screen.findByRole("button", { name: "booking:bookableItems.actions.menu" }));
    await user.click(await screen.findByRole("menuitem", { name: "booking:bookableItems.actions.restore" }));

    await waitFor(() => expect(patchRequest).toBeDefined());
    expect(patchRequest?.headers.get("Authorization")).toBe("Bearer token");
    expect(patchRequest?.headers.get("Content-Type")).toBe("application/json");
    expect(patchRequest?.headers.get("If-Match")).toBe('"2"');
    await expect(patchRequest?.json()).resolves.toEqual({ state: "ACTIVE" });
    expect(await screen.findByRole("button", { name: "booking:bookings.actions.newBooking" })).toBeVisible();
    expect(screen.getByRole("button", { name: "booking:bookableItems.actions.menu" })).toHaveFocus();
    await waitFor(() => expect(queryClient.getQueryState(catalogueQueryKey)?.isInvalidated).toBe(true));
  });

  it("invalidates availability after leaving a configuration", async () => {
    const user = userEvent.setup();
    const catalogueQueryKey = ["api-v2", "booking-catalogue"] as const;
    const leaveConfiguration = {
      ...configuration,
      capabilities: { ...configuration.capabilities, canLeaveConfiguration: true },
    };
    let leaveRequests = 0;
    server.use(
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionPage([leaveConfiguration], 2))),
      http.get("/api/v2/bookings", () => HttpResponse.json(collectionPage([], 10))),
      http.delete("/api/v2/booking-configurations/7/access/me", () => {
        leaveRequests += 1;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const { queryClient, router } = renderPage();
    queryClient.setQueryData(catalogueQueryKey, []);

    await user.click(await screen.findByRole("button", { name: "booking:bookableItemDetails.actions.leave" }));
    const dialog = await screen.findByRole("alertdialog", { name: "booking:bookableItemDetails.leaveDialog.title" });
    await user.click(within(dialog).getByRole("button", { name: "booking:bookableItemDetails.actions.leave" }));

    await waitFor(() => expect(leaveRequests).toBe(1));
    await waitFor(() => expect(queryClient.getQueryState(catalogueQueryKey)?.isInvalidated).toBe(true));
    await waitFor(() => expect(router.state.location.pathname).toBe("/booking"));
  });

  it("requires the exact item name before directly deleting an active configuration", async () => {
    const active = { ...configuration, configurationVersion: 2 };
    let deleteRequest: Request | undefined;
    server.use(
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionPage([active], 2))),
      http.get("/api/v2/bookings", () => HttpResponse.json(collectionPage([], 10))),
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
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionPage([archived], 2))),
      http.get("/api/v2/bookings", () => HttpResponse.json(collectionPage([], 10))),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "booking:bookableItems.actions.menu" }));
    expect(await screen.findByRole("menuitem", { name: "booking:bookableItems.actions.restore" })).toBeVisible();
    expect(
      screen.queryByRole("menuitem", { name: "booking:bookableItems.actions.deletePermanently" }),
    ).not.toBeInTheDocument();
  });
});
