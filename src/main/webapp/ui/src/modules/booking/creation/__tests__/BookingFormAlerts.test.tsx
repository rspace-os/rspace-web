import { beforeAll as beforeBookingCatalog } from "vitest";
import appI18n from "@/modules/common/i18n";

beforeBookingCatalog(async () => {
  appI18n.addResourceBundle(
    "en-US",
    "booking",
    (await import("@/modules/common/i18n/locales/en-US/booking.json")).default,
  );
});

import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { setupRealAppI18n } from "@/__tests__/helpers/realI18n";
import { BookingFormAlerts } from "../BookingFormAlerts";

// Each conflict is one translated label-and-period message, which cimode would reduce to its key.
setupRealAppI18n();

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
    expect(alert).toMatchTextContent("Conflict");
    expect(within(alert).getByRole("listitem")).toMatchTextContent(/^Cell imaging · 10:00/);
    expect(alert).toHaveClass("bg-red-100");
  });

  it("names a maintenance conflict without a purpose by its number", () => {
    render(
      <BookingFormAlerts
        conflicts={[{ ...conflict, id: 60, kind: "MAINTENANCE", purpose: null }]}
        displayTimezone="UTC"
      />,
    );

    expect(within(screen.getByRole("alert")).getByRole("listitem")).toMatchTextContent(
      /^Maintenance: Maintenance event #60 · 10:00/,
    );
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
    expect(alert).toMatchTextContent(/This period overlaps:.*This period is within the buffer time of:/);
    expect(overlaps).toMatchTextContent("Cell imaging");
    expect(buffered).toMatchTextContent("Booking #43");
    expect(buffered).not.toMatchTextContent("Cell imaging");
  });

  it("omits the overlap heading when every conflict is buffer-only", () => {
    render(<BookingFormAlerts conflicts={[{ ...conflict, bufferOnly: true }]} displayTimezone="UTC" />);

    const alert = screen.getByRole("alert");
    expect(alert).toMatchTextContent("This period is within the buffer time of:");
    expect(alert).not.toMatchTextContent("This period overlaps:");
  });

  it("keeps allowed conflicts visible as warnings", () => {
    render(<BookingFormAlerts conflicts={[conflict]} displayTimezone="UTC" conflictSeverity="warning" />);
    const alert = screen.getByRole("status");
    expect(alert).toMatchTextContent("Cell imaging");
    expect(alert).toHaveClass("bg-amber-100");
  });

  it("formats conflicts in the booking form's display timezone", () => {
    render(<BookingFormAlerts conflicts={[conflict]} displayTimezone="Europe/Berlin" />);

    const alert = screen.getByRole("alert");
    expect(alert).toMatchTextContent("12:00");
    expect(alert).not.toMatchTextContent("10:00");
  });
});
