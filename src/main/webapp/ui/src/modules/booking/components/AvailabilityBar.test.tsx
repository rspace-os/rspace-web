import {
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  Outlet,
  RouterContextProvider,
  RouterProvider,
} from "@tanstack/react-router";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import type { AvailabilitySource } from "@/modules/booking/domain/availability";
import bookingEnglish from "@/modules/common/i18n/locales/en-US/booking.json";

vi.unmock("react-i18next");

const { renderWithRealI18n } = await import("@/__tests__/helpers/realI18n");
const { AvailabilityBar } = await import("./AvailabilityBar");
const { BookingSummaryAccordion } = await import("./BookingSummaryAccordion");

const resources = { booking: bookingEnglish };

const periodStart = new Date("2026-08-12T00:00:00.000Z");
const item = {
  name: "Confocal microscope",
  globalId: "IN123",
  location: { name: "Imaging suite", globalId: "IC456" },
};

const sourced = (
  id: string,
  startsAt: string,
  endsAt: string,
  kind: "booking" | "blockout" = "booking",
  sourceStartsAt = startsAt,
  sourceEndsAt = endsAt,
  booking?: AvailabilitySource["booking"],
) => ({
  kind,
  startsAt: new Date(startsAt),
  endsAt: new Date(endsAt),
  source: {
    id,
    startsAt: new Date(sourceStartsAt),
    endsAt: new Date(sourceEndsAt),
    ...(booking ? { booking } : {}),
  },
});

function availabilityBar(props: Partial<React.ComponentProps<typeof AvailabilityBar>> = {}) {
  return <AvailabilityBar item={item} intervals={[]} periodStart={periodStart} timeZone="UTC" {...props} />;
}

async function renderAvailabilityBar(props: Partial<React.ComponentProps<typeof AvailabilityBar>> = {}) {
  return renderWithRealI18n(availabilityBar(props), { resources, defaultNS: "booking" });
}

async function renderRoutedAvailabilityBar(props: Partial<React.ComponentProps<typeof AvailabilityBar>>) {
  const root = createRootRoute({ component: Outlet });
  const index = createRoute({ getParentRoute: () => root, path: "/", component: () => availabilityBar(props) });
  const details = createRoute({ getParentRoute: () => root, path: "/booking/calendar/bookings/$id" });
  const router = createRouter({
    routeTree: root.addChildren([index, details]),
    history: createMemoryHistory({ initialEntries: ["/"] }),
  });
  return {
    ...(await renderWithRealI18n(<RouterProvider router={router as never} />, { resources, defaultNS: "booking" })),
    router,
  };
}

