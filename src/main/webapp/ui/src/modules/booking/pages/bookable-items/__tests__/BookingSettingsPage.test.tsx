import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { Suspense } from "react";
import * as v from "valibot";
import { describe, expect, it } from "vitest";
import { oauthTokenHandler } from "@/__tests__/mocks/oauthTokenMocks";
import { server } from "@/__tests__/mswServer";
import {
  DEFAULT_SCHEDULING_SETTINGS,
  SchedulingSettingsSchema,
  validMaximumBookingDuration,
  validOpeningHours,
} from "@/modules/booking/configuration/schedulingSettings";
import type { OpeningException } from "@/modules/booking/domain/bookingOpeningHours";
import BookingSettingsPage from "../BookingSettingsPage";

const settings = {
  slotGranularityMinutes: 5,
  openingStart: "08:00",
  openingEnd: "18:00",
  openDays: [1, 2, 3, 4, 5, 6, 7],
  openingExceptions: [] as OpeningException[],
  bufferBeforeMinutes: 3,
  bufferAfterMinutes: 7,
  maxBookingDurationMinutes: 0,
  allowDoubleBooking: false,
  availabilityWindowStart: "08:00",
  availabilityWindowEnd: "18:00",
  timezoneMode: "BROWSER" as const,
  customTimezone: null,
  institutionTimezone: "UTC",
  defaultSharedWith: "ALL_USERS" as const,
  selectedAccessGrantees: [],
  configurationVersion: 0,
  state: "ACTIVE",
};

function renderPage(queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })) {
  return render(
    <QueryClientProvider client={queryClient}>
      <Suspense fallback={null}>
        <BookingSettingsPage />
      </Suspense>
    </QueryClientProvider>,
  );
}

