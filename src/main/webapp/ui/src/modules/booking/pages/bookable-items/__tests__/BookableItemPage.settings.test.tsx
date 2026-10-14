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
    const { queryClient } = renderPage();
    const catalogueQueryKeys = [
      ["api-v2", "booking-catalogue", "picker"],
      ["api-v2", "bookings", "booking-catalogue", "all-items", "token", 1],
      ["api-v2", "bookings", "booking-catalogue", "availability-counts", 1],
    ] as const;
    for (const key of catalogueQueryKeys) queryClient.setQueryData(key, []);
    const unrelatedBookingKey = ["api-v2", "bookings", "unrelated"] as const;
    queryClient.setQueryData(unrelatedBookingKey, []);

    await user.click(await screen.findByRole("tab", { name: "booking:bookableItemDetails.tabs.details" }));
    await user.click(await screen.findByRole("button", { name: "booking:bookableItemDetails.edit" }));
    const maximumDuration = await screen.findByLabelText("booking:settings.fields.maximumDuration");
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
    await waitFor(() =>
      expect(catalogueQueryKeys.map((key) => queryClient.getQueryState(key)?.isInvalidated)).toEqual([
        true,
        true,
        true,
      ]),
    );
    expect(queryClient.getQueryState(unrelatedBookingKey)?.isInvalidated).toBe(false);
  });

  it("keeps the stored increment unit through cancel, and saves and reloads a Days increment", async () => {
    const user = userEvent.setup();
    let current = {
      ...configuration,
      slotGranularityMinutes: 1440,
      incrementUnit: "HOURS",
      openingHours: [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "00:00", end: "24:00" })),
    };
    const patches: Record<string, unknown>[] = [];
    server.use(
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionPage([current], 2))),
      http.get("/api/v2/bookings", () => HttpResponse.json(collectionPage([], 10))),
      http.patch("/api/v2/booking-configurations/7", async ({ request }) => {
        const body = (await request.json()) as Record<string, unknown>;
        patches.push(body);
        current = { ...current, ...body };
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderPage();
    const unitButton = (unit: "Hours" | "Days") =>
      within(screen.getByRole("group", { name: "booking:settings.fields.incrementUnit" })).getByRole("button", {
        name: `booking:settings.fields.increment${unit}`,
      });
    const edit = async () => {
      await user.click(await screen.findByRole("button", { name: "booking:bookableItemDetails.edit" }));
      return screen.findByLabelText("booking:bookableItemDetails.fields.granularity");
    };

    await user.click(await screen.findByRole("tab", { name: "booking:bookableItemDetails.tabs.details" }));
    expect(await screen.findByText("booking:bookableItemDetails.hours")).toBeVisible();
    expect(screen.getByText("booking:bookableItemDetails.alignmentUnavailable")).toBeVisible();

    // 24 Hours is not shown as 1 Day, and an unsaved unit change does not outlive Cancel.
    let increment = await edit();
    expect(increment).toHaveValue(24);
    expect(unitButton("Hours")).toHaveAttribute("aria-pressed", "true");
    await user.click(unitButton("Days"));
    expect(increment).toHaveValue(24);
    expect(unitButton("Days")).toHaveAttribute("aria-pressed", "true");
    await user.click(
      within(screen.getByRole("tabpanel")).getByRole("button", { name: "booking:bookableItemDetails.cancelEdit" }),
    );
    increment = await edit();
    expect(increment).toHaveValue(24);
    expect(unitButton("Hours")).toHaveAttribute("aria-pressed", "true");

    await user.click(unitButton("Days"));
    await user.clear(increment);
    await user.type(increment, "0.5");
    expect(screen.getByText("booking:settings.errors.incrementWholeDays")).toBeVisible();
    await user.clear(increment);
    await user.type(increment, "2");
    expect(screen.queryByText("booking:settings.errors.incrementWholeDays")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "booking:bookableItems.actions.save" }));
    await waitFor(() => expect(patches).toHaveLength(1));
    expect(patches[0]).toMatchObject({ slotGranularityMinutes: 2880, incrementUnit: "DAYS", alignToIncrement: true });
    expect(await screen.findByText("booking:bookableItemDetails.days")).toBeVisible();

    increment = await edit();
    expect(increment).toHaveValue(2);
    expect(unitButton("Days")).toHaveAttribute("aria-pressed", "true");
  });

  it("starts a new Open all day history for each edit session", async () => {
    const user = userEvent.setup();
    const allDay = {
      ...configuration,
      openingHours: [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "00:00", end: "24:00" })),
    };
    server.use(
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionPage([allDay], 2))),
      http.get("/api/v2/bookings", () => HttpResponse.json(collectionPage([], 10))),
    );
    renderPage();
    const sharedHours = () => screen.getByRole("group", { name: "Monday" });
    const control = (name: string) => within(sharedHours()).getByLabelText(name);
    const openAllDay = () =>
      within(sharedHours()).getByRole("checkbox", { name: "booking:settings.openingHours.openAllDay" });

    await user.click(await screen.findByRole("tab", { name: "booking:bookableItemDetails.tabs.details" }));
    expect(screen.getAllByText("booking:settings.openingHours.openAllDay")).toHaveLength(7);
    await user.click(await screen.findByRole("button", { name: "booking:bookableItemDetails.edit" }));
    await screen.findByLabelText("booking:bookableItemDetails.fields.granularity");
    await user.click(openAllDay());
    expect(control("booking:settings.fields.openingStart")).toHaveValue("09:00");
    await user.clear(control("booking:settings.fields.openingStart"));
    await user.type(control("booking:settings.fields.openingStart"), "10:00");
    await user.click(openAllDay());
    await user.click(
      within(screen.getByRole("tabpanel")).getByRole("button", { name: "booking:bookableItemDetails.cancelEdit" }),
    );

    await user.click(await screen.findByRole("button", { name: "booking:bookableItemDetails.edit" }));
    await screen.findByLabelText("booking:bookableItemDetails.fields.granularity");
    expect(openAllDay()).toBeChecked();
    await user.click(openAllDay());
    expect(control("booking:settings.fields.openingStart")).toHaveValue("09:00");
    expect(control("booking:settings.fields.openingEnd")).toHaveValue("17:00");
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
    await user.click(
      within(screen.getByRole("tabpanel")).getByRole("button", { name: "booking:bookableItemDetails.cancelEdit" }),
    );

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

  describe("weekly opening hours", () => {
    const independent = {
      ...configuration,
      openingHours: [
        { dayOfWeek: 1, start: "08:00", end: "17:30" },
        { dayOfWeek: 2, start: "09:00", end: "16:00" },
        { dayOfWeek: 6, start: "10:00", end: "16:00" },
      ],
    };
    function serve() {
      const patches: Record<string, unknown>[] = [];
      server.use(
        http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionPage([independent], 2))),
        http.get("/api/v2/bookings", () => HttpResponse.json(collectionPage([], 10))),
        http.patch("/api/v2/booking-configurations/7", async ({ request }) => {
          patches.push((await request.json()) as Record<string, unknown>);
          return new HttpResponse(null, { status: 204 });
        }),
      );
      return patches;
    }
    const dayHours = (day: string) => screen.getByRole("group", { name: day });
    const save = () => screen.getByRole("button", { name: "booking:bookableItems.actions.save" });
    async function openEditor(user: ReturnType<typeof userEvent.setup>) {
      await user.click(await screen.findByRole("tab", { name: "booking:bookableItemDetails.tabs.details" }));
      await user.click(await screen.findByRole("button", { name: "booking:bookableItemDetails.edit" }));
      await screen.findByRole("group", { name: "Monday" });
    }

    it("reads every weekday's own hours and omitted days as closed", async () => {
      const user = userEvent.setup();
      serve();
      renderPage();
      await user.click(await screen.findByRole("tab", { name: "booking:bookableItemDetails.tabs.details" }));
      const panel = screen.getByRole("tabpanel");
      expect(within(panel).getByText("10:00 AM\u201304:00 PM")).toBeVisible();
      expect(within(panel).getByText("08:00 AM\u201305:30 PM")).toBeVisible();
      expect(within(panel).getAllByText("booking:bookableItemDetails.openingHours.closed")).toHaveLength(4);
    });

    it("submits the supplied replacement week with closed days omitted", async () => {
      const user = userEvent.setup();
      const patches = serve();
      renderPage();
      await openEditor(user);
      await user.click(screen.getByRole("checkbox", { name: "Saturday" }));
      await user.click(save());
      await waitFor(() => expect(patches).toHaveLength(1));
      expect(patches[0]).toMatchObject({ openingHours: independent.openingHours.slice(0, 2) });
    });

    it.each([412, 503])("retains an unchecked day's hours after a failed save (%s)", async (status) => {
      const user = userEvent.setup();
      serve();
      server.use(http.patch("/api/v2/booking-configurations/7", () => HttpResponse.json({ status }, { status })));
      renderPage();
      await openEditor(user);
      await user.click(screen.getByRole("checkbox", { name: "Saturday" }));
      await user.click(save());
      expect(await screen.findByRole("alert")).toHaveTextContent(
        status === 412 ? "booking:bookableItems.staleEdit" : "booking:bookableItems.editError",
      );
      await user.click(screen.getByRole("checkbox", { name: "Saturday" }));
      expect(within(dayHours("Saturday")).getByLabelText("booking:settings.fields.openingStart")).toHaveValue("10:00");
      expect(within(dayHours("Saturday")).getByLabelText("booking:settings.fields.openingEnd")).toHaveValue("16:00");
    });
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

    const maximumDuration = await screen.findByLabelText("booking:settings.fields.maximumDuration");
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
    expect(
      screen.queryByRole("button", { name: "booking:bookableItemDetails.actions.archive" }),
    ).not.toBeInTheDocument();
  });
});
