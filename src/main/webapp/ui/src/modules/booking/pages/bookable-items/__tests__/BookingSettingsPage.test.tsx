import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { Suspense } from "react";
import { describe, expect, it } from "vitest";
import { oauthTokenHandler } from "@/__tests__/mocks/oauthTokenMocks";
import { server } from "@/__tests__/mswServer";
import { validMaximumBookingDuration } from "@/modules/booking/configuration/schedulingSettings";
import BookingSettingsPage from "../BookingSettingsPage";
import { bookingSettingsFixture } from "./bookingSettingsFixture";

const settings = {
  ...bookingSettingsFixture,
  bufferBeforeMinutes: 3,
  bufferAfterMinutes: 7,
  state: "ACTIVE" as const,
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
  it.each([
    ["H12", "twelveHour", "H24", "twentyFourHour"],
    ["H24", "twentyFourHour", "H12", "twelveHour"],
  ] as const)(
    "keeps persisted %s, the saved choice, and Automatic distinct",
    async (initial, initialLabel, next, nextLabel) => {
      const user = userEvent.setup();
      const bodies: unknown[] = [];
      let current = { ...settings, timeFormat: initial };
      server.use(
        oauthTokenHandler(true),
        http.get("/api/v2/booking-settings/admin", () => HttpResponse.json(current, { headers: { ETag: `"${current.configurationVersion}"` } })),
        http.patch("/api/v2/booking-settings/admin", async ({ request }) => {
          const body = (await request.json()) as Record<string, unknown>;
          expect(request.headers.get("If-Match")).toBe(`"${current.configurationVersion}"`);
          expect(body).not.toHaveProperty("configurationVersion");
          bodies.push(body);
          current = { ...current, ...body, configurationVersion: current.configurationVersion + 1 };
          return HttpResponse.json(current, { headers: { ETag: `"${current.configurationVersion}"` } });
        }),
      );
      renderPage();

      expect(
        await screen.findByRole("radio", { name: `booking:preferences.timeFormat.${initialLabel}` }),
      ).toBeChecked();
      expect(screen.getByRole("button", { name: "booking:settings.actions.save" })).toBeDisabled();
      await user.click(screen.getByRole("radio", { name: `booking:preferences.timeFormat.${nextLabel}` }));
      await user.click(screen.getByRole("button", { name: "booking:settings.actions.save" }));

      expect(await screen.findByRole("button", { name: "booking:preferences.actions.saved" })).toBeDisabled();
      expect(screen.getAllByText("booking:preferences.actions.saved")).toHaveLength(1);
      expect(bodies[0]).toMatchObject({ timeFormat: next });
      expect(screen.getByRole("radio", { name: `booking:preferences.timeFormat.${nextLabel}` })).toBeChecked();

      await user.click(screen.getByRole("radio", { name: "booking:preferences.timeFormat.automatic" }));
      expect(screen.getByRole("radio", { name: "booking:preferences.timeFormat.automatic" })).toBeChecked();
      expect(screen.queryByText("booking:preferences.actions.saved")).not.toBeInTheDocument();
      expect(screen.getByRole("button", { name: "booking:settings.actions.save" })).toBeEnabled();
      await user.click(screen.getByRole("button", { name: "booking:settings.actions.save" }));

      expect(await screen.findByRole("button", { name: "booking:preferences.actions.saved" })).toBeDisabled();
      expect(screen.getAllByText("booking:preferences.actions.saved")).toHaveLength(1);
      expect(bodies[1]).toMatchObject({ timeFormat: "AUTOMATIC" });
      expect(screen.getByRole("radio", { name: "booking:preferences.timeFormat.automatic" })).toBeChecked();
    },
  );

  it("keeps the draft's ETag after a background settings refresh", async () => {
    const user = userEvent.setup();
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    let current = settings;
    let submitted: unknown;
    server.use(
      http.get("/api/v2/booking-settings/admin", () =>
        HttpResponse.json(current, { headers: { ETag: `"${current.configurationVersion}"` } }),
      ),
      http.patch("/api/v2/booking-settings/admin", async ({ request }) => {
        expect(request.headers.get("If-Match")).toBe('"0"');
        submitted = await request.json();
        return HttpResponse.json({ status: 412, code: "errors.api.v2.bookingConfiguration.stale" }, { status: 412 });
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
      openingHours: settings.openingHours,
      allowDoubleBooking: true,
    });
    expect(doubleBooking).toBeChecked();
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
      http.get("/api/v2/booking-settings/admin", () =>
        HttpResponse.json(fullDaySettings, { headers: { ETag: '"0"' } }),
      ),
      http.patch("/api/v2/booking-settings/admin", async ({ request }) => {
        expect(request.headers.get("If-Match")).toBe('"0"');
        submitted = (await request.json()) as Record<string, unknown>;
        expect(submitted).not.toHaveProperty("configurationVersion");
        return HttpResponse.json(fullDaySettings, { headers: { ETag: '"0"' } });
      }),
    );
    renderPage();

    const monday = await screen.findByRole("group", { name: "Monday" });
    const openingEnd = within(monday).getByLabelText("booking:settings.fields.openingEnd");
    expect(openingEnd).toHaveValue("00:00");

    await user.click(screen.getByRole("checkbox", { name: "booking:settings.fields.allowDoubleBooking" }));
    await user.click(screen.getByRole("button", { name: "booking:settings.actions.save" }));

    expect(await screen.findByRole("button", { name: "booking:preferences.actions.saved" })).toBeDisabled();
    expect(submitted).toMatchObject({ openingHours: fullDaySettings.openingHours });
  });

  it("validates maximum duration against the selected increment", () => {
    expect(validMaximumBookingDuration(0, 5)).toBe(true);
    expect(validMaximumBookingDuration(60, 5)).toBe(true);
    expect(validMaximumBookingDuration(7, 5)).toBe(false);
  });

  it("offers every supported time increment", async () => {
    server.use(
      http.get("/api/v2/booking-settings/admin", () => HttpResponse.json(settings, { headers: { ETag: '"0"' } })),
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
      http.get("/api/v2/booking-settings/admin", () => HttpResponse.json(settings, { headers: { ETag: '"0"' } })),
      http.patch("/api/v2/booking-settings/admin", () => {
        patches += 1;
        return HttpResponse.json(settings, { headers: { ETag: '"0"' } });
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
      http.get("/api/v2/booking-settings/admin", () => HttpResponse.json(settings, { headers: { ETag: '"0"' } })),
      http.patch("/api/v2/booking-settings/admin", async ({ request }) => {
        if (!request.headers.has("If-Match")) return new HttpResponse(null, { status: 428 });
        expect(request.headers.get("If-Match")).toBe('"0"');
        body = await request.json();
        return HttpResponse.json(
          { ...settings, allowDoubleBooking: true, configurationVersion: 1 },
          { headers: { ETag: '"1"' } },
        );
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
      openingHours: settings.openingHours,
      bufferBeforeMinutes: 3,
      bufferAfterMinutes: 7,
      maxBookingDurationMinutes: 0,
      allowDoubleBooking: true,
      availabilityWindowStart: "08:00",
      availabilityWindowEnd: "18:00",
      timezoneMode: "BROWSER",
      customTimezone: null,
      timeFormat: "AUTOMATIC",
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
      http.get("/api/v2/booking-settings/admin", () => HttpResponse.json(settings, { headers: { ETag: '"0"' } })),
      http.patch("/api/v2/booking-settings/admin", async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ ...settings, ...body, configurationVersion: 1 }, { headers: { ETag: '"1"' } });
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
    expect(body).not.toHaveProperty("configurationVersion");

    await user.click(screen.getByRole("radio", { name: "booking:preferences.timezone.institution" }));
    expect(screen.getByRole("button", { name: "booking:settings.actions.save" })).toBeEnabled();
  });

  it("keeps a stale form open and asks the admin to reload", async () => {
    const user = userEvent.setup();
    server.use(
      http.get("/api/v2/booking-settings/admin", () => HttpResponse.json(settings, { headers: { ETag: '"0"' } })),
      http.patch("/api/v2/booking-settings/admin", () =>
        HttpResponse.json(
          {
            status: 412,
            code: "errors.api.v2.bookingConfiguration.stale",
            detail: "stale",
          },
          { status: 412 },
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
});
