import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { BookingInstrumentTimeTooltip } from "./BookingInstrumentTimeTooltip";

const originalTimeZone = process.env.TZ;

/** The zone the browser reports; Node applies a changed TZ to Intl straight away. */
function browserReports(timeZone: string) {
  process.env.TZ = timeZone;
  expect(Intl.DateTimeFormat().resolvedOptions().timeZone).toBe(timeZone);
}

function renderTooltip(displayTimeZone: string, instrumentTimeZone: string) {
  render(
    <BookingInstrumentTimeTooltip
      start="2026-08-17T08:00:00Z"
      end="2026-08-17T09:00:00Z"
      displayTimeZone={displayTimeZone}
      instrumentTimeZone={instrumentTimeZone}
    >
      {"10:00-11:00"}
    </BookingInstrumentTimeTooltip>,
  );
}

describe("BookingInstrumentTimeTooltip", () => {
  afterEach(() => {
    if (originalTimeZone === undefined) delete process.env.TZ;
    else process.env.TZ = originalTimeZone;
  });

  it.each([
    ["Asia/Calcutta", "Asia/Kolkata"],
    ["UTC", "Etc/UTC"],
  ])("offers no instrument time when a browser in %s views an item in its alias %s", (viewer, instrument) => {
    browserReports(viewer);
    renderTooltip(viewer, instrument);

    expect(screen.getByText("10:00-11:00")).toBeVisible();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("offers the instrument time when the item's zone differs from the viewer's", () => {
    browserReports("Asia/Calcutta");
    renderTooltip("Asia/Calcutta", "Europe/Berlin");

    expect(screen.getByRole("button", { name: "10:00-11:00" })).toBeVisible();
  });
});
