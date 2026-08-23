import { screen } from "@testing-library/react";
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
      summary: "Availability period: {period}. {states}",
      fullAvailable: "Available for the full period.",
      ranges: {
        available: "Available: {ranges}.",
        booking: "Booked: {ranges}.",
        blockout: "Blocked out: {ranges}.",
        overlap: "Booked and blocked out: {ranges}.",
      },
    },
  },
};

const periodStart = new Date("2026-08-12T00:00:00.000Z");

async function renderAvailabilityBar(props: Partial<React.ComponentProps<typeof AvailabilityBar>> = {}) {
  return renderWithRealI18n(
    <AvailabilityBar
      itemName="Confocal microscope"
      intervals={[]}
      periodStart={periodStart}
      timeZone="UTC"
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

  it("positions an optional current-time marker within the period", async () => {
    await renderAvailabilityBar({ now: new Date("2026-08-12T12:00:00.000Z") });

    const marker = screen.getByTitle("Current time: Aug 12, 2026, 12:00 UTC.");
    expect(marker).toHaveStyle({ left: "50%" });
    expect(screen.getByRole("img", { name: "Confocal microscope availability" })).toHaveAccessibleDescription(
      /Current time: Aug 12, 2026, 12:00 UTC\./,
    );
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

  it("omits the current-time marker when it is outside the period", async () => {
    await renderAvailabilityBar({ now: new Date("2026-08-13T00:00:00.000Z") });

    expect(screen.queryByTitle(/Current time:/)).not.toBeInTheDocument();
    expect(screen.getByRole("img", { name: "Confocal microscope availability" })).not.toHaveAccessibleDescription(
      /Current time:/,
    );
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
