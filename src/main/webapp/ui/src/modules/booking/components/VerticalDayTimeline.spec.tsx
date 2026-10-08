import { cleanup, render } from "@testing-library/react";
import * as React from "react";
import { afterEach, describe, expect, test } from "vitest";
import { page, userEvent } from "vitest/browser";
import type { BookingWindowDraft } from "@/modules/booking/domain/bookingTime";
import { VerticalDayTimelinePage } from "./pageObjects/VerticalDayTimelinePage";
import { VerticalDayTimelineStory } from "./VerticalDayTimeline.story";

const timeline = new VerticalDayTimelinePage();

function InitiallyHiddenTimelineStory() {
  const [visible, setVisible] = React.useState(false);
  return (
    <>
      <button type="button" aria-label="Show timeline" onClick={() => setVisible(true)}>
        {"Show timeline"}
      </button>
      <div hidden={!visible}>
        <VerticalDayTimelineStory
          initialDraft={{
            startDate: "2026-10-25",
            startTime: "15:00",
            startOccurrence: "later",
            endDate: "2026-10-25",
            endTime: "16:00",
          }}
        />
      </div>
    </>
  );
}

afterEach(cleanup);

describe("VerticalDayTimeline", () => {
  test("moves a one-hour draft by keyboard, keeps both repeated-hour labels, and shows resize handles", async () => {
    render(<VerticalDayTimelineStory />);

    await expect.element(timeline.canvas).toBeVisible();
    await expect.element(page.getByText("Europe/Berlin · Confocal microscope", { exact: true })).toBeVisible();
    expect(page.getByRole("button", { name: "Day", exact: true }).query()).toBeNull();
    expect(page.getByRole("button", { name: "List", exact: true }).query()).toBeNull();
    expect(page.getByRole("list").elements()).toHaveLength(1);
    await expect.element(timeline.moveHandle).toBeVisible();
    await expect.element(timeline.endResizeHandle).toBeVisible();
    expect(timeline.moveHandle.element().getBoundingClientRect().height).toBeGreaterThanOrEqual(24);
    expect(timeline.endResizeHandle.element().getBoundingClientRect().height).toBeCloseTo(12);
    await expect.element(page.getByText("02:00 +02:00", { exact: true })).toBeVisible();
    await expect.element(page.getByText("02:00 +01:00", { exact: true })).toBeVisible();

    await timeline.moveDraftLaterByOneSlot();

    await expect.element(timeline.draft).toHaveTextContent("02:05 +01:00–03:05 +01:00");
  });

  test("uses controlled date navigation and provides a return to the draft day", async () => {
    render(<VerticalDayTimelineStory />);
    await expect.element(timeline.canvas).toHaveAttribute("data-timeline-date", "2026-10-25");

    await timeline.nextDay.click();

    await expect.element(timeline.canvas).toHaveAttribute("data-timeline-date", "2026-10-26");
    await expect.element(page.getByRole("button", { name: "Draft day" })).toBeVisible();
    await page.getByRole("button", { name: "Draft day" }).click();

    await expect.element(timeline.canvas).toHaveAttribute("data-timeline-date", "2026-10-25");
  });

  test("keeps the mobile date visible and centered between day navigation buttons", async () => {
    const originalViewport = { width: window.innerWidth, height: window.innerHeight };
    await page.viewport(320, 800);
    try {
      render(<VerticalDayTimelineStory />);

      await expect.element(timeline.dateHeading).toBeVisible();
      const heading = timeline.dateHeading.element();
      expect(heading.scrollWidth).toBeLessThanOrEqual(heading.clientWidth);
      const dateCenter = heading.getBoundingClientRect().left + heading.getBoundingClientRect().width / 2;
      const previousBounds = timeline.previousDay.element().getBoundingClientRect();
      const nextBounds = timeline.nextDay.element().getBoundingClientRect();
      expect(dateCenter).toBeGreaterThan(previousBounds.left + previousBounds.width / 2);
      expect(dateCenter).toBeLessThan(nextBounds.left + nextBounds.width / 2);
    } finally {
      await page.viewport(originalViewport.width, originalViewport.height);
    }
  });

  test("scrolls the draft into view when a hidden timeline becomes visible", async () => {
    render(<InitiallyHiddenTimelineStory />);

    await expect.element(timeline.draft).not.toBeVisible();
    await page.getByRole("button", { name: "Show timeline" }).click();
    await expect.element(timeline.draft).toBeVisible();

    const scroller = page.getByTestId("vertical-day-timeline-scroller");
    await expect
      .poll(() => {
        const draftBounds = timeline.draft.element().getBoundingClientRect();
        const scrollerBounds = scroller.element().getBoundingClientRect();
        return draftBounds.top >= scrollerBounds.top && draftBounds.bottom <= scrollerBounds.bottom;
      })
      .toBe(true);
  });

  test("omits canvas handles for short drafts and reveals short event details on demand", async () => {
    render(
      <VerticalDayTimelineStory
        initialDraft={{
          startDate: "2026-10-25",
          startTime: "10:00",
          startOccurrence: "later",
          endDate: "2026-10-25",
          endTime: "10:05",
        }}
      />,
    );

    await expect.element(timeline.canvas).toBeVisible();
    expect(timeline.moveHandle.query()).toBeNull();
    expect(timeline.startResizeHandle.query()).toBeNull();
    expect(timeline.endResizeHandle.query()).toBeNull();
    const occupancyEventIds = timeline.occupancyMarks
      .elements()
      .map((mark) => mark.parentElement?.getAttribute("data-event-id"));
    expect(occupancyEventIds).toEqual(expect.arrayContaining(["busy-short", "short-full"]));

    const fullEvent = page.getByRole("article", {
      name: /Flow cytometer · Katherine Johnson/,
      includeHidden: true,
    });
    await expect.element(fullEvent).not.toBeVisible();
    await timeline.eventDetailsSummary.click();

    await expect.element(fullEvent).toBeVisible();
    const busyEvent = page.getByRole("article", { name: /Busy/ });
    await expect.element(busyEvent).toBeVisible();
    await expect.element(busyEvent).not.toHaveTextContent("Katherine Johnson");
  });

  test("leaves a stationary edge press unchanged and resizes by the pointer delta", async () => {
    const initialDraft: BookingWindowDraft = {
      startDate: "2026-10-25",
      startTime: "02:00",
      startOccurrence: "later",
      endDate: "2026-10-25",
      endTime: "04:00",
    };
    render(<VerticalDayTimelineStory initialDraft={initialDraft} />);

    await expect.element(timeline.endResizeHandle).toBeVisible();
    await timeline.endResizeHandle.click();
    await expect.element(timeline.draft).toHaveTextContent("02:00 +01:00–04:00 +01:00");

    const canvasBounds = timeline.canvas.element().getBoundingClientRect();
    const handleBounds = timeline.endResizeHandle.element().getBoundingClientRect();
    const sourcePosition = { x: 20, y: handleBounds.height / 2 };
    const sourceInCanvas = handleBounds.top + sourcePosition.y - canvasBounds.top;
    const fiveMinuteDelta = (canvasBounds.height / 1500) * 5;
    const targetPosition = { x: 20, y: sourceInCanvas + fiveMinuteDelta };
    expect(canvasBounds.top + targetPosition.y - (handleBounds.top + sourcePosition.y)).toBeCloseTo(fiveMinuteDelta);
    await userEvent.dragAndDrop(timeline.endResizeHandle, timeline.canvas, {
      sourcePosition,
      targetPosition,
    });

    await expect.element(timeline.draft).toHaveTextContent("02:00 +01:00–04:05 +01:00");
  });

  test("continues a native drag through overlapping bookings with a full-width draft", async () => {
    render(
      <VerticalDayTimelineStory
        initialDraft={{
          startDate: "2026-10-25",
          startTime: "07:00",
          startOccurrence: "later",
          endDate: "2026-10-25",
          endTime: "08:00",
        }}
      />,
    );

    const adaBooking = page.getByRole("article", { name: /Confocal microscope · Ada Lovelace/ });
    const graceBooking = page.getByRole("article", { name: /Confocal microscope · Grace Hopper/ });
    await expect.element(adaBooking).toBeVisible();
    await expect.element(graceBooking).toBeVisible();
    const adaBounds = adaBooking.element().getBoundingClientRect();
    const graceBounds = graceBooking.element().getBoundingClientRect();
    expect(adaBounds.left).toBeLessThan(graceBounds.left);
    expect(adaBounds.right).toBeLessThanOrEqual(graceBounds.left + 1);

    const draftElement = timeline.draft.element();
    const canvasBounds = timeline.canvas.element().getBoundingClientRect();
    const draftBounds = draftElement.getBoundingClientRect();
    const backgroundColor = getComputedStyle(draftElement).backgroundColor;
    const dayPixelsPerMinute = canvasBounds.height / 1500;
    expect(draftBounds.width).toBeGreaterThanOrEqual(canvasBounds.width * 0.9);

    const observedDraftStates: Array<{ backgroundColor: string; width: number }> = [];
    const observer = new MutationObserver(() => {
      const bounds = draftElement.getBoundingClientRect();
      observedDraftStates.push({
        backgroundColor: getComputedStyle(draftElement).backgroundColor,
        width: bounds.width,
      });
    });
    observer.observe(draftElement, { attributes: true, attributeFilter: ["class", "style"] });

    const dragDraftBy = async (deltaMinutes: number, steps: number) => {
      const moveHandle = timeline.moveHandle;
      const moveHandleBounds = moveHandle.element().getBoundingClientRect();
      const currentCanvasBounds = timeline.canvas.element().getBoundingClientRect();
      await userEvent.dragAndDrop(moveHandle, timeline.canvas, {
        sourcePosition: { x: moveHandleBounds.width / 2, y: moveHandleBounds.height / 2 },
        targetPosition: {
          x: currentCanvasBounds.width / 2,
          y:
            moveHandleBounds.top -
            currentCanvasBounds.top +
            moveHandleBounds.height / 2 +
            dayPixelsPerMinute * deltaMinutes,
        },
        steps,
      });
    };

    try {
      await dragDraftBy(120, 12);
      await expect.element(timeline.draft).toHaveTextContent("09:00 +01:00–10:00 +01:00");

      await dragDraftBy(5, 3);
    } finally {
      observer.disconnect();
    }

    await expect.element(timeline.draft).toHaveTextContent("09:05 +01:00–10:05 +01:00");
    const movedBounds = timeline.draft.element().getBoundingClientRect();
    expect(movedBounds.width).toBeGreaterThanOrEqual(canvasBounds.width * 0.9);
    expect(getComputedStyle(timeline.draft.element()).backgroundColor).toBe(backgroundColor);
    expect(observedDraftStates.length).toBeGreaterThan(0);
    for (const state of observedDraftStates) {
      expect(state.backgroundColor).toBe(backgroundColor);
      expect(state.width).toBeCloseTo(draftBounds.width);
    }
  });
});
