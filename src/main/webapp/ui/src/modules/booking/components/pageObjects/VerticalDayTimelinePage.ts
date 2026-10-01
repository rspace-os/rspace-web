import { type Locator, page, userEvent } from "vitest/browser";

export class VerticalDayTimelinePage {
  get nextDay(): Locator {
    return page.getByRole("button", { name: "Next day" });
  }

  get previousDay(): Locator {
    return page.getByRole("button", { name: "Previous day" });
  }

  get dateHeading(): Locator {
    return page.getByRole("heading", { name: /Oct 25, 2026/ });
  }

  get endResizeHandle(): Locator {
    return page.getByRole("button", { name: /^Adjust draft end,/ });
  }

  get startResizeHandle(): Locator {
    return page.getByRole("button", { name: /^Adjust draft start,/ });
  }

  get moveHandle(): Locator {
    return page.getByRole("button", { name: /^Move draft booking,/ });
  }

  async moveDraftLaterByOneSlot(): Promise<void> {
    this.moveHandle.element().focus();
    await userEvent.keyboard("{ArrowDown}");
  }

  get eventDetailsSummary(): Locator {
    return page.getByText("Event details", { exact: true });
  }

  get occupancyMarks(): Locator {
    return page.getByTestId("vertical-day-timeline-occupancy");
  }

  get draft(): Locator {
    return page.getByTestId("vertical-day-timeline-draft");
  }

  get canvas(): Locator {
    return page.getByTestId("vertical-day-timeline-canvas");
  }
}
