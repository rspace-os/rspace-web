import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen, waitFor, within } from "@testing-library/react";
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
import BookingSettingsPage from "../BookingSettingsPage";

const settings = {
  slotGranularityMinutes: 5,
  openingHours: [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "08:00", end: "18:00" })),
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
    current = {
      ...settings,
      configurationVersion: 1,
      openingHours: settings.openingHours.map((entry) => ({ ...entry, end: "20:00" })),
    };
    await act(() => queryClient.refetchQueries({ queryKey: ["api-v2", "booking-settings", "admin"] }));
    await user.click(screen.getByRole("button", { name: "booking:settings.actions.save" }));

    expect(await screen.findByText("booking:settings.errors.stale")).toBeVisible();
    expect(submitted).toMatchObject({
      configurationVersion: 0,
      openingHours: settings.openingHours,
      allowDoubleBooking: true,
    });
    expect(doubleBooking).toBeChecked();
  });

  it("refreshes a clean form and uses the refreshed version on its next save", async () => {
    const user = userEvent.setup();
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    let current = settings;
    let submitted: unknown;
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/booking-settings/admin", () => HttpResponse.json(current)),
      http.patch("/api/v2/booking-settings/admin", async ({ request }) => {
        submitted = await request.json();
        return HttpResponse.json({ ...current, allowDoubleBooking: true, configurationVersion: 2 });
      }),
    );
    renderPage(queryClient);
    await screen.findByRole("group", { name: "Monday" });
    current = {
      ...settings,
      configurationVersion: 1,
      openingHours: settings.openingHours.map((entry) => ({ ...entry, end: "20:00" })),
    };
    await act(() => queryClient.refetchQueries({ queryKey: ["api-v2", "booking-settings", "admin"] }));
    await waitFor(() =>
      expect(
        within(screen.getByRole("group", { name: "Monday" })).getByLabelText("booking:settings.fields.openingEnd"),
      ).toHaveValue("20:00"),
    );
    expect(screen.getByRole("button", { name: "booking:settings.actions.save" })).toBeDisabled();
    await user.click(screen.getByRole("checkbox", { name: "booking:settings.fields.allowDoubleBooking" }));
    await user.click(screen.getByRole("button", { name: "booking:settings.actions.save" }));
    expect(await screen.findByRole("button", { name: "booking:preferences.actions.saved" })).toBeDisabled();
    expect(submitted).toMatchObject({
      configurationVersion: 1,
      openingHours: current.openingHours,
      allowDoubleBooking: true,
    });
  });

  it("explicitly discards a conflicted draft and reloads the latest settings", async () => {
    const user = userEvent.setup();
    let current = settings;
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/booking-settings/admin", () => HttpResponse.json(current)),
      http.patch("/api/v2/booking-settings/admin", () =>
        HttpResponse.json({ status: 409, code: "errors.api.v2.bookingConfiguration.stale" }, { status: 409 }),
      ),
    );
    renderPage();
    await user.click(await screen.findByRole("checkbox", { name: "booking:settings.fields.allowDoubleBooking" }));
    const closes = within(screen.getByRole("group", { name: "Monday" })).getByLabelText(
      "booking:settings.fields.openingEnd",
    );
    await user.clear(closes);
    await user.type(closes, "12:00");
    current = {
      ...settings,
      configurationVersion: 1,
      openingHours: settings.openingHours.map((entry) => ({ ...entry, end: "20:00" })),
    };
    await user.click(screen.getByRole("button", { name: "booking:settings.actions.save" }));
    await screen.findByRole("button", { name: "booking:settings.actions.reload" });
    await user.click(screen.getByRole("button", { name: "booking:settings.actions.reload" }));
    await waitFor(() =>
      expect(
        within(screen.getByRole("group", { name: "Monday" })).getByLabelText("booking:settings.fields.openingEnd"),
      ).toHaveValue("20:00"),
    );
    expect(screen.getByRole("checkbox", { name: "booking:settings.fields.allowDoubleBooking" })).not.toBeChecked();
    expect(screen.queryByText("booking:settings.errors.stale")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "booking:settings.actions.save" })).toBeDisabled();
  });

  it("locks editing and saving while explicitly reloading a conflicted draft", async () => {
    const user = userEvent.setup();
    let release!: () => void;
    const responseReady = new Promise<void>((resolve) => {
      release = resolve;
    });
    let loads = 0;
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/booking-settings/admin", async () => {
        loads += 1;
        if (loads > 1) await responseReady;
        return HttpResponse.json({ ...settings, configurationVersion: loads - 1 });
      }),
      http.patch("/api/v2/booking-settings/admin", () =>
        HttpResponse.json({ status: 409, code: "errors.api.v2.bookingConfiguration.stale" }, { status: 409 }),
      ),
    );
    renderPage();
    const doubleBooking = await screen.findByRole("checkbox", { name: "booking:settings.fields.allowDoubleBooking" });
    await user.click(doubleBooking);
    const save = screen.getByRole("button", { name: "booking:settings.actions.save" });
    await user.click(save);
    await user.click(await screen.findByRole("button", { name: "booking:settings.actions.reload" }));
    expect(doubleBooking).toHaveAttribute("aria-disabled", "true");
    expect(save).toBeDisabled();
    expect(screen.getByRole("radio", { name: "booking:preferences.timeFormat.twentyFourHour" })).toBeDisabled();
    await act(async () => {
      release();
      await responseReady;
    });
    await waitFor(() =>
      expect(screen.getByRole("checkbox", { name: "booking:settings.fields.allowDoubleBooking" })).not.toHaveAttribute(
        "aria-disabled",
        "true",
      ),
    );
    expect(screen.getByRole("checkbox", { name: "booking:settings.fields.allowDoubleBooking" })).not.toBeChecked();
  });

  it("allows closing at 24:00 after any valid start", () => {
    expect(validOpeningHours("00:00", "24:00")).toBe(true);
    expect(validOpeningHours("08:00", "24:00")).toBe(true);
    expect(validOpeningHours("24:00", "24:00")).toBe(false);
  });

  it("defaults to every weekday open for the full day", () => {
    expect(DEFAULT_SCHEDULING_SETTINGS).toMatchObject({
      openingHours: [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "00:00", end: "24:00" })),
    });
    expect(v.safeParse(SchedulingSettingsSchema, DEFAULT_SCHEDULING_SETTINGS).success).toBe(true);
  });

  it.each([
    ["no open days", [], "openingHours"],
    ["duplicate weekdays", [1, 1].map((dayOfWeek) => ({ dayOfWeek, start: "09:00", end: "17:00" })), "openingHours"],
    ["an out-of-range weekday", [{ dayOfWeek: 8, start: "09:00", end: "17:00" }], "openingHours.0.dayOfWeek"],
    ["a reversed interval", [{ dayOfWeek: 2, start: "16:00", end: "10:00" }], "openingHours.0.end"],
  ])("rejects scheduling settings with %s", (_, openingHours, path) => {
    const result = v.safeParse(SchedulingSettingsSchema, { ...DEFAULT_SCHEDULING_SETTINGS, openingHours });
    expect(result.success).toBe(false);
    expect(result.issues?.map((issue) => v.getDotPath(issue))).toEqual([path]);
  });

  it("accepts 24:00 after a non-midnight start", () => {
    const result = v.safeParse(SchedulingSettingsSchema, {
      ...DEFAULT_SCHEDULING_SETTINGS,
      openingHours: [{ dayOfWeek: 2, start: "08:00", end: "24:00" }],
    });
    expect(result.success).toBe(true);
  });

  it("submits only the supplied open weekdays", () => {
    const output = v.parse(SchedulingSettingsSchema, {
      ...DEFAULT_SCHEDULING_SETTINGS,
      openingHours: [{ dayOfWeek: 1, start: "00:00", end: "24:00" }],
    });
    expect(output.openingHours).toEqual([{ dayOfWeek: 1, start: "00:00", end: "24:00" }]);
  });

  it("shows full-day closing as 00:00 while preserving the 24:00 API value", async () => {
    const user = userEvent.setup();
    let submitted: Record<string, unknown> | undefined;
    const fullDaySettings = {
      ...settings,
      openingHours: settings.openingHours.map((entry) => ({ ...entry, start: "00:00", end: "24:00" })),
    };
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/booking-settings/admin", () => HttpResponse.json(fullDaySettings)),
      http.patch("/api/v2/booking-settings/admin", async ({ request }) => {
        submitted = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(fullDaySettings);
      }),
    );
    renderPage();

    const openingEnd = within(await screen.findByRole("group", { name: "Monday" })).getByLabelText(
      "booking:settings.fields.openingEnd",
    );
    expect(openingEnd).toHaveValue("00:00");
    expect(
      within(screen.getByRole("group", { name: "Monday" })).getByRole("checkbox", {
        name: "booking:settings.openingHours.openAllDay",
      }),
    ).toBeChecked();

    await user.click(screen.getByRole("checkbox", { name: "booking:settings.fields.allowDoubleBooking" }));
    await user.click(screen.getByRole("button", { name: "booking:settings.actions.save" }));

    expect(await screen.findByRole("button", { name: "booking:preferences.actions.saved" })).toBeDisabled();
    expect(submitted).toMatchObject({
      openingHours: [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "00:00", end: "24:00" })),
    });
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
    expect(screen.getByRole("status")).toHaveTextContent("booking:preferences.actions.saved");
    expect(screen.queryByText("booking:settings.saved")).not.toBeInTheDocument();
    expect(body).toEqual({
      slotGranularityMinutes: 5,
      openingHours: [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "08:00", end: "18:00" })),
      bufferBeforeMinutes: 3,
      bufferAfterMinutes: 7,
      maxBookingDurationMinutes: 0,
      allowDoubleBooking: true,
      availabilityWindowStart: "08:00",
      availabilityWindowEnd: "18:00",
      timezoneMode: "BROWSER",
      customTimezone: null,
      timeFormat: "AUTOMATIC",
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

  it("saves the institution default Time format with the display defaults", async () => {
    const user = userEvent.setup();
    let body: Record<string, unknown> | undefined;
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/booking-settings/admin", () => HttpResponse.json({ ...settings, timeFormat: "H12" })),
      http.patch("/api/v2/booking-settings/admin", async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ ...settings, ...body, configurationVersion: 1 });
      }),
    );
    renderPage();

    expect(await screen.findByRole("radio", { name: "booking:preferences.timeFormat.twelveHour" })).toBeChecked();
    expect(screen.getByRole("button", { name: "booking:settings.actions.save" })).toBeDisabled();
    await user.click(screen.getByRole("radio", { name: "booking:preferences.timeFormat.twentyFourHour" }));
    await user.click(screen.getByRole("button", { name: "booking:settings.actions.save" }));

    expect(await screen.findByRole("button", { name: "booking:preferences.actions.saved" })).toBeDisabled();
    expect(body).toMatchObject({ timeFormat: "H24", timezoneMode: "BROWSER", configurationVersion: 0 });
    expect(screen.getByRole("radio", { name: "booking:preferences.timeFormat.twentyFourHour" })).toBeChecked();
  });

  it.each([409, 503])("keeps the weekday draft available after a failed save (%s)", async (status) => {
    const user = userEvent.setup();
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/booking-settings/admin", () => HttpResponse.json(settings)),
      http.patch("/api/v2/booking-settings/admin", () =>
        HttpResponse.json(
          {
            status,
            ...(status === 409 ? { code: "errors.api.v2.bookingConfiguration.stale" } : {}),
          },
          { status },
        ),
      ),
    );
    renderPage();

    await user.click(
      await screen.findByRole("checkbox", {
        name: "booking:settings.fields.allowDoubleBooking",
      }),
    );
    const closes = within(screen.getByRole("group", { name: "Monday" })).getByLabelText(
      "booking:settings.fields.openingEnd",
    );
    await user.clear(closes);
    await user.type(closes, "12:00");
    await user.click(screen.getByRole("button", { name: "booking:settings.actions.save" }));

    expect(
      await screen.findByText(status === 409 ? "booking:settings.errors.stale" : "booking:settings.errors.save"),
    ).toBeVisible();
    expect(
      within(screen.getByRole("group", { name: "Monday" })).getByLabelText("booking:settings.fields.openingEnd"),
    ).toHaveValue("12:00");
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
    const dayHours = (day: string) => screen.getByRole("group", { name: day });
    const save = () => screen.getByRole("button", { name: "booking:settings.actions.save" });

    it("shows seven independent rows and saves one-day edits immediately", async () => {
      const user = userEvent.setup();
      const submitted = serve();
      renderPage();
      await screen.findByRole("group", { name: "Monday" });
      const list = screen.getByRole("list", { name: "booking:bookableItemDetails.fields.openingHours" });
      expect(within(list).getAllByRole("listitem")).toHaveLength(7);
      const mondayEnd = within(dayHours("Monday")).getByLabelText("booking:settings.fields.openingEnd");
      await user.clear(mondayEnd);
      await user.type(mondayEnd, "16:00");
      expect(within(dayHours("Tuesday")).getByLabelText("booking:settings.fields.openingEnd")).toHaveValue("18:00");
      await user.click(save());
      await screen.findByRole("button", { name: "booking:preferences.actions.saved" });
      expect(submitted[0]).toMatchObject({
        openingHours: settings.openingHours.map((entry) =>
          entry.dayOfWeek === 1 ? { ...entry, end: "16:00" } : entry,
        ),
      });
    });

    it("retains a closed day's unsaved hours and starts a newly enabled day all day", async () => {
      const user = userEvent.setup();
      serve({ ...settings, openingHours: settings.openingHours.filter((entry) => entry.dayOfWeek !== 7) });
      renderPage();
      await screen.findByRole("group", { name: "Monday" });
      const start = within(dayHours("Tuesday")).getByLabelText("booking:settings.fields.openingStart");
      await user.clear(start);
      await user.type(start, "10:00");
      await user.click(screen.getByRole("checkbox", { name: "Tuesday" }));
      expect(screen.queryByRole("group", { name: "Tuesday" })).not.toBeInTheDocument();
      await user.click(screen.getByRole("checkbox", { name: "Tuesday" }));
      expect(within(dayHours("Tuesday")).getByLabelText("booking:settings.fields.openingStart")).toHaveValue("10:00");
      await user.click(screen.getByRole("checkbox", { name: "Sunday" }));
      expect(
        within(dayHours("Sunday")).getByRole("checkbox", { name: "booking:settings.openingHours.openAllDay" }),
      ).toBeChecked();
    });

    it("submits closed days by omission and rejects an empty week", async () => {
      const user = userEvent.setup();
      const submitted = serve();
      renderPage();
      await screen.findByRole("group", { name: "Monday" });
      for (const day of ["Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]) {
        await user.click(screen.getByRole("checkbox", { name: day }));
      }
      await user.click(save());
      await screen.findByRole("button", { name: "booking:preferences.actions.saved" });
      expect(submitted[0]).toMatchObject({ openingHours: [settings.openingHours[0]] });
      await user.click(screen.getByRole("checkbox", { name: "Monday" }));
      expect(screen.getByText("booking:settings.openingHours.errors.noDays")).toBeVisible();
      expect(screen.getByRole("checkbox", { name: "Monday" })).toHaveAccessibleDescription(
        "booking:settings.openingHours.errors.noDays",
      );
      await user.click(save());
      expect(submitted).toHaveLength(1);
    });
  });
});