describe("BookingSettingsPage", () => {
  it("keeps the draft's version after a background settings refresh", async () => {
    const user = userEvent.setup();
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    let current = settings;
    let submitted: unknown;
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/booking-settings/admin", () => HttpResponse.json(current)),
      http.patch("/api/v2/booking-settings/admin", async ({ request }) => {
        submitted = await request.json();
        return HttpResponse.json({ status: 409, code: "errors.api.v2.bookingConfiguration.stale" }, { status: 409 });
      }),
    );
    renderPage(queryClient);
    const doubleBooking = await screen.findByRole("checkbox", {
      name: "booking:settings.fields.allowDoubleBooking",
    });
    await user.click(doubleBooking);
    current = { ...settings, configurationVersion: 1, openingEnd: "20:00" };
    await act(() => queryClient.refetchQueries({ queryKey: ["api-v2", "booking-settings", "admin"] }));
    await user.click(screen.getByRole("button", { name: "booking:settings.actions.save" }));

    expect(await screen.findByText("booking:settings.errors.stale")).toBeVisible();
    expect(submitted).toMatchObject({ configurationVersion: 0, openingEnd: "18:00", allowDoubleBooking: true });
    expect(doubleBooking).toBeChecked();
  });

  it("reserves 24:00 for the full-day interval", () => {
    expect(validOpeningHours("00:00", "24:00")).toBe(true);
    expect(validOpeningHours("08:00", "24:00")).toBe(false);
  });

  it("defaults to every weekday open with no exceptions", () => {
    expect(DEFAULT_SCHEDULING_SETTINGS).toMatchObject({ openDays: [1, 2, 3, 4, 5, 6, 7], openingExceptions: [] });
    expect(v.safeParse(SchedulingSettingsSchema, DEFAULT_SCHEDULING_SETTINGS).success).toBe(true);
  });

  it.each([
    ["no open days", { openDays: [] }, "openDays"],
    ["duplicate open days", { openDays: [1, 1] }, "openDays"],
    ["an out-of-range open day", { openDays: [8] }, "openDays.0"],
    [
      "duplicate exception days",
      {
        openingExceptions: [
          { dayOfWeek: 2, start: "10:00", end: "16:00" },
          { dayOfWeek: 2, start: "11:00", end: "15:00" },
        ],
      },
      "openingExceptions",
    ],
    [
      "a reversed exception",
      { openingExceptions: [{ dayOfWeek: 2, start: "16:00", end: "10:00" }] },
      "openingExceptions",
    ],
    [
      "24:00 in an exception after a non-midnight start",
      {
        openingExceptions: [{ dayOfWeek: 2, start: "08:00", end: "24:00" }],
      },
      "openingExceptions",
    ],
  ])("rejects scheduling settings with %s", (_, overrides, path) => {
    const result = v.safeParse(SchedulingSettingsSchema, { ...DEFAULT_SCHEDULING_SETTINGS, ...overrides });
    expect(result.success).toBe(false);
    expect(result.issues?.map((issue) => v.getDotPath(issue))).toEqual([path]);
  });

  it("drops an exception on a closed day from the submitted settings", () => {
    const output = v.parse(SchedulingSettingsSchema, {
      ...DEFAULT_SCHEDULING_SETTINGS,
      openDays: [1],
      openingExceptions: [{ dayOfWeek: 2, start: "10:00", end: "16:00" }],
    });
    expect(output.openingExceptions).toEqual([]);
  });

  it("shows full-day closing as 00:00 while preserving the 24:00 API value", async () => {
    const user = userEvent.setup();
    let submitted: Record<string, unknown> | undefined;
    const fullDaySettings = { ...settings, openingStart: "00:00", openingEnd: "24:00" };
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/booking-settings/admin", () => HttpResponse.json(fullDaySettings)),
      http.patch("/api/v2/booking-settings/admin", async ({ request }) => {
        submitted = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(fullDaySettings);
      }),
    );
    renderPage();

    const openingEnd = await screen.findByLabelText("booking:settings.fields.openingEnd");
    expect(openingEnd).toHaveValue("00:00");
    expect(screen.queryByRole("checkbox", { name: "booking:settings.fields.fullDay" })).not.toBeInTheDocument();

    await user.click(screen.getByRole("checkbox", { name: "booking:settings.fields.allowDoubleBooking" }));
    await user.click(screen.getByRole("button", { name: "booking:settings.actions.save" }));

    expect(await screen.findByRole("button", { name: "booking:preferences.actions.saved" })).toBeDisabled();
    expect(submitted).toMatchObject({ openingStart: "00:00", openingEnd: "24:00" });
  });

  it("validates maximum duration against the selected increment", () => {
    expect(validMaximumBookingDuration(0, 5)).toBe(true);
    expect(validMaximumBookingDuration(60, 5)).toBe(true);
    expect(validMaximumBookingDuration(7, 5)).toBe(false);
  });

  it("offers every supported time increment", async () => {
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/booking-settings/admin", () => HttpResponse.json(settings)),
    );
    renderPage();

    const granularity = await screen.findByRole("combobox", {
      name: "booking:settings.fields.granularity",
    });
    expect(
      within(granularity)
        .getAllByRole("option")
        .map((option) => option.getAttribute("value")),
    ).toEqual(["1", "5", "10", "15"]);
  });

  it("does not submit an invalid maximum duration", async () => {
    const user = userEvent.setup();
    let patches = 0;
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/booking-settings/admin", () => HttpResponse.json(settings)),
      http.patch("/api/v2/booking-settings/admin", () => {
        patches += 1;
        return HttpResponse.json(settings);
      }),
    );
    renderPage();

    const maximum = await screen.findByRole("spinbutton", {
      name: "booking:settings.fields.maximumDuration",
    });
    await user.clear(maximum);
    await user.type(maximum, "7");
    expect(maximum).toHaveAccessibleDescription("booking:settings.fields.maximumDurationDescription");
    expect(screen.getByText("booking:settings.errors.maximumDuration")).toBeVisible();
    await user.click(screen.getByRole("button", { name: "booking:settings.actions.save" }));

    expect(patches).toBe(0);
  });

  it("preserves asymmetric buffers during an unrelated edit", async () => {
    const user = userEvent.setup();
    let body: unknown;
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/booking-settings/admin", () => HttpResponse.json(settings)),
      http.patch("/api/v2/booking-settings/admin", async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({ ...settings, allowDoubleBooking: true, configurationVersion: 1 });
      }),
    );
    renderPage();

    const buffer = await screen.findByRole("spinbutton", {
      name: "booking:settings.fields.buffer",
    });
    expect(buffer).toHaveValue(null);
    expect(buffer).not.toBeRequired();
    expect(screen.getByText("booking:settings.fields.bufferMixed")).toBeVisible();
    await user.click(screen.getByRole("checkbox", { name: "booking:settings.fields.allowDoubleBooking" }));
    await user.click(screen.getByRole("button", { name: "booking:settings.actions.save" }));

    const savedButton = await screen.findByRole("button", { name: "booking:preferences.actions.saved" });
    expect(savedButton).toBeDisabled();
    expect(savedButton).toHaveClass("bg-emerald-600");
    expect(screen.queryByText("booking:settings.saved")).not.toBeInTheDocument();
    expect(body).toEqual({
      slotGranularityMinutes: 5,
      openingStart: "08:00",
      openingEnd: "18:00",
      openDays: [1, 2, 3, 4, 5, 6, 7],
      openingExceptions: [],
      bufferBeforeMinutes: 3,
      bufferAfterMinutes: 7,
      maxBookingDurationMinutes: 0,
      allowDoubleBooking: true,
      availabilityWindowStart: "08:00",
      availabilityWindowEnd: "18:00",
      timezoneMode: "BROWSER",
      customTimezone: null,
      configurationVersion: 0,
    });

    await user.click(screen.getByRole("checkbox", { name: "booking:settings.fields.allowDoubleBooking" }));
    expect(screen.getByRole("button", { name: "booking:settings.actions.save" })).toBeEnabled();
    expect(savedButton).not.toHaveClass("bg-emerald-600");
    await user.click(screen.getByRole("checkbox", { name: "booking:settings.fields.allowDoubleBooking" }));
    expect(screen.getByRole("button", { name: "booking:settings.actions.save" })).toBeDisabled();
  });

  it("writes one entered buffer value to both stored directions", async () => {
    const user = userEvent.setup();
    let body: Record<string, unknown> | undefined;
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/booking-settings/admin", () => HttpResponse.json(settings)),
      http.patch("/api/v2/booking-settings/admin", async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ ...settings, ...body, configurationVersion: 1 });
      }),
    );
    renderPage();

    const buffer = await screen.findByRole("spinbutton", {
      name: "booking:settings.fields.buffer",
    });
    await user.type(buffer, "12");
    await user.click(screen.getByRole("button", { name: "booking:settings.actions.save" }));

    expect(await screen.findByRole("button", { name: "booking:preferences.actions.saved" })).toBeDisabled();
    expect(body).toMatchObject({ bufferBeforeMinutes: 12, bufferAfterMinutes: 12 });
    expect(body).toMatchObject({ configurationVersion: 0 });

    await user.click(screen.getByRole("radio", { name: "booking:preferences.timezone.institution" }));
    expect(screen.getByRole("button", { name: "booking:settings.actions.save" })).toBeEnabled();
  });

  it("keeps a stale form open and asks the admin to reload", async () => {
    const user = userEvent.setup();
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/booking-settings/admin", () => HttpResponse.json(settings)),
      http.patch("/api/v2/booking-settings/admin", () =>
        HttpResponse.json(
          {
            status: 409,
            code: "errors.api.v2.bookingConfiguration.stale",
            detail: "stale",
          },
          { status: 409 },
        ),
      ),
    );
    renderPage();

    await user.click(
      await screen.findByRole("checkbox", {
        name: "booking:settings.fields.allowDoubleBooking",
      }),
    );
    await user.click(screen.getByRole("button", { name: "booking:settings.actions.save" }));

    expect(await screen.findByText("booking:settings.errors.stale")).toBeVisible();
    expect(screen.getByRole("button", { name: "booking:settings.actions.save" })).toBeEnabled();
  });

  describe("weekly opening hours", () => {
    function serve(initial: typeof settings = settings) {
      const submitted: Record<string, unknown>[] = [];
      server.use(
        oauthTokenHandler(true),
        http.get("/api/v2/booking-settings/admin", () => HttpResponse.json(initial)),
        http.patch("/api/v2/booking-settings/admin", async ({ request }) => {
          const body = (await request.json()) as Record<string, unknown>;
          submitted.push(body);
          return HttpResponse.json({ ...initial, ...body, configurationVersion: initial.configurationVersion + 1 });
        }),
      );
      return submitted;
    }
    const hoursList = () => screen.getByRole("list", { name: "booking:settings.openingHours.hoursByDay" });
    const dayRow = (day: string) => {
      const row = within(hoursList())
        .getAllByRole("listitem")
        .find((item) => within(item).queryByText(day, { exact: true }));
      if (!row) throw new Error(`No ${day} row`);
      return row;
    };
    const setHours = async (user: ReturnType<typeof userEvent.setup>, day: string, start: string, end: string) => {
      const group = within(dayRow(day)).getByRole("group", { name: day });
      const opens = within(group).getByLabelText("booking:settings.fields.openingStart");
      const closes = within(group).getByLabelText("booking:settings.fields.openingEnd");
      await user.clear(opens);
      await user.type(opens, start);
      await user.clear(closes);
      await user.type(closes, end);
    };
    const save = () => screen.getByRole("button", { name: "booking:settings.actions.save" });

    it("starts with every day selected, one shared pair and the day list closed", async () => {
      serve();
      renderPage();

      const days = await screen.findByRole("group", { name: "booking:settings.openingHours.openOn" });
      const checkboxes = within(days).getAllByRole("checkbox");
      expect(checkboxes.map((checkbox) => checkbox.getAttribute("aria-checked"))).toEqual(Array(7).fill("true"));
      expect(within(days).getByRole("checkbox", { name: "Monday" })).toBeChecked();
      expect(screen.getByLabelText("booking:settings.fields.openingStart")).toHaveValue("08:00");
      expect(screen.getByLabelText("booking:settings.fields.openingEnd")).toHaveValue("18:00");
      expect(screen.getByText("booking:settings.fields.openingEndDescription")).toBeVisible();
      expect(screen.queryByRole("list", { name: "booking:settings.openingHours.hoursByDay" })).not.toBeInTheDocument();
      expect(screen.getByRole("button", { name: "booking:settings.openingHours.setDifferentHours" })).toBeVisible();
    });

    it("edits, confirms, discards and reverts one day's exception", async () => {
      const user = userEvent.setup();
      const submitted = serve();
      renderPage();

      await user.click(await screen.findByRole("button", { name: "booking:settings.openingHours.setDifferentHours" }));
      expect(within(dayRow("Tuesday")).getByText("08:00\u201318:00")).toHaveClass("text-muted-foreground");

      // The pencil prefills the shared hours, and keyboard focus stays on the first icon button.
      const pencil = within(dayRow("Tuesday")).getByRole("button", { name: "booking:settings.openingHours.editDay" });
      await user.click(pencil);
      const tuesdayHours = within(dayRow("Tuesday")).getByRole("group", { name: "Tuesday" });
      expect(within(tuesdayHours).getByLabelText("booking:settings.fields.openingStart")).toHaveValue("08:00");
      expect(within(tuesdayHours).getByLabelText("booking:settings.fields.openingEnd")).toHaveValue("18:00");
      expect(pencil).toHaveFocus();
      expect(pencil).toHaveAccessibleName("booking:settings.openingHours.confirmDay");

      await setHours(user, "Tuesday", "16:00", "10:00");
      expect(
        within(dayRow("Tuesday")).getByRole("button", { name: "booking:settings.openingHours.confirmDay" }),
      ).toBeDisabled();
      expect(within(dayRow("Tuesday")).getByText("booking:settings.errors.openingHours")).toBeVisible();
      expect(screen.getByText("booking:settings.openingHours.errors.pendingDraft")).toBeVisible();

      await setHours(user, "Tuesday", "10:00", "16:00");
      await user.click(
        within(dayRow("Tuesday")).getByRole("button", { name: "booking:settings.openingHours.confirmDay" }),
      );
      expect(within(dayRow("Tuesday")).getByText("10:00\u201316:00")).toHaveClass("font-bold");
      expect(
        within(dayRow("Tuesday")).getByRole("button", { name: "booking:settings.openingHours.useSharedDay" }),
      ).toBeVisible();
      expect(
        within(dayRow("Tuesday")).getByRole("button", { name: "booking:settings.openingHours.editDay" }),
      ).toHaveFocus();
      expect(save()).toBeEnabled();

      // A pending draft blocks saving until it is discarded, which restores the earlier exception.
      await user.click(
        within(dayRow("Tuesday")).getByRole("button", { name: "booking:settings.openingHours.editDay" }),
      );
      await setHours(user, "Tuesday", "11:00", "12:00");
      expect(save()).toBeDisabled();
      await user.click(
        within(dayRow("Tuesday")).getByRole("button", { name: "booking:settings.openingHours.discardDay" }),
      );
      expect(within(dayRow("Tuesday")).getByText("10:00\u201316:00")).toHaveClass("font-bold");
      expect(save()).toBeEnabled();

      await user.click(
        within(dayRow("Wednesday")).getByRole("button", { name: "booking:settings.openingHours.editDay" }),
      );
      await user.click(
        within(dayRow("Wednesday")).getByRole("button", { name: "booking:settings.openingHours.confirmDay" }),
      );
      expect(within(dayRow("Wednesday")).getByText("08:00\u201318:00")).toHaveClass("text-muted-foreground");
      expect(
        within(dayRow("Wednesday")).queryByRole("button", { name: "booking:settings.openingHours.useSharedDay" }),
      ).not.toBeInTheDocument();

      // Changing the shared hours moves every day without an exception.
      await user.clear(screen.getAllByLabelText("booking:settings.fields.openingStart")[0]);
      await user.type(screen.getAllByLabelText("booking:settings.fields.openingStart")[0], "09:00");
      expect(within(dayRow("Wednesday")).getByText("09:00\u201318:00")).toBeVisible();
      expect(within(dayRow("Tuesday")).getByText("10:00\u201316:00")).toBeVisible();

      await user.click(save());
      await screen.findByRole("button", { name: "booking:preferences.actions.saved" });
      expect(submitted.at(-1)).toMatchObject({
        openingStart: "09:00",
        openDays: [1, 2, 3, 4, 5, 6, 7],
        openingExceptions: [{ dayOfWeek: 2, start: "10:00", end: "16:00" }],
      });
    });

    it("reverts one exception and clears all of them with the same hours every day", async () => {
      const user = userEvent.setup();
      const submitted = serve({
        ...settings,
        openingExceptions: [
          { dayOfWeek: 5, start: "09:00", end: "14:00" },
          { dayOfWeek: 6, start: "10:00", end: "16:00" },
        ],
      });
      renderPage();

      // A saved exception opens the day list.
      expect(await screen.findByRole("list", { name: "booking:settings.openingHours.hoursByDay" })).toBeVisible();
      await user.click(
        within(dayRow("Friday")).getByRole("button", { name: "booking:settings.openingHours.useSharedDay" }),
      );
      expect(within(dayRow("Friday")).getByText("08:00\u201318:00")).toHaveClass("text-muted-foreground");
      expect(within(dayRow("Saturday")).getByText("10:00\u201316:00")).toHaveClass("font-bold");

      await user.click(within(dayRow("Monday")).getByRole("button", { name: "booking:settings.openingHours.editDay" }));
      await user.click(screen.getByRole("button", { name: "booking:settings.openingHours.useSameHours" }));
      expect(screen.queryByRole("list", { name: "booking:settings.openingHours.hoursByDay" })).not.toBeInTheDocument();
      expect(save()).toBeEnabled();

      await user.click(save());
      await screen.findByRole("button", { name: "booking:preferences.actions.saved" });
      expect(submitted.at(-1)).toMatchObject({ openingExceptions: [] });
    });

    it("keeps an unchecked day's exception for a re-check but drops it on save", async () => {
      const user = userEvent.setup();
      const submitted = serve({
        ...settings,
        openingExceptions: [{ dayOfWeek: 6, start: "10:00", end: "16:00" }],
      });
      renderPage();

      const days = await screen.findByRole("group", { name: "booking:settings.openingHours.openOn" });
      await user.click(within(days).getByRole("checkbox", { name: "Saturday" }));
      expect(within(hoursList()).queryByText("Saturday", { exact: true })).not.toBeInTheDocument();
      await user.click(within(days).getByRole("checkbox", { name: "Saturday" }));
      expect(within(dayRow("Saturday")).getByText("10:00\u201316:00")).toHaveClass("font-bold");

      await user.click(within(days).getByRole("checkbox", { name: "Saturday" }));
      await user.click(save());
      await screen.findByRole("button", { name: "booking:preferences.actions.saved" });
      expect(submitted.at(-1)).toMatchObject({ openDays: [1, 2, 3, 4, 5, 7], openingExceptions: [] });
    });

    it("blocks saving with no day selected", async () => {
      const user = userEvent.setup();
      const submitted = serve();
      renderPage();

      const days = await screen.findByRole("group", { name: "booking:settings.openingHours.openOn" });
      for (const checkbox of within(days).getAllByRole("checkbox")) await user.click(checkbox);

      expect(screen.getByText("booking:settings.openingHours.errors.noDays")).toBeVisible();
      expect(days).toHaveAccessibleDescription("booking:settings.openingHours.errors.noDays");
      expect(save()).toBeDisabled();
      expect(submitted).toHaveLength(0);
    });
  });
});
