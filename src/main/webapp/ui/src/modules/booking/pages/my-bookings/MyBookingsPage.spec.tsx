import { createMemoryHistory, type RouterHistory } from "@tanstack/react-router";
import { cleanup, render } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, test } from "vitest";
import { type Locator, page } from "vitest/browser";
import { worker } from "@/__tests__/browserMocks";
import { bookableItemDetailsHandlers } from "../bookable-items/mocks/bookableItemsMocks";
import { currentUser } from "../calendar/calendarFixtures";
import { MyBookingsPageStory, myBookingsStoryUrl } from "./MyBookingsPage.story";
import { bookingHandlers, roleLostBooking, upcomingBooking } from "./mocks/bookingMocks";
import { MyBookingsPageObject } from "./pageObjects/MyBookingsPage";

const pageObj = new MyBookingsPageObject();

function clickWithoutDriverWait(locator: Locator) {
  const element = locator.element();
  if (!(element instanceof HTMLElement)) throw new TypeError("Expected an HTML element");
  element.click();
}

function registerHandlers() {
  worker.use(
    http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
    ...bookableItemDetailsHandlers(),
    ...bookingHandlers(),
    http.get("/api/v2/bookings/41", () => HttpResponse.json(upcomingBooking)),
  );
}

let history: RouterHistory;
let browserUrl: string;

beforeEach(() => {
  history = createMemoryHistory({ initialEntries: [myBookingsStoryUrl] });
  browserUrl = window.location.href;
  registerHandlers();
});

afterEach(() => {
  cleanup();
  expect(window.location.href).toBe(browserUrl);
});

