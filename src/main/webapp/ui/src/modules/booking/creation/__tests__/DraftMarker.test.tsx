import { render, screen } from "@testing-library/react";
import { expect, it } from "vitest";
import { DraftMarker } from "../CompactBookingCreationDialog";

it("retains the elapsed width of a booking across the repeated hour", () => {
  render(
    <section id="timeline">
      <div data-testid="day-timeline-canvas" />
    </section>,
  );
  render(
    <DraftMarker
      creation={{ ownerId: "test", triggerId: "timeline", initialDate: "2026-10-25", eventKind: "BOOKING" }}
      timeZone="Europe/Berlin"
      draft={{
        startDate: "2026-10-25",
        startTime: "02:45",
        startOccurrence: "earlier",
        endDate: "2026-10-25",
        endTime: "02:15",
        endOccurrence: "later",
      }}
    />,
  );
  expect(screen.getByTestId("compact-booking-draft-marker")).toHaveStyle({ left: "11%", width: "2%" });
});
