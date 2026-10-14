import { cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, test } from "vitest";
import { page } from "vitest/browser";
import { expectNoAxeViolations } from "@/__tests__/pageObjects/accessibility";
import { DayTimeline, type DayTimelineEvent } from "./DayTimeline";
import { DayTimelineStory, LONG_ITEM_NAME } from "./DayTimeline.story";
import { DayTimelinePage } from "./pageObjects/DayTimelinePage";

const timeline = new DayTimelinePage();

afterEach(cleanup);

function bounds(locator: ReturnType<DayTimelinePage["popup"]>) {
  return locator.element().getBoundingClientRect();
}

describe("DayTimeline expanded cards", () => {
  test("fits the popup inside a narrow timeline scroller", async () => {
    render(<DayTimelineStory width={320} />);
    await timeline.open(LONG_ITEM_NAME);

    await expect
      .poll(() => {
        const scroller = bounds(timeline.scroller);
        const popup = bounds(timeline.popup("09:30–10:30"));
        return {
          left: popup.left >= scroller.left - 1,
          right: popup.right <= scroller.right + 1,
          width: popup.width <= scroller.width + 1,
        };
      })
      .toEqual({ left: true, right: true, width: true });
  });

  test("places the duration on the time line", async () => {
    render(<DayTimelineStory />);
    await timeline.open(LONG_ITEM_NAME);

    const popup = timeline.popup("09:30–10:30");
    const period = popup.getByRole("heading", { name: "09:30–10:30" });
    const duration = popup.getByText(/1 hour/);
    await expect.element(duration).toBeVisible();
    const periodBox = period.element().getBoundingClientRect();
    const durationBox = duration.element().getBoundingClientRect();
    expect(duration.element().parentElement).toBe(period.element().parentElement);
    expect(durationBox.left).toBeGreaterThanOrEqual(periodBox.right);
    expect(durationBox.top).toBeLessThan(periodBox.bottom);
  });

  test("keeps a sticky popup within the scroller when its trigger scrolls away", async () => {
    render(<DayTimelineStory />);
    await timeline.open(LONG_ITEM_NAME);
    const scroller = timeline.scroller.element() as HTMLElement;
    scroller.scrollLeft = scroller.scrollWidth;
    scroller.dispatchEvent(new Event("scroll"));

    await expect
      .poll(() => {
        const scrollerBounds = scroller.getBoundingClientRect();
        const popup = bounds(timeline.popup("09:30–10:30"));
        return popup.left >= scrollerBounds.left - 1 && popup.right <= scrollerBounds.right + 1;
      })
      .toBe(true);
  });

  test("keeps only one overlapping popup open and restores focus when it closes", async () => {
    render(<DayTimelineStory />);
    await timeline.open(LONG_ITEM_NAME);
    await expect.element(timeline.popup("09:30–10:30")).toBeVisible();

    await timeline.openWithKeyboard("Electron microscope · Grace Hopper");

    await expect.element(timeline.popup("09:30–10:30")).not.toBeInTheDocument();
    await expect.element(timeline.popup("09:45–10:45")).toBeVisible();

    await timeline.close("09:45–10:45", "Electron microscope · Grace Hopper");
    await expect.element(timeline.popup("09:45–10:45")).not.toBeInTheDocument();
    await expect.element(timeline.trigger("Electron microscope · Grace Hopper")).toHaveFocus();
  });

  test("shows instrument-local time on hover and keeps the booking card expandable", async () => {
    const startInstant = "2026-08-17T07:30:00Z";
    const endInstant = "2026-08-17T08:30:00Z";
    const event: DayTimelineEvent = {
      id: "cross-zone",
      kind: "booking",
      privacy: "full",
      title: "Cross-zone booking",
      bookedBy: "Ada Lovelace",
      item: { name: "Confocal microscope", globalId: "IN123" },
      canEdit: true,
      notes: "Cross-zone run",
      startMinute: 9 * 60 + 30,
      endMinute: 10 * 60 + 30,
      startInstant,
      endInstant,
      instrumentTimeZone: "America/New_York",
    };
    render(
      <DayTimeline
        date="2026-08-17"
        timezone="Europe/Berlin"
        events={[event]}
        startWindow={7 * 60}
        endWindow={12 * 60}
        showZoomControls={false}
        itemName="Test instruments"
      />,
    );

    const trigger = timeline.trigger("Cross-zone booking");
    await trigger.hover();
    const tooltip = page.getByRole("tooltip");
    await expect.element(tooltip).toBeVisible();
    const expectedInstrumentRange = new Intl.DateTimeFormat("en-US", {
      year: "numeric",
      month: "short",
      day: "numeric",
      hour: "numeric",
      minute: "2-digit",
      timeZoneName: "shortOffset",
      timeZone: "America/New_York",
    });
    await expect
      .element(tooltip)
      .toHaveTextContent(
        `${expectedInstrumentRange.format(new Date(startInstant))} – ${expectedInstrumentRange.format(new Date(endInstant))}`,
      );
    await expect.element(tooltip).toHaveTextContent("America/New_York");
    await expect.element(tooltip).not.toHaveTextContent("Cross-zone run");

    await trigger.click();
    const booking = timeline.popup("09:30–10:30");
    await expect.element(booking).toBeVisible();
    await expect.element(booking.getByText("Cross-zone run")).toBeVisible();
  });

  test("shows both offsets when an instrument booking crosses the repeated DST hour", async () => {
    const startInstant = "2026-11-01T05:30:00Z";
    const endInstant = "2026-11-01T06:30:00Z";
    const event: DayTimelineEvent = {
      id: "dst-booking",
      kind: "booking",
      privacy: "full",
      title: "DST booking",
      bookedBy: "Ada Lovelace",
      item: { name: "Confocal microscope", globalId: "IN123" },
      canEdit: false,
      startMinute: 6 * 60 + 30,
      endMinute: 7 * 60 + 30,
      startInstant,
      endInstant,
      instrumentTimeZone: "America/New_York",
    };
    render(
      <DayTimeline
        date="2026-11-01"
        timezone="Europe/Berlin"
        events={[event]}
        startWindow={6 * 60}
        endWindow={9 * 60}
        showZoomControls={false}
        itemName="Test instruments"
      />,
    );

    await timeline.trigger("DST booking").hover();
    const tooltip = page.getByRole("tooltip");
    const formatter = new Intl.DateTimeFormat("en-US", {
      year: "numeric",
      month: "short",
      day: "numeric",
      hour: "numeric",
      minute: "2-digit",
      timeZoneName: "shortOffset",
      timeZone: "America/New_York",
    });
    await expect.element(tooltip).toHaveTextContent(formatter.format(new Date(startInstant)));
    await expect.element(tooltip).toHaveTextContent(formatter.format(new Date(endInstant)));
    await expect.element(tooltip).toHaveTextContent("GMT-4");
    await expect.element(tooltip).toHaveTextContent("GMT-5");
    await expect.element(tooltip).toHaveTextContent("America/New_York");
  });

  test("closes on outside press and Escape while preserving event privacy rules", async () => {
    render(<DayTimelineStory />);
    await timeline.open(LONG_ITEM_NAME);
    const booking = timeline.popup("09:30–10:30");
    await expect.element(booking.getByText("Ada Lovelace")).toBeVisible();
    await expect.element(booking.getByText("Cell imaging with the 63x oil objective.")).toBeVisible();
    await expect.element(booking.getByRole("link", { name: "View details" })).toBeVisible();
    await expect.element(booking.getByRole("link", { name: "Edit" })).toBeVisible();

    await timeline.outsideButton.click();
    await expect.element(booking).not.toBeInTheDocument();
    await expect.element(timeline.trigger(LONG_ITEM_NAME)).toHaveFocus();

    await timeline.open("Scheduled maintenance");
    const blockout = timeline.popup("11:00–12:00");
    await expect.element(blockout.getByText("Laser alignment and inspection.")).toBeVisible();
    await expect.element(blockout.getByText("Booked by")).not.toBeInTheDocument();
    await expect.element(blockout.getByRole("link", { name: "View details" })).not.toBeInTheDocument();
    await timeline.pressEscape();
    await expect.element(blockout).not.toBeInTheDocument();
    await expect.element(timeline.trigger("Scheduled maintenance")).toHaveFocus();

    await timeline.open("Busy");
    await expect.element(page.getByText("Booked by")).not.toBeInTheDocument();
    await expect.element(page.getByText("Cell imaging with the 63x oil objective.")).not.toBeInTheDocument();
    await expect.element(page.getByText("Read-only booking purpose.")).not.toBeInTheDocument();
  });

  test("has no accessibility violations with an expanded event", async () => {
    render(<DayTimelineStory />);
    await timeline.open(LONG_ITEM_NAME);

    await expect.element(timeline.scroller).not.toHaveAttribute("aria-hidden");
    await expectNoAxeViolations();
  });
});
