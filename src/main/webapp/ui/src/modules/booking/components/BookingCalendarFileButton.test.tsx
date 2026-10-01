import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";

vi.unmock("react-i18next");

const { renderWithRealI18n } = await import("@/__tests__/helpers/realI18n");
const { BookingCalendarFileButton } = await import("./BookingCalendarFileButton");

const resources = {
  booking: {
    calendar: {
      file: {
        accessibleLabel: ".ics file for {item}, {period}",
        downloaded: "Downloaded {filename}. No subscription was created or changed.",
        failed: "The calendar file for {item}, {period} could not be downloaded. Nothing was saved; try again.",
        label: ".ics file",
        preparing: "Preparing the calendar file for {item}, {period}.",
      },
    },
  },
};

const PATH = "/api/v2/bookings/12/calendar-file";
const CALENDAR = "BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n";

let saved: { url: string; download: string } | null = null;
let revoked: string[] = [];

beforeEach(() => {
  saved = null;
  revoked = [];
  vi.spyOn(URL, "createObjectURL").mockReturnValue("blob:calendar");
  vi.spyOn(URL, "revokeObjectURL").mockImplementation((url) => revoked.push(url));
  vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (this: HTMLAnchorElement) {
    saved = { url: this.href, download: this.download };
  });
});

afterEach(() => {
  vi.restoreAllMocks();
});

function renderButton(iconOnly = false) {
  return renderWithRealI18n(
    <BookingCalendarFileButton
      bookingId={12}
      itemName="Confocal microscope"
      period="12 Sep 2026, 10:00–12:00"
      token="token"
      iconOnly={iconOnly}
    />,
    { resources, defaultNS: "booking" },
  );
}

describe("BookingCalendarFileButton", () => {
  it("keeps its download action when rendered as an icon", async () => {
    server.use(
      http.get(PATH, () =>
        HttpResponse.text(CALENDAR, {
          headers: {
            "Content-Type": "text/calendar;charset=UTF-8",
            "Content-Disposition": 'attachment; filename="confocal-microscope.ics"',
          },
        }),
      ),
    );
    await renderButton(true);

    const button = screen.getByRole("button", {
      name: ".ics file for Confocal microscope, 12 Sep 2026, 10:00–12:00",
    });
    expect(button).not.toHaveTextContent(".ics file");
    await userEvent.click(button);

    expect(await screen.findByRole("status")).toHaveTextContent("Downloaded confocal-microscope.ics");
  });

  it("saves the file the server named and says no subscription changed", async () => {
    server.use(
      http.get(PATH, () =>
        HttpResponse.text(CALENDAR, {
          headers: {
            "Content-Type": "text/calendar;charset=UTF-8",
            "Content-Disposition": 'attachment; filename="confocal-microscope-IN9-2026-09-12.ics"',
          },
        }),
      ),
    );
    await renderButton();

    await userEvent.click(
      screen.getByRole("button", { name: ".ics file for Confocal microscope, 12 Sep 2026, 10:00–12:00" }),
    );

    expect(await screen.findByRole("status")).toHaveTextContent(
      "Downloaded confocal-microscope-IN9-2026-09-12.ics. No subscription was created or changed.",
    );
    expect(saved).toEqual({ url: "blob:calendar", download: "confocal-microscope-IN9-2026-09-12.ics" });
    expect(revoked).toEqual(["blob:calendar"]);
  });

  it("reports a failure without saving anything", async () => {
    server.use(http.get(PATH, () => new HttpResponse(null, { status: 404 })));
    await renderButton();

    await userEvent.click(screen.getByRole("button", { name: /\.ics file for/ }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "The calendar file for Confocal microscope, 12 Sep 2026, 10:00–12:00 could not be downloaded.",
    );
    expect(saved).toBeNull();
  });
});
