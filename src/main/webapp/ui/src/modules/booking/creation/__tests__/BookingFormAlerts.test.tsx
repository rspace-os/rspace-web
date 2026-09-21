import { render, screen } from "@testing-library/react";
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
    render(<BookingFormAlerts error="Conflict" conflicts={[conflict]} />);

    const alert = screen.getByRole("alert");
    expect(alert).toHaveTextContent("Conflict");
    expect(alert).toHaveTextContent("Cell imaging");
    expect(alert).toHaveTextContent("10:00");
    expect(alert).toHaveClass("bg-red-100");
  });

  it("keeps allowed conflicts visible as warnings", () => {
    render(<BookingFormAlerts conflicts={[conflict]} conflictSeverity="warning" />);

    const alert = screen.getByRole("status");
    expect(alert).toHaveTextContent("Cell imaging");
    expect(alert).toHaveClass("bg-amber-100");
  });
});
