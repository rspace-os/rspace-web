import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { Suspense } from "react";
import { describe, expect, it } from "vitest";
import { oauthTokenHandler } from "@/__tests__/mocks/oauthTokenMocks";
import { server } from "@/__tests__/mswServer";
import BookingSettingsPage from "../BookingSettingsPage";
import { validMaximumBookingDuration, validOpeningHours } from "../schedulingSettings";

const settings = {
  slotGranularityMinutes: 5,
  openingStart: "08:00",
  openingEnd: "18:00",
  bufferBeforeMinutes: 3,
  bufferAfterMinutes: 7,
  maxBookingDurationMinutes: 0,
  allowDoubleBooking: false,
  configurationVersion: 0,
};

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <Suspense fallback={null}>
        <BookingSettingsPage />
      </Suspense>
    </QueryClientProvider>,
  );
}

describe("BookingSettingsPage", () => {
  it("reserves 24:00 for the full-day interval", () => {
    expect(validOpeningHours("00:00", "24:00")).toBe(true);
    expect(validOpeningHours("08:00", "24:00")).toBe(false);
  });

  it("validates maximum duration against the selected increment", () => {
    expect(validMaximumBookingDuration(0, 5)).toBe(true);
    expect(validMaximumBookingDuration(60, 5)).toBe(true);
    expect(validMaximumBookingDuration(7, 5)).toBe(false);
  });

  it("does not submit an invalid maximum duration", async () => {
    const user = userEvent.setup();
    let patches = 0;
    server.use(
      oauthTokenHandler(),
      http.get("/api/v2/booking-settings", () => HttpResponse.json(settings)),
      http.patch("/api/v2/booking-settings", () => {
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
      oauthTokenHandler(),
      http.get("/api/v2/booking-settings", () => HttpResponse.json(settings)),
      http.patch("/api/v2/booking-settings", async ({ request }) => {
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

    expect(await screen.findByRole("status")).toHaveTextContent("booking:settings.saved");
    expect(body).toEqual({
      slotGranularityMinutes: 5,
      openingStart: "08:00",
      openingEnd: "18:00",
      bufferBeforeMinutes: 3,
      bufferAfterMinutes: 7,
      maxBookingDurationMinutes: 0,
      allowDoubleBooking: true,
      configurationVersion: 0,
    });
  });

  it("writes one entered buffer value to both stored directions", async () => {
    const user = userEvent.setup();
    let body: Record<string, unknown> | undefined;
    server.use(
      oauthTokenHandler(),
      http.get("/api/v2/booking-settings", () => HttpResponse.json(settings)),
      http.patch("/api/v2/booking-settings", async ({ request }) => {
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

    expect(await screen.findByRole("status")).toBeVisible();
    expect(body).toMatchObject({ bufferBeforeMinutes: 12, bufferAfterMinutes: 12 });
    expect(body).toMatchObject({ configurationVersion: 0 });
  });

  it("keeps a stale form open and asks the admin to reload", async () => {
    const user = userEvent.setup();
    server.use(
      oauthTokenHandler(),
      http.get("/api/v2/booking-settings", () => HttpResponse.json(settings)),
      http.patch("/api/v2/booking-settings", () =>
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
});
