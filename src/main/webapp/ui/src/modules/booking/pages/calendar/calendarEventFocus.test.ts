import { describe, expect, it } from "vitest";
import { calendarEventFocusHref } from "./calendarEventFocus";

describe("calendarEventFocusHref", () => {
  it("uses the saved start date in the display timezone and clears calendar filters", () => {
    const href = calendarEventFocusHref({
      id: 41,
      start: "2026-08-17T22:30:00Z",
      targetGlobalId: "IN123",
      timeZone: "Europe/Berlin",
      searchStr:
        "?layout=agenda&view=week&calendar-resources.where=location%3D%3DIC1&calendar-resources.q=confocal&calendar-events.where=state%3D%3DCONFIRMED&mineOnly=true&myItemsOnly=true&keep=yes",
    });
    const url = new URL(href, "https://example.test");

    expect(url.pathname).toBe("/booking/calendar");
    expect(url.searchParams.get("date")).toBe("2026-08-18");
    expect(url.searchParams.get("target")).toBe("IN123");
    expect(url.searchParams.get("focus")).toBe("41");
    expect(url.searchParams.get("focusRequest")).toBeTruthy();
    expect(url.searchParams.get("layout")).toBe("agenda");
    expect(url.searchParams.get("view")).toBe("week");
    expect(url.searchParams.get("keep")).toBe("yes");
    for (const key of [
      "calendar-resources.where",
      "calendar-resources.q",
      "calendar-events.where",
      "mineOnly",
      "myItemsOnly",
    ]) {
      expect(url.searchParams.has(key)).toBe(false);
    }
  });

  it("creates a new marker for repeated focus requests", () => {
    const args = {
      id: 41,
      start: "2026-08-17T22:30:00Z",
      targetGlobalId: "IN123",
      timeZone: "Europe/Berlin",
      searchStr: "layout=agenda&view=week",
    };

    const first = new URL(calendarEventFocusHref(args), "https://example.test");
    const second = new URL(calendarEventFocusHref(args), "https://example.test");

    expect(first.searchParams.get("focusRequest")).not.toBe(second.searchParams.get("focusRequest"));
  });
});
