import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import {
  createBookingSettingsFixture,
  renderBookingSettingsPage,
  serveBookingSettings,
} from "./bookingSettingsTestHarness";

const settings = createBookingSettingsFixture({
  slotGranularityMinutes: 15,
  bufferBeforeMinutes: 0,
  bufferAfterMinutes: 0,
});

function serve(initial: Partial<typeof settings> = {}, saveSucceeds: () => boolean = () => true) {
  return serveBookingSettings({ ...settings, ...initial }, saveSucceeds);
}

type User = ReturnType<typeof userEvent.setup>;

const sharedHours = () => screen.getByRole("group", { name: "Monday" });
const findSharedHours = () => screen.findByRole("group", { name: "Monday" });
const dayRow = (day: string) => {
  const row = within(screen.getByRole("list", { name: "booking:bookableItemDetails.fields.openingHours" }))
    .getAllByRole("listitem")
    .find((item) => within(item).queryByText(day, { exact: true }));
  if (!row) throw new Error(`No ${day} row`);
  return row;
};
const dayHours = (day: string) => within(dayRow(day)).getByRole("group", { name: day });
const controls = (group: HTMLElement) => ({
  get start() {
    return within(group).getByLabelText("booking:settings.fields.openingStart");
  },
  get end() {
    return within(group).getByLabelText("booking:settings.fields.openingEnd");
  },
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
  it("describes midnight only for a custom interval ending at midnight", async () => {
    const user = userEvent.setup();
    serve({ openingHours: [{ dayOfWeek: 1, start: "08:00", end: "18:00" }] });
    renderBookingSettingsPage();
    const group = await findSharedHours();
    expect(within(group).queryByText("booking:settings.openingHours.midnightEnd")).not.toBeInTheDocument();
    const end = controls(group).end;
    await user.clear(end);
    await user.type(end, "00:00");
    expect(end).toHaveAccessibleDescription("booking:settings.openingHours.midnightEnd");
  });

  it("follows the 00:00 to 24:00 pair and falls back to 09:00-17:00", async () => {
    const user = userEvent.setup();
    serve({ openingHours: [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "00:00", end: "24:00" })) });
    const { container } = renderBookingSettingsPage();

    const group = await findSharedHours();
    const allDay = within(group).getByRole("checkbox", { name: "booking:settings.openingHours.openAllDay" });
    expect(allDay).toBeChecked();
    expect(within(group).queryByLabelText("booking:settings.fields.openingStart")).not.toBeInTheDocument();
    expect(within(group).queryByLabelText("booking:settings.fields.openingEnd")).not.toBeInTheDocument();
    await expectAccessible(container);

    // Keyboard operation keeps focus on the checkbox; an all-day baseline has no custom hours to restore.
    allDay.focus();
    await user.keyboard(" ");
    expect(allDay).not.toBeChecked();
    expect(allDay).toHaveFocus();
    expectHours(sharedHours(), "09:00", "17:00");
    expect(controls(group).start).toBeEnabled();
    await user.keyboard(" ");
    expect(allDay).toBeChecked();
  });

  it("keeps typed midnight controls focused and restores the remembered custom hours", async () => {
    const user = userEvent.setup();
    serve({ openingHours: [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "01:00", end: "24:00" })) });
    renderBookingSettingsPage();

    const { start, allDay } = controls(await findSharedHours());
    expect(allDay).not.toBeChecked();
    await user.clear(start);
    await user.type(start, "00:00");
    expect(allDay).toBeChecked();
    expect(start).toBeInTheDocument();
    expect(start).toBeEnabled();
    expect(start).toHaveFocus();
    expect(screen.getByText("booking:settings.openingHours.openAllDaySelected")).toBeInTheDocument();

    await user.click(allDay);
    expectHours(sharedHours(), "01:00", "00:00");
    expect(allDay).toHaveFocus();
    expect(screen.queryByText("booking:settings.openingHours.openAllDaySelected")).not.toBeInTheDocument();
  });

  it("remembers custom hours from typing and the checkbox, never blank or invalid drafts", async () => {
    const user = userEvent.setup();
    serve();
    renderBookingSettingsPage();

    await setHours(user, await findSharedHours(), "10:00", "16:00");
    const { allDay } = controls(sharedHours());
    await user.click(allDay);
    await user.click(allDay);
    expectHours(sharedHours(), "10:00", "16:00");

    await user.clear(controls(sharedHours()).start);
    await user.type(controls(sharedHours()).start, "17:00");
    await user.click(allDay);
    await user.click(allDay);
    expectHours(sharedHours(), "10:00", "16:00");

    await user.clear(controls(sharedHours()).start);
    await user.click(allDay);
    await user.click(allDay);
    expectHours(sharedHours(), "10:00", "16:00");
  });

  it("keeps each weekday's history and closed-day draft independent", async () => {
    const user = userEvent.setup();
    const submitted = serve({ openingHours: settings.openingHours.filter((entry) => entry.dayOfWeek !== 7) });
    renderBookingSettingsPage();
    await findSharedHours();

    await setHours(user, dayHours("Tuesday"), "10:00", "16:00");
    await user.click(controls(dayHours("Tuesday")).allDay);
    await user.click(controls(dayHours("Wednesday")).allDay);
    await user.click(controls(dayHours("Wednesday")).allDay);
    expectHours(dayHours("Wednesday"), "08:00", "18:00");

    await user.click(screen.getByRole("checkbox", { name: "Tuesday" }));
    await user.click(screen.getByRole("checkbox", { name: "Tuesday" }));
    expect(
      within(dayHours("Tuesday")).getByRole("checkbox", { name: "booking:settings.openingHours.openAllDay" }),
    ).toBeChecked();
    await user.click(controls(dayHours("Tuesday")).allDay);
    expectHours(dayHours("Tuesday"), "10:00", "16:00");
    expect(screen.getByRole("checkbox", { name: "Sunday" })).not.toBeChecked();

    await user.click(save());
    expect(await screen.findByRole("status")).toHaveTextContent("booking:settings.saved");
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
    renderBookingSettingsPage();

    await setHours(user, await findSharedHours(), "10:00", "16:00");
    await setHours(user, dayHours("Tuesday"), "11:00", "15:00");
    await user.click(screen.getByRole("checkbox", { name: "Tuesday" }));
    await user.click(controls(sharedHours()).allDay);
    await user.click(save());
    expect(await screen.findByText("booking:settings.errors.save")).toBeVisible();
    await user.click(controls(sharedHours()).allDay);
    expectHours(sharedHours(), "10:00", "16:00");
    await user.click(screen.getByRole("checkbox", { name: "Tuesday" }));
    expectHours(dayHours("Tuesday"), "11:00", "15:00");
    await user.click(screen.getByRole("checkbox", { name: "Tuesday" }));

    succeed = true;
    await user.click(controls(sharedHours()).allDay);
    await user.click(save());
    expect(await screen.findByRole("status")).toHaveTextContent("booking:settings.saved");
    expect(submitted.at(-1)).toMatchObject({
      openingHours: settings.openingHours
        .filter((entry) => entry.dayOfWeek !== 2)
        .map((entry) => (entry.dayOfWeek === 1 ? { ...entry, start: "00:00", end: "24:00" } : entry)),
    });
    await user.click(controls(sharedHours()).allDay);
    expectHours(sharedHours(), "09:00", "17:00");
    await user.click(screen.getByRole("checkbox", { name: "Tuesday" }));
    expect(
      within(dayHours("Tuesday")).getByRole("checkbox", { name: "booking:settings.openingHours.openAllDay" }),
    ).toBeChecked();
  });

  it("restores a closed day's unsaved hours and requires at least one open day", async () => {
    const user = userEvent.setup();
    const submitted = serve({ openingHours: settings.openingHours.filter((entry) => entry.dayOfWeek === 1) });
    const { container } = renderBookingSettingsPage();
    await setHours(user, await findSharedHours(), "10:00", "16:00");
    const monday = screen.getByRole("checkbox", { name: "Monday" });

    await user.click(monday);
    expect(monday).toHaveAccessibleDescription("booking:settings.openingHours.errors.noDays");
    expect(monday).toBeInvalid();
    await user.click(save());
    expect(submitted).toHaveLength(0);
    await expectAccessible(container);

    await user.click(monday);
    expectHours(sharedHours(), "10:00", "16:00");
    await user.click(save());
    expect(await screen.findByRole("status")).toHaveTextContent("booking:settings.saved");
    expect(submitted[0]).toMatchObject({ openingHours: [{ dayOfWeek: 1, start: "10:00", end: "16:00" }] });
  });
});