describe("AvailabilityBar", () => {
  it("describes unbuffered source times, overlap intersections, and visible gaps", async () => {
    await renderAvailabilityBar({
      periodStart: new Date("2026-08-12T08:00:00Z"),
      periodEnd: new Date("2026-08-12T12:00:00Z"),
      intervals: [
        sourced(
          "buffered",
          "2026-08-12T08:45:00Z",
          "2026-08-12T10:15:00Z",
          "booking",
          "2026-08-12T09:00:00Z",
          "2026-08-12T10:00:00Z",
        ),
        sourced("blockout", "2026-08-12T09:30:00Z", "2026-08-12T11:00:00Z", "blockout"),
      ],
    });
    expect(screen.getByRole("img", { name: "Confocal microscope availability" })).toHaveAccessibleDescription(
      "Availability period: Aug 12, 2026, 08:00–12:00. Available: Aug 12, 2026, 08:00–08:45 and Aug 12, 2026, 11:00–12:00. Booked: Aug 12, 2026, 09:00–10:00. Unavailable: Aug 12, 2026, 09:30–11:00. Booked and unavailable: Aug 12, 2026, 09:30–10:15. Gap before or after a booking: Aug 12, 2026, 08:45–09:00.",
    );
  });
  it.each([
    {
      name: "overnight",
      timeZone: "UTC",
      start: "2026-08-12T23:30:00Z",
      end: "2026-08-13T00:30:00Z",
      bookingStart: "2026-08-12T23:45:00Z",
      bookingEnd: "2026-08-13T00:15:00Z",
      expected:
        "Booked: Aug 12, 2026, 23:45–Aug 13, 2026, 00:15. Gap before or after a booking: Aug 12, 2026, 23:30–23:45 and Aug 13, 2026, 00:15–00:30.",
    },
    {
      name: "clock-change",
      timeZone: "Europe/London",
      start: "2026-10-25T00:45:00Z",
      end: "2026-10-25T02:00:00Z",
      bookingStart: "2026-10-25T01:15:00Z",
      bookingEnd: "2026-10-25T01:45:00Z",
      expected:
        "Booked: Oct 25, 2026, 01:15 +00:00–01:45 +00:00. Gap before or after a booking: Oct 25, 2026, 01:45 +01:00–01:15 +00:00 and Oct 25, 2026, 01:45 +00:00–02:00 +00:00.",
    },
  ])(
    "preserves $name dates and offsets in booking and gap descriptions",
    async ({ timeZone, start, end, bookingStart, bookingEnd, expected }) => {
      await renderAvailabilityBar({
        timeZone,
        periodStart: new Date(start),
        periodEnd: new Date(end),
        intervals: [sourced("buffered", start, end, "booking", bookingStart, bookingEnd)],
      });
      expect(screen.getByRole("img", { name: "Confocal microscope availability" })).toHaveAccessibleDescription(
        expect.stringContaining(expected),
      );
    },
  );

  it("keeps clock-change offsets in a same-day accessible range", async () => {
    await renderAvailabilityBar({
      timeZone: "Europe/London",
      periodStart: new Date("2026-10-25T00:00:00Z"),
      intervals: [sourced("clock-change", "2026-10-25T00:30:00Z", "2026-10-25T01:30:00Z")],
    });
    expect(
      screen.getByRole("button", {
        name: "Confocal microscope, Booked, Oct 25, 2026, 01:30 +01:00–01:30 +00:00, 1 event",
      }),
    ).toHaveAccessibleName("Confocal microscope, Booked, Oct 25, 2026, 01:30 +01:00–01:30 +00:00, 1 event");
  });

  it.each([true, false, undefined])(
    "gates summary item links on configuration access (%s)",
    async (canViewConfiguration) => {
      const user = userEvent.setup();
      const router = createRouter({ routeTree: createRootRoute(), history: createMemoryHistory() });
      await renderWithRealI18n(
        <RouterContextProvider router={router}>
          <BookingSummaryAccordion
            accordionName="permissions"
            heading={item.name}
            period="09:00–10:00"
            purpose="Imaging"
            item={item}
            canViewConfiguration={canViewConfiguration}
          />
        </RouterContextProvider>,
        { resources, defaultNS: "booking" },
      );
      await user.click(await screen.findByLabelText("Show details for Confocal microscope, 09:00–10:00"));
      if (canViewConfiguration) {
        expect(screen.getByRole("link", { name: item.name })).toHaveAttribute("href", "/booking/bookable-items/IN123");
        expect(screen.getByRole("link", { name: "Open inventory record IN123" })).toHaveAttribute(
          "href",
          "/globalId/IN123",
        );
      } else {
        expect(screen.queryByRole("link", { name: item.name })).not.toBeInTheDocument();
        expect(screen.queryByRole("link", { name: "Open inventory record IN123" })).not.toBeInTheDocument();
        expect(screen.getAllByText(item.name)).toHaveLength(2);
      }
    },
  );

  it("renders a booking summary without availability-bar context", async () => {
    const user = userEvent.setup();
    await renderWithRealI18n(
      <BookingSummaryAccordion
        accordionName="dashboard-bookings"
        heading="Confocal microscope"
        period="09:00–10:00"
        purpose="Cell imaging"
      />,
      { resources, defaultNS: "booking" },
    );

    expect(screen.getByText("Confocal microscope")).toBeVisible();
    expect(screen.queryByRole("link", { name: "Details" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Open inventory record IN123" })).not.toBeInTheDocument();

    await user.click(await screen.findByLabelText("Show details for Confocal microscope, 09:00–10:00"));

    expect(screen.getByText("Cell imaging")).toBeVisible();
    expect(screen.queryByText("None provided")).not.toBeInTheDocument();
  });

  it("explains an empty DST display window without drawing a misleading availability bar", async () => {
    await renderAvailabilityBar({ periodEnd: periodStart });
    expect(
      screen.getByText("This display window contains no time on this date because the clocks change."),
    ).toBeVisible();
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
  });

  it("defaults to the 24 hours after the period start", async () => {
    await renderAvailabilityBar();

    const graphic = screen.getByRole("img", { name: "Confocal microscope availability" });
    expect(graphic).toHaveAccessibleDescription(
      "Availability period: Aug 12, 2026, 00:00–Aug 13, 2026, 00:00. Available for the full period.",
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
    expect(graphic).toHaveAccessibleDescription(
      "Availability period: Aug 12, 2026, 00:00–Aug 13, 2026, 00:00. Available: Aug 12, 2026, 01:00–09:00 and Aug 12, 2026, 12:00–Aug 13, 2026, 00:00. Booked: Aug 12, 2026, 09:00–12:00. Unavailable: Aug 12, 2026, 00:00–01:00.",
    );
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
    expect(graphic).toHaveAccessibleDescription(
      "Availability period: Aug 12, 2026, 00:00–Aug 13, 2026, 00:00. Available: Aug 12, 2026, 00:00–09:00 and Aug 12, 2026, 12:00–Aug 13, 2026, 00:00. Booked: Aug 12, 2026, 09:00–10:00. Unavailable: Aug 12, 2026, 11:00–12:00. Booked and unavailable: Aug 12, 2026, 10:00–11:00.",
    );
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
        name: "Confocal microscope, Booked, Aug 12, 2026, 09:00–10:00, 1 event",
      }),
    ).toBeVisible();
    expect(
      screen.getByRole("button", {
        name: "Confocal microscope, Booked, Aug 12, 2026, 11:00–12:00, 1 event",
      }),
    ).toBeVisible();
  });

  it("rounds highlighted slices where they meet either end of the bar", async () => {
    const user = userEvent.setup();
    await renderAvailabilityBar({
      intervals: [
        sourced("booking:1", "2026-08-11T23:00:00.000Z", "2026-08-12T01:00:00.000Z"),
        sourced("booking:2", "2026-08-12T23:00:00.000Z", "2026-08-13T01:00:00.000Z"),
      ],
    });

    const leftSlice = screen.getByRole("button", {
      name: "Confocal microscope, Booked, Aug 12, 2026, 00:00–01:00, 1 event",
    });
    const rightSlice = screen.getByRole("button", {
      name: "Confocal microscope, Booked, Aug 12, 2026, 23:00–Aug 13, 2026, 00:00, 1 event",
    });
    await user.click(leftSlice);
    expect(leftSlice).toHaveAttribute("data-popup-open");
    expect(leftSlice).toHaveClass("rounded-l-full");

    await user.click(rightSlice);
    expect(rightSlice).toHaveAttribute("data-popup-open");
    expect(rightSlice).toHaveClass("rounded-r-full");
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
      "Availability period: Aug 12, 2026, 00:00–Aug 13, 2026, 00:00. Available: Aug 12, 2026, 00:00–09:00 and Aug 12, 2026, 12:00–Aug 13, 2026, 00:00. Booked: Aug 12, 2026, 09:00–12:00.",
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
          {
            id: 99,
            kind: "BOOKING",
            privacy: "busy",
            purpose: null,
            bookedBy: null,
          },
        ),
        sourced("opening-hours:secret", "2026-08-12T09:30:00.000Z", "2026-08-12T11:00:00.000Z", "blockout"),
      ],
    });

    await user.click(screen.getAllByRole("button")[1]);

    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText("2 events")).toBeVisible();
    expect(within(dialog).getByText("Booking")).toBeVisible();
    expect(within(dialog).getByText("Outside opening hours")).toBeVisible();
    expect(within(dialog).getByText("09:30–10:10")).toBeVisible();
    expect(within(dialog).getByText("09:00–10:00")).toBeVisible();
    expect(within(dialog).queryByText("Booked and outside opening hours")).not.toBeInTheDocument();
    expect(dialog).not.toHaveTextContent("Aug 12, 2026");
    expect(dialog).not.toHaveTextContent("Sensitive purpose");
    expect(dialog).not.toHaveTextContent("opening-hours:secret");
    expect(within(dialog).queryByRole("link", { name: "Details" })).not.toBeInTheDocument();
    await expectAccessible(dialog);
  });

  it.each(["BOOKING", "MAINTENANCE"] as const)(
    "expands authorized %s details and links to the booking page",
    async (kind) => {
      const user = userEvent.setup();
      const { router } = await renderRoutedAvailabilityBar({
        intervals: [
          sourced(
            "booking:41",
            "2026-08-12T09:00:00.000Z",
            "2026-08-12T10:00:00.000Z",
            kind === "BOOKING" ? "booking" : "blockout",
            undefined,
            undefined,
            {
              id: 41,
              kind,
              privacy: "full",
              canViewConfiguration: true,
              purpose: "Cell imaging",
              bookedBy: "Ada Lovelace",
              createdBy: "Ada Lovelace",
            },
          ),
        ],
      });

      await user.hover(
        await screen.findByRole("button", {
          name: `Confocal microscope, ${kind === "BOOKING" ? "Booked" : "Unavailable"}, Aug 12, 2026, 09:00–10:00, 1 event`,
        }),
      );
      const dialog = await screen.findByRole("dialog");
      const disclosure = within(dialog).getByLabelText(
        `Show details for ${kind === "BOOKING" ? "Confocal microscope" : "Maintenance"}, 09:00–10:00`,
      );
      expect(within(dialog).getByText(kind === "BOOKING" ? "Booked by Ada Lovelace" : "Maintenance")).toBeVisible();
      await user.click(disclosure);

      expect(within(dialog).getByRole("link", { name: "Open inventory record IN123" })).toBeVisible();
      expect(within(dialog).getByRole("link", { name: "Imaging suite" })).toBeVisible();
      expect(within(dialog).getByText("Ada Lovelace")).toBeVisible();
      expect(within(dialog).getByText("Cell imaging")).toBeVisible();
      const details = within(dialog).getByRole("link", { name: "Details" });
      expect(details).toHaveAttribute("href", "/booking/calendar/bookings/41");
      await expectAccessible(dialog);

      await user.click(details);
      await waitFor(() => expect(router.state.location.pathname).toBe("/booking/calendar/bookings/41"));
    },
  );

  it("expands full-privacy maintenance details from a blockout source", async () => {
    const user = userEvent.setup();
    await renderRoutedAvailabilityBar({
      intervals: [
        sourced(
          "maintenance:42",
          "2026-08-12T09:00:00.000Z",
          "2026-08-12T10:00:00.000Z",
          "blockout",
          undefined,
          undefined,
          {
            id: 42,
            kind: "MAINTENANCE",
            privacy: "full",
            purpose: "Laser alignment and inspection",
            bookedBy: "Booking initiator",
            createdBy: "Grace Hopper (grace)",
          },
        ),
      ],
    });

    await user.click(await screen.findByRole("button"));
    const dialog = await screen.findByRole("dialog");
    const disclosure = within(dialog).getByLabelText("Show details for Maintenance, 09:00–10:00");
    await user.click(disclosure);

    expect(within(dialog).getByText("Created by")).toBeVisible();
    expect(within(dialog).getByText("Grace Hopper (grace)")).toBeVisible();
    expect(within(dialog).getByText("Notes")).toBeVisible();
    expect(within(dialog).getByText("Laser alignment and inspection")).toBeVisible();
  });

  it("keeps busy maintenance actor and purpose metadata hidden", async () => {
    const user = userEvent.setup();
    await renderAvailabilityBar({
      intervals: [
        sourced(
          "maintenance:43",
          "2026-08-12T09:00:00.000Z",
          "2026-08-12T10:00:00.000Z",
          "blockout",
          undefined,
          undefined,
          {
            id: 43,
            kind: "MAINTENANCE",
            privacy: "busy",
            purpose: "Private maintenance reason",
            bookedBy: "Private maintenance actor",
            createdBy: "Private maintenance actor",
          },
        ),
      ],
    });

    await user.click(screen.getByRole("button"));
    const dialog = await screen.findByRole("dialog");

    expect(within(dialog).getByText("Maintenance")).toBeVisible();
    expect(screen.queryByText("Private maintenance actor")).not.toBeInTheDocument();
    expect(screen.queryByText("Private maintenance reason")).not.toBeInTheDocument();
  });

  it("can omit redundant booking context while preserving the reusable detail path", async () => {
    const user = userEvent.setup();
    await renderRoutedAvailabilityBar({
      intervals: [
        sourced("booking:41", "2026-08-12T09:00:00.000Z", "2026-08-12T10:00:00.000Z", "booking", undefined, undefined, {
          id: 41,
          kind: "BOOKING",
          privacy: "full",
          purpose: "Cell imaging",
          bookedBy: "Ada Lovelace",
        }),
      ],
      showBookingContextDetails: false,
    });

    await user.hover(
      await screen.findByRole("button", {
        name: "Confocal microscope, Booked, Aug 12, 2026, 09:00–10:00, 1 event",
      }),
    );
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByLabelText("Show details for Confocal microscope, 09:00–10:00"));

    expect(within(dialog).getByText("Booked by Ada Lovelace")).toBeVisible();
    expect(within(dialog).getByText("Cell imaging")).toBeVisible();
    expect(within(dialog).queryByRole("link", { name: "Open inventory record IN123" })).not.toBeInTheDocument();
    expect(within(dialog).queryByText("Booked by")).not.toBeInTheDocument();
    expect(within(dialog).getByRole("link", { name: "Details" })).toBeVisible();
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
      "Availability period: Aug 12, 2026, 00:00–Aug 15, 2026, 00:00. Available: Aug 12, 2026, 00:00–Aug 14, 2026, 20:00 and Aug 14, 2026, 22:00–Aug 15, 2026, 00:00. Booked: Aug 14, 2026, 20:00–22:00.",
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
        item={item}
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
      "Availability period: Aug 12, 2026, 00:00–Aug 13, 2026, 00:00. Available for the full period. Current time: Aug 12, 2026, 12:00 GMT+2.",
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

    expect(screen.getByTitle("Current time Aug 13, 2026, 14:00 GMT+8 is after the displayed window.")).toHaveStyle({
      left: "100%",
    });
  });

  it("clamps the current time to the edge when it is before the display period", async () => {
    await renderAvailabilityBar({
      now: new Date("2026-08-11T06:00:00.000Z"),
      periodStart: new Date("2026-08-11T16:00:00.000Z"),
      periodEnd: new Date("2026-08-12T16:00:00.000Z"),
      timeZone: "Asia/Singapore",
    });

    expect(screen.getByTitle("Current time Aug 11, 2026, 14:00 GMT+8 is before the displayed window.")).toHaveStyle({
      left: "0%",
    });
  });
});
