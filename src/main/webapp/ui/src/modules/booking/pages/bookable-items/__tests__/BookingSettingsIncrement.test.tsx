import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { Suspense } from "react";
import { describe, expect, it } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { oauthTokenHandler } from "@/__tests__/mocks/oauthTokenMocks";
import { server } from "@/__tests__/mswServer";
import BookingSettingsPage from "../BookingSettingsPage";

const settings = {
  slotGranularityMinutes: 15,
  incrementUnit: "MINUTES",
  alignToIncrement: true,
  openingHours: [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "00:00", end: "24:00" })),

  bufferBeforeMinutes: 0,
  bufferAfterMinutes: 0,
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
};

function serve(initial: Partial<typeof settings> = {}) {
  const submitted: Record<string, unknown>[] = [];
  let current = { ...settings, ...initial };
  server.use(
    oauthTokenHandler(true),
    http.get("/api/v2/booking-settings/admin", () => HttpResponse.json(current)),
    http.patch("/api/v2/booking-settings/admin", async ({ request }) => {
      const body = (await request.json()) as Record<string, unknown>;
      submitted.push(body);
      current = { ...current, ...body, configurationVersion: current.configurationVersion + 1 };
      return HttpResponse.json(current);
    }),
  );
  return submitted;
}

function renderPage() {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <Suspense fallback={null}>
        <BookingSettingsPage />
      </Suspense>
    </QueryClientProvider>,
  );
}

const increment = () => screen.getByRole("spinbutton", { name: "booking:settings.fields.granularity" });
const unit = (name: "Minutes" | "Hours" | "Days") =>
  within(screen.getByRole("group", { name: "booking:settings.fields.incrementUnit" })).getByRole("button", {
    name: `booking:settings.fields.increment${name}`,
  });
const alignment = () => screen.getByRole("checkbox", { name: "booking:settings.fields.alignToIncrement" });
const maximum = () => screen.getByRole("spinbutton", { name: "booking:settings.fields.maximumDuration" });
const save = () => screen.getByRole("button", { name: "booking:settings.actions.save" });
const openDay = (day: string) =>
  screen.getByRole("checkbox", {
    name: day,
  });
const sharedOpenAllDay = () =>
  within(screen.getByRole("group", { name: "Monday" })).getByRole("checkbox", {
    name: "booking:settings.openingHours.openAllDay",
  });

async function setNumber(user: ReturnType<typeof userEvent.setup>, input: HTMLElement, value: string) {
  await user.clear(input);
  await user.type(input, value);
}

