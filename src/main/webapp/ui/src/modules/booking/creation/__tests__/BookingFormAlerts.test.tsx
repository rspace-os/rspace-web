import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { BookingFormAlerts } from "../BookingFormAlerts";

const conflict = {
  id: 42,
  kind: "BOOKING" as const,
  privacy: "full" as const,
  purpose: "Cell imaging",
  bookedBy: "Ada Lovelace",
  start: "2026-08-17T10:00:00Z",
  end: "2026-08-17T11:00:00Z",
  timezone: "UTC",
};

describe("BookingFormAlerts", () => {
  it("lists conflicting bookings in a blocking alert", () => {
    render(<BookingFormAlerts error="Conflict" conflicts={[conflict]} displayTimezone="UTC" />);

    const alert = screen.getByRole("alert");
    expect(alert).toHaveTextContent("Conflict");
    expect(alert).toHaveTextContent("Cell imaging");
    expect(alert).toHaveTextContent("10:00");
    expect(alert).toHaveClass("bg-red-100");
  });

  it("keeps allowed conflicts visible as warnings", () => {
    render(<BookingFormAlerts conflicts={[conflict]} displayTimezone="UTC" conflictSeverity="warning" />);

    const alert = screen.getByRole("status");
    expect(alert).toHaveTextContent("Cell imaging");
    expect(alert).toHaveClass("bg-amber-100");
  });

  it("lists buffer-only conflicts as within the buffer rather than as overlaps", () => {
    render(
      <BookingFormAlerts
        conflicts={[
          { ...conflict, id: 43, purpose: null, bufferOnly: true },
          { ...conflict, bufferOnly: false },
        ]}
        displayTimezone="UTC"
      />,
    );

    const alert = screen.getByRole("alert");
    const [overlaps, buffered] = within(alert).getAllByRole("list");
    expect(alert).toHaveTextContent(
      /booking:bookings\.errors\.overlapSummary.*booking:bookings\.errors\.bufferSummary/,
    );
    expect(overlaps).toHaveTextContent("Cell imaging");
    expect(buffered).toHaveTextContent("booking:bookings.errors.overlapBooking");
    expect(buffered).not.toHaveTextContent("Cell imaging");
  });

  it("omits the overlap heading when every conflict is buffer-only", () => {
    render(<BookingFormAlerts conflicts={[{ ...conflict, bufferOnly: true }]} displayTimezone="UTC" />);

    const alert = screen.getByRole("alert");
    expect(alert).toHaveTextContent("booking:bookings.errors.bufferSummary");
    expect(alert).not.toHaveTextContent("booking:bookings.errors.overlapSummary");
  });

  it("formats conflicts in the booking form's display timezone", () => {
    render(<BookingFormAlerts conflicts={[conflict]} displayTimezone="Europe/Berlin" />);

    const alert = screen.getByRole("alert");
    expect(alert).toHaveTextContent("12:00");
    expect(alert).not.toHaveTextContent("10:00");
  });
});
