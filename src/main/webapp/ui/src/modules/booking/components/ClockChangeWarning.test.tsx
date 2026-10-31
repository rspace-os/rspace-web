import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { createRealI18nWrapper, renderWithRealI18n } from "@/__tests__/helpers/realI18n";
import bookingEnglish from "@/modules/common/i18n/locales/en-US/booking.json";
import { ClockChangeWarning } from "./ClockChangeWarning";

function renderWarning(props: Parameters<typeof ClockChangeWarning>[0]) {
  return renderWithRealI18n(<ClockChangeWarning {...props} />, {
    resources: { booking: { bookings: { clockChange: bookingEnglish.bookings.clockChange } } },
    defaultNS: "booking",
  });
}

describe("ClockChangeWarning", () => {
  it("describes calendar days against elapsed hours across the spring change", async () => {
    // Saturday noon to Monday noon in Berlin, with the clocks going forward on Sunday.
    await renderWarning({
      start: "2026-03-28T11:00:00Z",
      end: "2026-03-30T10:00:00Z",
      timezone: "Europe/Berlin",
      kind: "BOOKING",
    });

    expect(screen.getByRole("note")).toMatchTextContent(
      "This booking crosses a daylight-saving change in Europe/Berlin. It spans 2 calendar days but lasts 47 hours.",
    );
  });

  it("gives maintenance its local range and elapsed duration", async () => {
    await renderWarning({
      start: "2026-03-29T00:30:00Z",
      end: "2026-03-29T02:00:00Z",
      timezone: "Europe/Berlin",
      kind: "MAINTENANCE",
    });

    expect(screen.getByRole("note")).toMatchTextContent(
      /^This maintenance event crosses a daylight-saving change in Europe\/Berlin\. It runs from .+ to .+ local time and lasts 1 hour(,| and)? 30 minutes\.$/,
    );
  });

  it("uses neutral wording for more than one change, even with equal offsets at both ends", async () => {
    await renderWarning({
      start: "2026-03-01T12:00:00Z",
      end: "2026-11-01T12:00:00Z",
      timezone: "Europe/Berlin",
      kind: "BOOKING",
    });

    expect(screen.getByRole("note")).toMatchTextContent("crosses 2 clock changes in Europe/Berlin");
  });

  it("announces a changed interval through a polite live region", async () => {
    await renderWarning({
      start: "2026-10-24T10:00:00Z",
      end: "2026-10-25T11:00:00Z",
      timezone: "Europe/Berlin",
      kind: "BOOKING",
    });

    const message =
      "This booking crosses a daylight-saving change in Europe/Berlin. It spans 1 calendar day but lasts 25 hours.";
    expect(screen.getByRole("note")).toMatchTextContent(message);
    // Set after mount, so the region changes and is announced rather than holding its first content silently.
    expect(screen.getByText(message, { selector: "[aria-live='polite']" })).toHaveAttribute("aria-atomic", "true");
  });

  it("announces a moved interval even when the sentence stays the same", async () => {
    const props = { timezone: "Europe/Berlin", kind: "BOOKING" as const };
    const wrapper = await createRealI18nWrapper({
      resources: { booking: { bookings: { clockChange: bookingEnglish.bookings.clockChange } } },
      defaultNS: "booking",
    });
    const { rerender } = render(
      <ClockChangeWarning {...props} start="2026-03-28T11:00:00Z" end="2026-03-30T10:00:00Z" />,
      { wrapper },
    );
    const live = document.querySelector("[aria-live='polite']");
    const first = live?.textContent;

    rerender(<ClockChangeWarning {...props} start="2026-03-28T12:00:00Z" end="2026-03-30T11:00:00Z" />);

    expect(live?.textContent).not.toBe(first);
    expect(live?.textContent?.trim()).toBe(first?.trim());
  });

  it.each([
    ["without a visible scheduling timezone", null, "2026-03-28T11:00:00Z", "2026-03-30T10:00:00Z"],
    ["without a clock change", "Europe/Berlin", "2026-08-17T09:00:00Z", "2026-08-19T09:00:00Z"],
  ])("shows nothing %s", async (_, timezone, start, end) => {
    await renderWarning({ start, end, timezone, kind: "BOOKING" });

    expect(screen.queryByRole("note")).not.toBeInTheDocument();
  });
});