describe("the My Bookings page", () => {
  test("keeps an inaccessible booking visible without item links or edit actions", async () => {
    worker.use(...bookingHandlers(undefined, undefined, [roleLostBooking]));
    render(<MyBookingsPageStory history={history} />);

    await expect.element(pageObj.unknownItem).toBeVisible();
    await expect.element(pageObj.roleLossNotice).toBeVisible();
    await expect.element(pageObj.confocalItemCalendar).not.toBeInTheDocument();
    await expect.element(pageObj.confocalEdit).not.toBeInTheDocument();
    await expect.element(pageObj.confocalMoreActions).not.toBeInTheDocument();
    await expect.element(pageObj.confocalDetails).toBeVisible();
  });

  test("searches booking purpose and instrument IDs without dropping requester scope", async () => {
    history.replace("/booking/my-bookings?period=upcoming");
    const listRequests: URL[] = [];
    worker.use(...bookingHandlers((url) => listRequests.push(url)));
    render(<MyBookingsPageStory history={history} />);

    const search = page.getByRole("textbox", { name: "Search Bookings" });
    await expect.element(search).toBeVisible();

    await search.fill("Scope training");
    await expect
      .poll(() => listRequests.map((url) => url.searchParams.get("where") ?? ""))
      .toEqual(expect.arrayContaining([expect.stringContaining("purpose")]));
    const purposeWhere = listRequests.find((url) => url.searchParams.get("where")?.includes("purpose=contains="));
    expect(purposeWhere?.searchParams.get("where")).toContain("Scope training");
    expect(purposeWhere?.searchParams.get("where")).toContain("requesterId==84");

    await search.fill("IN123");
    await expect
      .poll(() => listRequests.map((url) => url.searchParams.get("where") ?? ""))
      .toEqual(expect.arrayContaining([expect.stringContaining("target==IN123")]));
    const globalIdWhere = listRequests.find((url) => url.searchParams.get("where")?.includes("target==IN123"));
    expect(globalIdWhere?.searchParams.get("where")).toContain("requesterId==84");
  });

  test("shows a tooltip for every icon-only page control", async () => {
    render(<MyBookingsPageStory history={history} />);

    const controls = [
      [pageObj.confocalDetails, "View details"],
      [pageObj.confocalItemCalendar, "View item calendar"],
      [pageObj.confocalEdit, "Edit"],
      [pageObj.confocalMoreActions, "More actions"],
    ] as const;

    for (const [control, label] of controls) {
      await control.hover();
      await expect.element(pageObj.tooltip(label)).toBeVisible();
    }
  });

  test("shows the instrument-local start time and timezone without private booking details", async () => {
    const browserTimeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
    const instrumentTimeZone = browserTimeZone === "Pacific/Auckland" ? "Europe/Berlin" : "Pacific/Auckland";
    worker.use(...bookingHandlers(undefined, undefined, [{ ...upcomingBooking, timezone: instrumentTimeZone }]));
    render(<MyBookingsPageStory history={history} />);

    await pageObj.confocalStartDate.hover();

    const tooltip = page.getByRole("tooltip");
    await expect.element(tooltip).toBeVisible();
    const expectedInstrumentDateTime = new Intl.DateTimeFormat("en-US", {
      year: "numeric",
      month: "short",
      day: "numeric",
      hour: "numeric",
      minute: "2-digit",
      timeZoneName: "shortOffset",
      timeZone: instrumentTimeZone,
    }).format(new Date(upcomingBooking.start));
    await expect.element(tooltip).toHaveTextContent(expectedInstrumentDateTime);
    await expect.element(tooltip).toHaveTextContent(instrumentTimeZone);
    const tooltipText = tooltip.element().textContent ?? "";
    expect(tooltipText).not.toContain("Scope training");
    expect(tooltipText).not.toContain("Test User");
  });

  test("does not show an instrument-time tooltip when its timezone is the browser timezone or unknown", async () => {
    const browserTimeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;

    for (const timezone of [browserTimeZone, null]) {
      cleanup();
      worker.use(...bookingHandlers(undefined, undefined, [{ ...upcomingBooking, timezone }]));
      render(<MyBookingsPageStory history={history} />);

      await pageObj.confocalStartDate.hover();
      await expect.element(page.getByRole("tooltip")).not.toBeInTheDocument();
    }
  });

  test("uses the same appearance for every booking action", async () => {
    render(<MyBookingsPageStory history={history} />);

    const controls = [
      pageObj.confocalDetails,
      pageObj.confocalItemCalendar,
      pageObj.confocalEdit,
      pageObj.confocalMoreActions,
    ];
    await expect.element(controls[0]).toBeVisible();
    const styles = controls.map((control) => {
      const element = control.element();
      expect(element).toHaveClass("border-border", "bg-background");
      const style = getComputedStyle(element);
      return {
        border: style.border,
        borderRadius: style.borderRadius,
        color: style.color,
        height: style.height,
        width: style.width,
      };
    });

    expect(styles).toEqual(controls.map(() => styles[0]));
  });

  test("keeps the calendar file and cancel behind More actions with text labels", async () => {
    render(<MyBookingsPageStory history={history} />);

    await pageObj.confocalMoreActions.click();
    await expect.element(pageObj.calendarFileMenuItem).toBeVisible();
    await expect.element(pageObj.cancelMenuItem).toBeVisible();

    await pageObj.cancelMenuItem.click();
    await expect.element(pageObj.cancelDialog).toBeVisible();
    await pageObj.keepBooking.click();
    await expect.element(pageObj.cancelDialog).not.toBeInTheDocument();
    await expect.element(pageObj.confocalMoreActions).toHaveFocus();
  });

  test("reports each cancellation above the table, restores one on Undo and dismisses the other", async () => {
    const electron = {
      ...upcomingBooking,
      id: 42,
      target: {
        ...upcomingBooking.target,
        globalId: "IN124",
        value: { ...upcomingBooking.target.value, id: 124, name: "Electron microscope" },
      },
    };
    const docs: Array<Record<string, unknown>> = [{ ...upcomingBooking }, electron];
    const patches: Array<{ id: string; body: { state: string; cancellationReason?: string }; ifMatch: string | null }> =
      [];
    worker.use(
      ...bookingHandlers(undefined, undefined, docs),
      http.patch("/api/v2/bookings/:id", async ({ params, request }) => {
        const body = (await request.json()) as { state: string; cancellationReason?: string };
        patches.push({ id: String(params.id), body, ifMatch: request.headers.get("If-Match") });
        const index = docs.findIndex((doc) => String(doc.id) === String(params.id));
        docs[index] = {
          ...docs[index],
          state: body.state,
          version: Number(docs[index].version ?? 0) + 1,
          cancellationReason: body.cancellationReason ?? null,
        };
        return HttpResponse.json(docs[index]);
      }),
    );
    render(<MyBookingsPageStory history={history} />);
    const alerts = page.getByRole("list", { name: "Recent changes" });
    const confocalAlert = alerts.getByRole("listitem", { name: /^Cancelled Confocal microscope, / });
    const electronAlert = alerts.getByRole("listitem", { name: /^Cancelled Electron microscope, / });

    await pageObj.confocalMoreActions.click();
    await pageObj.cancelMenuItem.click();
    await page.getByRole("textbox", { name: "Cancellation reason (optional)" }).fill("Instrument needs recalibration");
    await pageObj.cancelDialog.getByRole("button", { name: "Cancel booking" }).click();

    await expect.element(page.getByText("Confocal microscope", { exact: true })).not.toBeInTheDocument();
    await expect.element(confocalAlert).toHaveFocus();
    expect(patches[0]).toEqual({
      id: "41",
      body: { state: "CANCELLED", cancellationReason: "Instrument needs recalibration" },
      ifMatch: '"0"',
    });

    // Electron's row is now the first with More actions.
    await pageObj.confocalMoreActions.click();
    await pageObj.cancelMenuItem.click();
    await pageObj.cancelDialog.getByRole("button", { name: "Cancel booking" }).click();

    await expect.element(electronAlert).toHaveFocus();
    await expect
      .poll(() =>
        alerts
          .getByRole("listitem")
          .elements()
          .map((item) => item.getAttribute("data-table-list-alert")),
      )
      .toEqual(["booking-cancelled-42", "booking-cancelled-41"]);

    await confocalAlert.getByRole("button", { name: "Undo" }).click();

    // Both presentations render the row again; CSS shows one.
    await expect
      .poll(() => page.getByText("Confocal microscope", { exact: true }).elements().length)
      .toBeGreaterThan(0);
    expect(patches[2]).toEqual({ id: "41", body: { state: "CONFIRMED" }, ifMatch: '"1"' });
    await expect
      .poll(() =>
        document.activeElement?.closest("[data-table-list-row-actions]")?.getAttribute("data-table-list-row-actions"),
      )
      .toBe("41");
    await expect.element(confocalAlert).not.toBeInTheDocument();

    await electronAlert.getByRole("button", { name: "Dismiss" }).click();

    await expect.element(alerts).not.toBeInTheDocument();
    await expect.poll(() => document.activeElement?.hasAttribute("data-table-list-filters")).toBe(true);
  });

  test("keeps the booking actions on one line", async () => {
    render(<MyBookingsPageStory history={history} />);

    await expect.element(pageObj.confocalMoreActions).toBeVisible();
    const controls = [
      pageObj.confocalItemCalendar,
      pageObj.confocalDetails,
      pageObj.confocalEdit,
      pageObj.confocalMoreActions,
    ];
    const tops = controls.map((control) => control.element().getBoundingClientRect().top);
    expect(tops).toEqual(controls.map(() => tops[0]));
  });

  test("shows the upcoming count inside its period button", async () => {
    render(<MyBookingsPageStory history={history} />);

    await expect.element(pageObj.upcomingCount).toBeVisible();
    const buttonRect = pageObj.upcoming.element().getBoundingClientRect();
    const badgeRect = pageObj.upcomingCount.element().getBoundingClientRect();

    expect(badgeRect.left).toBeGreaterThanOrEqual(buttonRect.left);
    expect(badgeRect.top).toBeGreaterThanOrEqual(buttonRect.top);
    expect(badgeRect.right).toBeLessThanOrEqual(buttonRect.right);
    expect(badgeRect.bottom).toBeLessThanOrEqual(buttonRect.bottom);
  });

  test("navigates from a booking row to the bookable item details page", async () => {
    render(<MyBookingsPageStory history={history} />);

    await expect.element(pageObj.confocalItemCalendar).toBeVisible();
    clickWithoutDriverWait(pageObj.confocalItemCalendar);

    await expect.element(pageObj.bookableItemDetailsHeading).toBeVisible();
    await expect.element(pageObj.bookableItemDetailsTarget).toBeVisible();
    await expect.poll(() => history.location.pathname).toBe("/booking/bookable-items/IN123");
  });

  test("navigates from View details to the booking event", async () => {
    render(<MyBookingsPageStory history={history} />);

    await expect.element(pageObj.confocalDetails).toBeVisible();
    clickWithoutDriverWait(pageObj.confocalDetails);

    await expect.poll(() => history.location.pathname).toBe("/booking/calendar/bookings/41");
    await expect.element(pageObj.bookableItemDetailsHeading).toBeVisible();
  });
});
