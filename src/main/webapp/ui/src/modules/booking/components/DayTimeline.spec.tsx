import { cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, test } from "vitest";
import { page } from "vitest/browser";
import { expectNoAxeViolations } from "@/__tests__/pageObjects/accessibility";
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

  test("uses its preferred width in a wide timeline scroller", async () => {
    render(<DayTimelineStory width={480} />);
    await timeline.open(LONG_ITEM_NAME);
    await expect.poll(() => Math.round(bounds(timeline.popup("09:30–10:30")).width)).toBe(352);
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

  test("uses the existing one-line item layout with the ID in the title row", async () => {
    render(<DayTimelineStory />);
    await timeline.open(LONG_ITEM_NAME);
    const popup = timeline.popup("09:30–10:30");
    const name = popup.getByText(LONG_ITEM_NAME).element();
    const id = popup.getByRole("link", { name: "Open inventory record IN123" }).element();
    const location = popup.getByRole("link", { name: "Imaging lab" }).element();

    expect(name.closest("[data-slot=item-title]")).toBe(id.closest("[data-slot=item-title]"));
    expect(location.closest("[data-slot=item-description]")).not.toBeNull();
    expect(getComputedStyle(name).whiteSpace).toBe("nowrap");
    expect(name.getBoundingClientRect().height).toBeLessThanOrEqual(
      Number.parseFloat(getComputedStyle(name).lineHeight) + 1,
    );
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
