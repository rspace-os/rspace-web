import { QueryClient } from "@tanstack/react-query";
import { act, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { getGetAdminBookingSettingsQueryKey } from "@/modules/common/api/v2/generated/booking-settings/booking-settings";
import type { ApiV2Problem } from "@/modules/common/api/v2/generated/models/apiV2Problem";
import { bookingSettingsFixture, renderBookingSettingsPage } from "./bookingSettingsTestHarness";

const settings = { ...bookingSettingsFixture, state: "ACTIVE" as const };

describe("BookingSettingsPage", () => {
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
        return HttpResponse.json<ApiV2Problem>(
          { title: "Request failed", status: 412, code: "errors.api.v2.bookingConfiguration.stale" },
          { status: 412 },
        );
      }),
    );
    renderBookingSettingsPage(queryClient);
    const doubleBooking = await screen.findByRole("checkbox", {
      name: "booking:settings.fields.allowDoubleBooking",
    });
    await user.click(doubleBooking);
    current = {
      ...settings,
      configurationVersion: 1,
      openingHours: settings.openingHours.map((entry) => ({ ...entry, end: "20:00" })),
    };
    await act(() => queryClient.refetchQueries({ queryKey: getGetAdminBookingSettingsQueryKey() }));
    await user.click(screen.getByRole("button", { name: "booking:settings.actions.save" }));

    expect(await screen.findByText("booking:settings.errors.stale")).toBeVisible();
    expect(submitted).toMatchObject({
      openingHours: settings.openingHours,
      allowDoubleBooking: true,
    });
    expect(doubleBooking).toBeChecked();
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
    renderBookingSettingsPage();

    const maximum = await screen.findByRole("spinbutton", {
      name: "booking:settings.fields.maximumDuration",
    });
    await user.clear(maximum);
    await user.type(maximum, "7");
    expect(maximum).toHaveAccessibleDescription(
      "booking:settings.fields.maximumDurationDescription booking:settings.errors.maximumDuration",
    );
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
    renderBookingSettingsPage();

    const buffer = await screen.findByRole("spinbutton", {
      name: "booking:settings.fields.bufferBefore",
    });
    expect(buffer).toHaveValue(3);
    expect(buffer).toBeRequired();
    expect(screen.getByRole("spinbutton", { name: "booking:settings.fields.bufferAfter" })).toHaveValue(7);
    await user.click(
      screen.getByRole("checkbox", {
        name: "booking:settings.fields.allowDoubleBooking",
      }),
    );
    await user.click(screen.getByRole("button", { name: "booking:settings.actions.save" }));

    expect(await screen.findByRole("status")).toHaveTextContent("booking:settings.saved");
    expect(body).toEqual({
      slotGranularityMinutes: 5,
      incrementUnit: "MINUTES",
      openingHours: settings.openingHours,
      bufferBeforeMinutes: 3,
      bufferAfterMinutes: 7,
      maxBookingDurationMinutes: 0,
      allowDoubleBooking: true,
      availabilityWindowStart: "08:00",
      availabilityWindowEnd: "18:00",
      timezoneMode: "BROWSER",
      customTimezone: null,
    });
  });

  it("edits the before gap independently of the after gap", async () => {
    const user = userEvent.setup();
    let body: Record<string, unknown> | undefined;
    server.use(
      http.get("/api/v2/booking-settings/admin", () => HttpResponse.json(settings, { headers: { ETag: '"0"' } })),
      http.patch("/api/v2/booking-settings/admin", async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ ...settings, ...body, configurationVersion: 1 }, { headers: { ETag: '"1"' } });
      }),
    );
    renderBookingSettingsPage();

    const buffer = await screen.findByRole("spinbutton", {
      name: "booking:settings.fields.bufferBefore",
    });
    await user.clear(buffer);
    await user.type(buffer, "12");
    await user.click(screen.getByRole("button", { name: "booking:settings.actions.save" }));

    expect(await screen.findByRole("status")).toBeVisible();
    expect(body).toMatchObject({
      bufferBeforeMinutes: 12,
      bufferAfterMinutes: 7,
    });
    expect(body).not.toHaveProperty("configurationVersion");
  });

  it("keeps a stale form open and asks the admin to reload", async () => {
    const user = userEvent.setup();
    server.use(
      http.get("/api/v2/booking-settings/admin", () => HttpResponse.json(settings, { headers: { ETag: '"0"' } })),
      http.patch("/api/v2/booking-settings/admin", () =>
        HttpResponse.json<ApiV2Problem>(
          { title: "Request failed", status: 412, code: "errors.api.v2.bookingConfiguration.stale", detail: "stale" },
          { status: 412 },
        ),
      ),
    );
    renderBookingSettingsPage();

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
