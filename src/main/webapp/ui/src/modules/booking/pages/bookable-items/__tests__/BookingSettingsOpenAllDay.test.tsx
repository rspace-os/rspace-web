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
  slotGranularityMinutes: 5,
  openingHours: [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "08:00", end: "18:00" })),
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

function serve(initial: Partial<typeof settings> = {}, saveSucceeds: () => boolean = () => true) {
  const submitted: Record<string, unknown>[] = [];
  let current = { ...settings, ...initial };
  server.use(
    oauthTokenHandler(true),
    http.get("/api/v2/booking-settings/admin", () => HttpResponse.json(current)),
    http.patch("/api/v2/booking-settings/admin", async ({ request }) => {
      const body = (await request.json()) as Record<string, unknown>;
      submitted.push(body);
      if (!saveSucceeds()) return HttpResponse.json({ status: 500 }, { status: 500 });
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

type User = ReturnType<typeof userEvent.setup>;

const mondayHours = () => screen.getByRole("group", { name: "Monday" });
const findMondayHours = () => screen.findByRole("group", { name: "Monday" });
const dayHours = (day: string) => screen.getByRole("group", { name: day });
const controls = (group: HTMLElement) => ({
  start: within(group).getByLabelText("booking:settings.fields.openingStart"),
  end: within(group).getByLabelText("booking:settings.fields.openingEnd"),
  allDay: within(group).getByRole("checkbox", { name: "booking:settings.openingHours.openAllDay" }),
});
const save = () => screen.getByRole("button", { name: "booking:settings.actions.save" });

async function setHours(user: User, group: HTMLElement, start: string, end: string) {
  const { start: opens, end: closes } = controls(group);
  await user.clear(opens);
  await user.type(opens, start);
  await user.clear(closes);
  await user.type(closes, end);
}

function expectHours(group: HTMLElement, start: string, end: string) {
  expect(controls(group).start).toHaveValue(start);
  expect(controls(group).end).toHaveValue(end);
}

describe("Open all day", () => {
  it("follows the 00:00 to 24:00 pair and falls back to 09:00-17:00", async () => {
    const user = userEvent.setup();
    serve({ openingHours: [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "00:00", end: "24:00" })) });
    const { container } = renderPage();

    const { start, end, allDay } = controls(await findMondayHours());
    expect(allDay).toBeChecked();
    expect(start).toBeDisabled();
    expect(end).toBeDisabled();
    expect(end).toHaveValue("00:00");
    expect(end).toHaveAccessibleDescription("booking:settings.openingHours.midnightEnd");
    await expectAccessible(container);

    // Keyboard operation keeps focus on the checkbox; an all-day baseline has no custom hours to restore.
    allDay.focus();
    await user.keyboard(" ");
    expect(allDay).not.toBeChecked();
    expect(allDay).toHaveFocus();
    expectHours(mondayHours(), "09:00", "17:00");
    expect(start).toBeEnabled();
    await user.keyboard(" ");
    expect(allDay).toBeChecked();
  });

  it("selects the whole day when typed, moves focus to the checkbox and restores the typed hours", async () => {
    const user = userEvent.setup();
    serve({ openingHours: [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "01:00", end: "24:00" })) });
    renderPage();

    const { start, allDay } = controls(await findMondayHours());
    expect(allDay).not.toBeChecked();
    await user.clear(start);
    await user.type(start, "00:00");
    expect(allDay).toBeChecked();
    expect(start).toBeDisabled();
    await waitFor(() => expect(allDay).toHaveFocus());
    expect(screen.getByText("booking:settings.openingHours.openAllDaySelected")).toBeInTheDocument();

    await user.click(allDay);
    expectHours(mondayHours(), "01:00", "00:00");
    expect(allDay).toHaveFocus();
    expect(screen.queryByText("booking:settings.openingHours.openAllDaySelected")).not.toBeInTheDocument();
  });

  it("remembers custom hours from typing and the checkbox, never blank or invalid drafts", async () => {
    const user = userEvent.setup();
    serve();
    renderPage();

    await setHours(user, await findMondayHours(), "10:00", "16:00");
    const { start, allDay } = controls(mondayHours());
    await user.click(allDay);
    await user.click(allDay);
    expectHours(mondayHours(), "10:00", "16:00");

    await user.clear(start);
    await user.type(start, "17:00");
    await user.click(allDay);
    await user.click(allDay);
    expectHours(mondayHours(), "10:00", "16:00");

    await user.clear(start);
    await user.click(allDay);
    await user.click(allDay);
    expectHours(mondayHours(), "10:00", "16:00");
  });

  it("keeps each weekday's history and closed-day draft independent", async () => {
    const user = userEvent.setup();
    const submitted = serve({ openingHours: settings.openingHours.filter((entry) => entry.dayOfWeek !== 7) });
    renderPage();
    await findMondayHours();

    await setHours(user, dayHours("Tuesday"), "10:00", "16:00");
    await user.click(controls(dayHours("Tuesday")).allDay);
    await user.click(controls(dayHours("Wednesday")).allDay);
    await user.click(controls(dayHours("Wednesday")).allDay);
    expectHours(dayHours("Wednesday"), "08:00", "18:00");

    await user.click(screen.getByRole("checkbox", { name: "Tuesday" }));
    await user.click(screen.getByRole("checkbox", { name: "Tuesday" }));
    expect(controls(dayHours("Tuesday")).allDay).toBeChecked();
    await user.click(controls(dayHours("Tuesday")).allDay);
    expectHours(dayHours("Tuesday"), "10:00", "16:00");
    expect(screen.getByRole("checkbox", { name: "Sunday" })).not.toBeChecked();

    await user.click(save());
    expect(await screen.findByRole("button", { name: "booking:preferences.actions.saved" })).toBeDisabled();
    expect(submitted[0]).toMatchObject({
      openingHours: settings.openingHours
        .filter((entry) => entry.dayOfWeek !== 7)
        .map((entry) => (entry.dayOfWeek === 2 ? { ...entry, start: "10:00", end: "16:00" } : entry)),
    });
  });

  it("keeps the history after a failed save and starts afresh from an accepted one", async () => {
    const user = userEvent.setup();
    let succeed = false;
    const submitted = serve({}, () => succeed);
    renderPage();

    await setHours(user, await findMondayHours(), "10:00", "16:00");
    await setHours(user, dayHours("Tuesday"), "11:00", "15:00");
    await user.click(screen.getByRole("checkbox", { name: "Tuesday" }));
    await user.click(controls(mondayHours()).allDay);
    await user.click(save());
    expect(await screen.findByText("booking:settings.errors.save")).toBeVisible();
    await user.click(controls(mondayHours()).allDay);
    expectHours(mondayHours(), "10:00", "16:00");
    await user.click(screen.getByRole("checkbox", { name: "Tuesday" }));
    expectHours(dayHours("Tuesday"), "11:00", "15:00");
    await user.click(screen.getByRole("checkbox", { name: "Tuesday" }));

    succeed = true;
    await user.click(controls(mondayHours()).allDay);
    await user.click(save());
    expect(await screen.findByRole("button", { name: "booking:preferences.actions.saved" })).toBeDisabled();
    expect(submitted.at(-1)).toMatchObject({
      openingHours: settings.openingHours
        .filter((entry) => entry.dayOfWeek !== 2)
        .map((entry) => (entry.dayOfWeek === 1 ? { ...entry, start: "00:00", end: "24:00" } : entry)),
    });
    await user.click(controls(mondayHours()).allDay);
    expectHours(mondayHours(), "09:00", "17:00");
    await user.click(screen.getByRole("checkbox", { name: "Tuesday" }));
    expect(controls(dayHours("Tuesday")).allDay).toBeChecked();
  });

  it("restores a closed day's unsaved hours and requires at least one open day", async () => {
    const user = userEvent.setup();
    const submitted = serve({ openingHours: settings.openingHours.filter((entry) => entry.dayOfWeek === 1) });
    const { container } = renderPage();
    await setHours(user, await findMondayHours(), "10:00", "16:00");
    const monday = screen.getByRole("checkbox", { name: "Monday" });

    await user.click(monday);
    expect(monday).toHaveAccessibleDescription("booking:settings.openingHours.errors.noDays");
    expect(monday).toBeInvalid();
    await user.click(save());
    expect(submitted).toHaveLength(0);
    await expectAccessible(container);

    await user.click(monday);
    expectHours(mondayHours(), "10:00", "16:00");
    await user.click(save());
    expect(await screen.findByRole("button", { name: "booking:preferences.actions.saved" })).toBeDisabled();
    expect(submitted[0]).toMatchObject({ openingHours: [{ dayOfWeek: 1, start: "10:00", end: "16:00" }] });
  });
});