describe("Booking settings time increment", () => {
  it("rejects an aligned 60-minute increment with no on-the-hour start in 09:15-10:15", async () => {
    const user = userEvent.setup();
    const submitted = serve({
      openingHours: [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "09:15", end: "10:15" })),
    });
    renderPage();

    await setNumber(user, await screen.findByRole("spinbutton", { name: "booking:settings.fields.granularity" }), "60");
    expect(increment()).toHaveAttribute("aria-invalid", "true");
    expect(increment()).toHaveAccessibleDescription("booking:settings.errors.incrementAlignedOpening");
    await user.click(save());
    expect(submitted).toHaveLength(0);

    await user.click(alignment());
    expect(screen.queryByText("booking:settings.errors.incrementAlignedOpening")).not.toBeInTheDocument();
    await user.click(save());
    await screen.findByRole("button", { name: "booking:preferences.actions.saved" });
    expect(submitted[0]).toMatchObject({
      slotGranularityMinutes: 60,
      incrementUnit: "MINUTES",
      alignToIncrement: false,
    });
  });

  it("offers Days only while every weekday is open all day", async () => {
    const user = userEvent.setup();
    serve({
      openingHours: [1, 2, 3, 4, 5, 6, 7]
        .map((dayOfWeek) => ({ dayOfWeek, start: "00:00", end: "24:00" }))
        .map(
          (entry) =>
            [{ dayOfWeek: 3, start: "00:00", end: "24:00" }].find((day) => day.dayOfWeek === entry.dayOfWeek) ?? entry,
        ),
    });
    const { container } = renderPage();

    // A redundant full-day exception still leaves every day open all day.
    await screen.findByRole("spinbutton", { name: "booking:settings.fields.granularity" });
    expect(unit("Days")).not.toHaveAttribute("aria-disabled");

    await user.click(openDay("Sunday"));
    expect(unit("Days")).toHaveAttribute("aria-disabled", "true");
    expect(unit("Days")).toHaveAccessibleDescription("booking:settings.fields.incrementDaysUnavailable");
    // It stays focusable so its reason can be read, but does not change the unit.
    unit("Days").focus();
    expect(unit("Days")).toHaveFocus();
    await user.click(unit("Days"));
    expect(unit("Minutes")).toHaveAttribute("aria-pressed", "true");
    await expectAccessible(container);

    await user.click(openDay("Sunday"));
    expect(unit("Days")).not.toHaveAttribute("aria-disabled");
    await user.click(sharedOpenAllDay());
    expect(unit("Days")).toHaveAttribute("aria-disabled", "true");
  });

  it("keeps Days selected but blocks saving once a weekday closes or the hours narrow", async () => {
    const user = userEvent.setup();
    const submitted = serve();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "booking:settings.fields.incrementDays" }));
    await setNumber(user, increment(), "1");
    expect(screen.queryByText("booking:settings.errors.incrementDaysNeedFullWeek")).not.toBeInTheDocument();

    await user.click(openDay("Sunday"));
    expect(screen.getByText("booking:settings.errors.incrementDaysNeedFullWeek")).toBeVisible();
    expect(unit("Days")).toHaveAttribute("aria-pressed", "true");
    await user.click(save());
    expect(submitted).toHaveLength(0);

    await user.click(openDay("Sunday"));
    expect(screen.queryByText("booking:settings.errors.incrementDaysNeedFullWeek")).not.toBeInTheDocument();
    await user.click(sharedOpenAllDay());
    expect(screen.getByText("booking:settings.errors.incrementDaysNeedFullWeek")).toBeVisible();
    await user.click(save());
    expect(submitted).toHaveLength(0);

    await user.click(unit("Hours"));
    expect(screen.queryByText("booking:settings.errors.incrementDaysNeedFullWeek")).not.toBeInTheDocument();
    await user.click(save());
    await screen.findByRole("button", { name: "booking:preferences.actions.saved" });
    expect(submitted[0]).toMatchObject({
      slotGranularityMinutes: 60,
      incrementUnit: "HOURS",
      openingHours: settings.openingHours.map((entry) =>
        entry.dayOfWeek === 1 ? { ...entry, start: "09:00", end: "17:00" } : entry,
      ),
    });
  });

  it("enforces the nominal Days maximum without a multiple-of-increment rule", async () => {
    const user = userEvent.setup();
    const submitted = serve();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "booking:settings.fields.incrementDays" }));
    await setNumber(user, increment(), "1");
    await setNumber(user, maximum(), "1380");
    expect(maximum()).toHaveAccessibleDescription(
      "booking:settings.fields.maximumDurationDescription booking:settings.errors.maximumDurationDays",
    );
    await setNumber(user, maximum(), "1440");
    expect(screen.queryByText("booking:settings.errors.maximumDurationDays")).not.toBeInTheDocument();
    await setNumber(user, maximum(), "1500");
    expect(screen.queryByText("booking:settings.errors.maximumDurationDays")).not.toBeInTheDocument();

    await user.click(save());
    await screen.findByRole("button", { name: "booking:preferences.actions.saved" });
    expect(submitted[0]).toMatchObject({
      slotGranularityMinutes: 1440,
      incrementUnit: "DAYS",
      maxBookingDurationMinutes: 1500,
    });
  });

  it("revalidates an untouched maximum when the unit or quantity changes", async () => {
    const user = userEvent.setup();
    const submitted = serve({ maxBookingDurationMinutes: 90 });
    renderPage();

    await screen.findByRole("spinbutton", { name: "booking:settings.fields.granularity" });
    expect(screen.queryByText("booking:settings.errors.maximumDuration")).not.toBeInTheDocument();
    await user.click(unit("Hours"));
    expect(increment()).toHaveValue(15);
    expect(screen.getByText("booking:settings.errors.maximumDuration")).toBeVisible();
    await setNumber(user, increment(), "1.5");
    expect(screen.queryByText("booking:settings.errors.maximumDuration")).not.toBeInTheDocument();

    // 1.5 Hours becomes 1.5 Days: shown invalid, never rounded.
    await user.click(unit("Days"));
    expect(increment()).toHaveValue(1.5);
    expect(maximum()).toHaveValue(90);
    expect(screen.getByText("booking:settings.errors.incrementWholeDays")).toBeVisible();
    expect(screen.getByText("booking:settings.errors.maximumDurationDays")).toBeVisible();
    await user.click(save());
    expect(submitted).toHaveLength(0);
  });

  it.each([
    [1440, "HOURS", 24, "Hours"],
    [1440, "DAYS", 1, "Days"],
    [120, "MINUTES", 120, "Minutes"],
  ] as const)("shows a stored %i %s increment as %i %s", async (minutes, storedUnit, shown, pressed) => {
    serve({ slotGranularityMinutes: minutes, incrementUnit: storedUnit });
    renderPage();

    expect(await screen.findByRole("spinbutton", { name: "booking:settings.fields.granularity" })).toHaveValue(shown);
    expect(unit(pressed)).toHaveAttribute("aria-pressed", "true");
  });

  it("keeps the saved unit when the saved settings become the new baseline", async () => {
    const user = userEvent.setup();
    const submitted = serve({ slotGranularityMinutes: 1440, incrementUnit: "HOURS" });
    renderPage();

    await user.click(await screen.findByRole("button", { name: "booking:settings.fields.incrementDays" }));
    await setNumber(user, increment(), "1");
    await user.click(save());
    await screen.findByRole("button", { name: "booking:preferences.actions.saved" });
    expect(submitted[0]).toMatchObject({ slotGranularityMinutes: 1440, incrementUnit: "DAYS" });
    await waitFor(() => expect(unit("Days")).toHaveAttribute("aria-pressed", "true"));
    expect(increment()).toHaveValue(1);
  });

  it("shows Start on clock intervals off from one day without changing the stored choice", async () => {
    const user = userEvent.setup();
    const submitted = serve();
    renderPage();

    await screen.findByRole("spinbutton", { name: "booking:settings.fields.granularity" });
    expect(alignment()).toBeChecked();
    expect(alignment()).toHaveAccessibleDescription("booking:settings.fields.alignToIncrementDescription");

    await setNumber(user, increment(), "1440");
    expect(alignment()).not.toBeChecked();
    expect(alignment()).toHaveAttribute("aria-disabled", "true");
    expect(alignment()).toHaveAccessibleDescription(
      "booking:settings.fields.alignToIncrementDescription booking:settings.fields.alignToIncrementUnavailable",
    );
    await setNumber(user, increment(), "60");
    expect(alignment()).toBeChecked();
    expect(alignment()).not.toHaveAttribute("aria-disabled");

    // A cleared choice is kept through an unavailable increment too.
    await user.click(alignment());
    await user.click(unit("Hours"));
    expect(alignment()).toHaveAttribute("aria-disabled", "true");
    await setNumber(user, increment(), "1");
    expect(alignment()).not.toBeChecked();
    expect(alignment()).not.toHaveAttribute("aria-disabled");
    await user.click(alignment());

    await user.click(unit("Days"));
    expect(alignment()).not.toBeChecked();
    expect(alignment()).toHaveAttribute("aria-disabled", "true");
    await user.click(save());
    await screen.findByRole("button", { name: "booking:preferences.actions.saved" });
    expect(submitted[0]).toMatchObject({ slotGranularityMinutes: 1440, incrementUnit: "DAYS", alignToIncrement: true });
  });
});
