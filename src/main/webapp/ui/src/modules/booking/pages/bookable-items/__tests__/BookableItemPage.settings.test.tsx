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
import { server } from "@/__tests__/mswServer";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { bookerBookingAccess, collectionPage } from "../mocks/bookableItemsMocks";
import { configuration, mockedUseCurrentUserQuery, renderPage, setupPage } from "./bookableItemPageTestUtils";

vi.mock("@/modules/common/hooks/auth", () => ({ useOauthTokenQuery: vi.fn() }));
vi.mock("@/modules/common/queries/currentUser", () => ({ useCurrentUserQuery: vi.fn() }));

beforeEach(() => setupPage());

describe("BookableItemPage", () => {
  it("saves booking rules in place and returns to the read-out", async () => {
    const user = userEvent.setup();
    const patches: unknown[] = [];
    server.use(
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionPage([configuration], 2))),
      http.get("/api/v2/bookings", () => HttpResponse.json(collectionPage([], 10))),
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
    const mondayStart = within(screen.getByRole("group", { name: "Monday" })).getByLabelText(
      "booking:settings.fields.openingStart",
    );
    await user.clear(mondayStart);
    await user.type(mondayStart, "09:00");
    await user.click(screen.getByRole("button", { name: "booking:bookableItems.actions.save" }));

    await waitFor(() => expect(patches).toHaveLength(1));
    expect(patches[0]).toMatchObject({
      maxBookingDurationMinutes: 60,
      openingHours: [{ ...configuration.openingHours[0], start: "09:00" }, configuration.openingHours[1]],
    });
    expect(patches[0]).not.toHaveProperty("timezone");
    const editButton = await screen.findByRole("button", { name: "booking:bookableItemDetails.edit" });
    expect(editButton).toHaveFocus();
  });

  it("cancels an edit without sending a request", async () => {
    const user = userEvent.setup();
    let patched = false;
    server.use(
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionPage([configuration], 2))),
      http.get("/api/v2/bookings", () => HttpResponse.json(collectionPage([], 10))),
      http.patch("/api/v2/booking-configurations/7", () => {
        patched = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderPage();

    await user.click(await screen.findByRole("tab", { name: "booking:bookableItemDetails.tabs.details" }));
    await user.click(await screen.findByRole("button", { name: "booking:bookableItemDetails.edit" }));
    const mondayStart = within(screen.getByRole("group", { name: "Monday" })).getByLabelText(
      "booking:settings.fields.openingStart",
    );
    await user.clear(mondayStart);
    await user.type(mondayStart, "09:00");
    await user.click(screen.getByRole("checkbox", { name: "Tuesday" }));
    await user.click(screen.getByRole("checkbox", { name: "Sunday" }));
    await user.click(await screen.findByRole("button", { name: "booking:bookableItemDetails.cancelEdit" }));

    expect(await screen.findByRole("button", { name: "booking:bookableItemDetails.edit" })).toHaveFocus();
    expect(screen.getByText("booking:bookableItemDetails.unlimited")).toBeVisible();
    expect(patched).toBe(false);
    await user.click(screen.getByRole("button", { name: "booking:bookableItemDetails.edit" }));
    expect(
      within(await screen.findByRole("group", { name: "Monday" })).getByLabelText(
        "booking:settings.fields.openingStart",
      ),
    ).toHaveValue("08:00");
    expect(
      within(screen.getByRole("group", { name: "Tuesday" })).getByLabelText("booking:settings.fields.openingStart"),
    ).toHaveValue("10:00");
    expect(screen.getByRole("checkbox", { name: "Sunday" })).not.toBeChecked();
  });

  it("keeps calendar status lazy and exposes the trigger to an ordinary readable user", async () => {
    let statusRequests = 0;
    let createRequests = 0;
    vi.mocked(useCurrentUserQuery).mockReturnValue({
      data: { hasSysAdminRole: false, session: { operatedAs: false } },
    } as ReturnType<typeof useCurrentUserQuery>);
    server.use(
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionPage([configuration], 2))),
      http.get("/api/v2/bookings", () => HttpResponse.json(collectionPage([], 10))),
      http.get("/api/v2/booking-configurations/7/calendar-subscription", () => {
        statusRequests += 1;
        return HttpResponse.json(
          { active: false, updatedAt: null, subscriptionUrl: null },
          { headers: { ETag: '"inactive"' } },
        );
      }),
      http.post("/api/v2/booking-configurations/7/calendar-subscription", () => {
        createRequests += 1;
        return HttpResponse.json(
          {
            active: true,
            updatedAt: "2026-08-27T12:00:00.000Z",
            subscriptionUrl: `https://rspace.example/public/booking/calendars/feed.ics?token=${"c".repeat(43)}`,
          },
          { headers: { ETag: '"current"' } },
        );
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
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionPage([configuration], 2))),
      http.get("/api/v2/bookings", () => HttpResponse.json(collectionPage([], 10))),
      http.patch("/api/v2/booking-configurations/7", () => HttpResponse.json({}, { status: 500 })),
    );
    renderPage("/booking/bookable-items/IN123/details?edit=true");

    const maximumDuration = await screen.findByLabelText("booking:bookableItemDetails.fields.maximumDuration");
    await user.clear(maximumDuration);
    await user.type(maximumDuration, "60");
    const mondayStart = within(screen.getByRole("group", { name: "Monday" })).getByLabelText(
      "booking:settings.fields.openingStart",
    );
    await user.clear(mondayStart);
    await user.type(mondayStart, "09:00");
    await user.click(screen.getByRole("checkbox", { name: "Tuesday" }));
    const saveButton = screen.getByRole("button", { name: "booking:bookableItems.actions.save" });
    await user.click(saveButton);

    expect(await screen.findByRole("alert")).toHaveTextContent("booking:bookableItems.editError");
    expect(maximumDuration).toHaveValue(60);
    expect(saveButton).toHaveFocus();
    expect(mondayStart).toHaveValue("09:00");
    await user.click(screen.getByRole("checkbox", { name: "Tuesday" }));
    expect(
      within(screen.getByRole("group", { name: "Tuesday" })).getByLabelText("booking:settings.fields.openingStart"),
    ).toHaveValue("10:00");
  });

  it("keeps a caller without edit capability in view mode on the direct edit URL", async () => {
    mockedUseCurrentUserQuery.mockReturnValue({
      data: { hasSysAdminRole: false, session: { operatedAs: false } },
    } as ReturnType<typeof useCurrentUserQuery>);
    server.use(
      http.get("/api/v2/booking-configurations", () =>
        HttpResponse.json(collectionPage([{ ...configuration, ...bookerBookingAccess }], 2)),
      ),
      http.get("/api/v2/bookings", () => HttpResponse.json(collectionPage([], 10))),
    );
    renderPage("/booking/bookable-items/IN123/details?edit=true");

    expect(await screen.findByText("booking:bookableItemDetails.unlimited")).toBeVisible();
    expect(screen.queryByRole("button", { name: "booking:bookableItems.actions.save" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "booking:bookableItemDetails.edit" })).not.toBeInTheDocument();
  });
});
