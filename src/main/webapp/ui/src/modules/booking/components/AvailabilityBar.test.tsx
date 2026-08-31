import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";

vi.unmock("react-i18next");

const { renderWithRealI18n } = await import("@/__tests__/helpers/realI18n");
const { AvailabilityBar } = await import("./AvailabilityBar");

const resources = {
  booking: {
    availabilityBar: {
      current: {
        available: "Available",
        availableUntil: "Available until {time}",
        notAvailable: "Not available",
        notAvailableFrom: "Not available · Available from {time}",
      },
      label: "{itemName} availability",
      now: "Current time: {time}.",
      nowAfterWindow: "Current time {time} is after the displayed window.",
      nowBeforeWindow: "Current time {time} is before the displayed window.",
      timezone: "Time zone: {timezone}",
      summary: "Availability period: {period}. {states}",
      fullAvailable: "Available for the full period.",
      ranges: {
        available: "Available: {ranges}.",
        booking: "Booked: {ranges}.",
        blockout: "Blocked out: {ranges}.",
        overlap: "Booked and blocked out: {ranges}.",
      },
      slice: {
        count: "{count, plural, one {# constituent event} other {# constituent events}}",
        sources: {
          booking: "Booking",
          openingHours: "Outside opening hours",
        },
        states: {
          booking: "Booked",
          blockout: "Outside opening hours",
          overlap: "Booked and outside opening hours",
        },
        trigger:
          "{itemName}, {state}, {period}, {count, plural, one {# constituent event} other {# constituent events}}",
      },
    },
  },
};

const periodStart = new Date("2026-08-12T00:00:00.000Z");

const sourced = (
  id: string,
  startsAt: string,
  endsAt: string,
  kind: "booking" | "blockout" = "booking",
  sourceStartsAt = startsAt,
  sourceEndsAt = endsAt,
) => ({
  kind,
  startsAt: new Date(startsAt),
  endsAt: new Date(endsAt),
  source: {
    id,
    startsAt: new Date(sourceStartsAt),
    endsAt: new Date(sourceEndsAt),
  },
});

async function renderAvailabilityBar(props: Partial<React.ComponentProps<typeof AvailabilityBar>> = {}) {
  return renderWithRealI18n(
    <AvailabilityBar
      itemName="Confocal microscope"
      intervals={[]}
      periodStart={periodStart}
      timeZone="UTC"
      userTimeZone="UTC"
      {...props}
    />,
    { resources, defaultNS: "booking" },
  );
}

describe("AvailabilityBar", () => {
  it("defaults to the 24 hours after the period start", async () => {
    await renderAvailabilityBar();

    const graphic = screen.getByRole("img", { name: "Confocal microscope availability" });
    expect(graphic).toHaveAccessibleDescription(
      /Availability period: Aug 12, 2026, 00:00 UTC–Aug 13, 2026, 00:00 UTC\. Available for the full period\./,
    );
    expect(screen.queryByText("Time zone: UTC")).not.toBeInTheDocument();
    await expectAccessible(graphic);
  });

  it("clips, sorts, and merges overlapping or adjacent intervals of the same kind", async () => {
    await renderAvailabilityBar({
      intervals: [
        {
          kind: "booking",
          startsAt: new Date("2026-08-12T10:30:00.000Z"),
          endsAt: new Date("2026-08-12T12:00:00.000Z"),
        },
        {
          kind: "booking",
          startsAt: new Date("2026-08-12T09:00:00.000Z"),
          endsAt: new Date("2026-08-12T10:00:00.000Z"),
        },
        {
          kind: "booking",
          startsAt: new Date("2026-08-12T10:00:00.000Z"),
          endsAt: new Date("2026-08-12T11:00:00.000Z"),
        },
        {
          kind: "blockout",
          startsAt: new Date("2026-08-11T23:00:00.000Z"),
          endsAt: new Date("2026-08-12T01:00:00.000Z"),
        },
        {
          kind: "booking",
          startsAt: new Date("2026-08-13T01:00:00.000Z"),
          endsAt: new Date("2026-08-13T02:00:00.000Z"),
        },
      ],
    });

    const graphic = screen.getByRole("img", { name: "Confocal microscope availability" });
    expect(graphic).toHaveAccessibleDescription(/Booked: Aug 12, 2026, 09:00 UTC–Aug 12, 2026, 12:00 UTC\./);
    expect(graphic).toHaveAccessibleDescription(/Blocked out: Aug 12, 2026, 00:00 UTC–Aug 12, 2026, 01:00 UTC\./);
    expect(graphic).not.toHaveAccessibleDescription(/Aug 13, 2026, 01:00 UTC/);
  });

  it("reports cross-kind overlaps without hiding either state", async () => {
    await renderAvailabilityBar({
      intervals: [
        {
          kind: "booking",
          startsAt: new Date("2026-08-12T09:00:00.000Z"),
          endsAt: new Date("2026-08-12T11:00:00.000Z"),
        },
        {
          kind: "blockout",
          startsAt: new Date("2026-08-12T10:00:00.000Z"),
          endsAt: new Date("2026-08-12T12:00:00.000Z"),
        },
      ],
    });

    const graphic = screen.getByRole("img", { name: "Confocal microscope availability" });
    expect(graphic).toHaveAccessibleDescription(/Booked: Aug 12, 2026, 09:00 UTC–Aug 12, 2026, 10:00 UTC\./);
    expect(graphic).toHaveAccessibleDescription(
      /Booked and blocked out: Aug 12, 2026, 10:00 UTC–Aug 12, 2026, 11:00 UTC\./,
    );
    expect(graphic).toHaveAccessibleDescription(/Blocked out: Aug 12, 2026, 11:00 UTC–Aug 12, 2026, 12:00 UTC\./);
  });

  it("creates named buttons only for sourced occupied slices", async () => {
    await renderAvailabilityBar({
      intervals: [
        sourced("booking:1", "2026-08-12T09:00:00.000Z", "2026-08-12T10:00:00.000Z"),
        sourced("booking:2", "2026-08-12T11:00:00.000Z", "2026-08-12T12:00:00.000Z"),
      ],
    });

    expect(screen.getAllByRole("button")).toHaveLength(2);
    expect(
      screen.getByRole("button", {
        name: /Confocal microscope, Booked, Aug 12, 2026, 09:00 UTC–Aug 12, 2026, 10:00 UTC, 1 constituent event/,
      }),
    ).toBeVisible();
    expect(
      screen.getByRole("button", {
        name: /Confocal microscope, Booked, Aug 12, 2026, 11:00 UTC–Aug 12, 2026, 12:00 UTC, 1 constituent event/,
      }),
    ).toBeVisible();
  });

  it("does not expose a partial card for mixed sourced and unsourced contributors", async () => {
    await renderAvailabilityBar({
      intervals: [
        sourced("booking:1", "2026-08-12T09:00:00.000Z", "2026-08-12T11:00:00.000Z"),
        {
          kind: "booking",
          startsAt: new Date("2026-08-12T10:00:00.000Z"),
          endsAt: new Date("2026-08-12T12:00:00.000Z"),
        },
      ],
    });

    expect(screen.queryByRole("button")).not.toBeInTheDocument();
    expect(screen.getByRole("img", { name: "Confocal microscope availability" })).toHaveAccessibleDescription(
      /Booked: Aug 12, 2026, 09:00 UTC–Aug 12, 2026, 12:00 UTC/,
    );
  });

  it("keeps source-free inputs non-interactive", async () => {
    await renderAvailabilityBar({
      intervals: [
        {
          kind: "booking",
          startsAt: new Date("2026-08-12T09:00:00.000Z"),
          endsAt: new Date("2026-08-12T10:00:00.000Z"),
        },
      ],
    });

    expect(screen.queryByRole("button")).not.toBeInTheDocument();
    expect(screen.getByRole("img", { name: "Confocal microscope availability" })).toBeVisible();
  });

  it("shows neutral constituent details without rendering source IDs", async () => {
    const user = userEvent.setup();
    await renderAvailabilityBar({
      intervals: [
        sourced(
          "booking:Sensitive purpose",
          "2026-08-12T08:50:00.000Z",
          "2026-08-12T10:10:00.000Z",
          "booking",
          "2026-08-12T09:00:00.000Z",
          "2026-08-12T10:00:00.000Z",
        ),
        sourced("opening-hours:secret", "2026-08-12T09:30:00.000Z", "2026-08-12T11:00:00.000Z", "blockout"),
      ],
    });

    await user.click(screen.getAllByRole("button")[1]);

    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText("Booking")).toBeVisible();
    expect(within(dialog).getByText("Outside opening hours")).toBeVisible();
    expect(within(dialog).getByText("09:30–10:10")).toBeVisible();
    expect(within(dialog).getByText("09:00–10:00")).toBeVisible();
    expect(within(dialog).queryByText("Booked and outside opening hours")).not.toBeInTheDocument();
    expect(dialog).not.toHaveTextContent("Aug 12, 2026");
    expect(dialog).not.toHaveTextContent("Sensitive purpose");
    expect(dialog).not.toHaveTextContent("opening-hours:secret");
    await expectAccessible(dialog);
  });

  it("uses an explicitly supplied arbitrary period", async () => {
    await renderAvailabilityBar({
      periodEnd: new Date("2026-08-15T00:00:00.000Z"),
      intervals: [
        {
          kind: "booking",
          startsAt: new Date("2026-08-14T20:00:00.000Z"),
          endsAt: new Date("2026-08-14T22:00:00.000Z"),
        },
      ],
    });

    expect(screen.getByRole("img", { name: "Confocal microscope availability" })).toHaveAccessibleDescription(
      /Availability period: Aug 12, 2026, 00:00 UTC–Aug 15, 2026, 00:00 UTC\./,
    );
  });

  it("optionally shows the period start and end times", async () => {
    const { rerender } = await renderAvailabilityBar({
      periodStart: new Date("2026-08-12T08:00:00.000Z"),
      periodEnd: new Date("2026-08-12T18:00:00.000Z"),
    });

    expect(screen.queryByText("08:00")).not.toBeInTheDocument();
    expect(screen.queryByText("18:00")).not.toBeInTheDocument();

    rerender(
      <AvailabilityBar
        itemName="Confocal microscope"
        intervals={[]}
        periodStart={new Date("2026-08-12T08:00:00.000Z")}
        periodEnd={new Date("2026-08-12T18:00:00.000Z")}
        showPeriodLabels
        timeZone="UTC"
      />,
    );

    expect(screen.getByText("08:00")).toBeVisible();
    expect(screen.getByText("18:00")).toBeVisible();
  });

  it("positions and labels the current-time marker in the display timezone", async () => {
    await renderAvailabilityBar({
      now: new Date("2026-08-12T10:00:00.000Z"),
      periodStart: new Date("2026-08-11T22:00:00.000Z"),
      periodEnd: new Date("2026-08-12T22:00:00.000Z"),
      timeZone: "Europe/Berlin",
      userTimeZone: "America/New_York",
    });

    const marker = screen.getByTitle("Current time: Aug 12, 2026, 12:00 GMT+2.");
    expect(marker).toHaveStyle({ left: "50%" });
    expect(screen.getByRole("img", { name: "Confocal microscope availability" })).toHaveAccessibleDescription(
      /Current time: Aug 12, 2026, 12:00 GMT\+2\./,
    );
    expect(screen.queryByText("Time zone: Europe/Berlin")).not.toBeInTheDocument();
    expect(screen.queryByText(/^Available/)).not.toBeInTheDocument();
  });

  it.each([
    {
      expected: "Available until 10:00",
      now: new Date("2026-08-12T08:00:00.000Z"),
    },
    {
      expected: "Not available · Available from 11:00",
      now: new Date("2026-08-12T10:30:00.000Z"),
    },
  ])("optionally shows the current availability as '$expected'", async ({ expected, now }) => {
    await renderAvailabilityBar({
      intervals: [
        {
          kind: "booking",
          startsAt: new Date("2026-08-12T10:00:00.000Z"),
          endsAt: new Date("2026-08-12T11:00:00.000Z"),
        },
      ],
      now,
      showCurrentAvailability: true,
    });

    expect(screen.getByText(expected)).toBeInTheDocument();
  });

  it.each([
    {
      expected: "Available",
      intervals: [],
      now: new Date("2026-08-12T08:00:00.000Z"),
      periodEnd: undefined,
    },
    {
      expected: "Not available",
      intervals: [
        {
          kind: "booking" as const,
          startsAt: new Date("2026-08-12T23:00:00.000Z"),
          endsAt: new Date("2026-08-13T01:00:00.000Z"),
        },
      ],
      now: new Date("2026-08-12T23:30:00.000Z"),
      periodEnd: new Date("2026-08-14T00:00:00.000Z"),
    },
  ])("omits the transition time and shows '$expected'", async ({ expected, intervals, now, periodEnd }) => {
    await renderAvailabilityBar({ intervals, now, periodEnd, showCurrentAvailability: true });

    expect(screen.getByText(expected)).toBeInTheDocument();
  });

  it("clamps the current time to the edge when it is after the display period", async () => {
    await renderAvailabilityBar({
      now: new Date("2026-08-13T06:00:00.000Z"),
      periodStart: new Date("2026-08-11T16:00:00.000Z"),
      periodEnd: new Date("2026-08-12T16:00:00.000Z"),
      timeZone: "Asia/Singapore",
      userTimeZone: "America/Los_Angeles",
    });

    expect(screen.getByTitle(/after the displayed window/)).toHaveStyle({ left: "100%" });
  });

  it("clamps the current time to the edge when it is before the display period", async () => {
    await renderAvailabilityBar({
      now: new Date("2026-08-11T06:00:00.000Z"),
      periodStart: new Date("2026-08-11T16:00:00.000Z"),
      periodEnd: new Date("2026-08-12T16:00:00.000Z"),
      timeZone: "Asia/Singapore",
    });

    expect(screen.getByTitle(/before the displayed window/)).toHaveStyle({ left: "0%" });
  });

  it("rejects invalid periods and intervals", async () => {
    await expect(renderAvailabilityBar({ periodEnd: new Date("2026-08-11T00:00:00.000Z") })).rejects.toThrow(
      RangeError,
    );

    await expect(
      renderAvailabilityBar({
        intervals: [
          {
            kind: "booking",
            startsAt: new Date("2026-08-12T11:00:00.000Z"),
            endsAt: new Date("2026-08-12T10:00:00.000Z"),
          },
        ],
      }),
    ).rejects.toThrow(RangeError);
  });
});
