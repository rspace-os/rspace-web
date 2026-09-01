import { cleanup, render } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { page, userEvent } from "vitest/browser";
import { worker } from "@/__tests__/browserSetup";
import {
  emulateForcedColors,
  emulateReducedMotion,
  expectNoAxeViolations,
} from "@/__tests__/pageObjects/accessibility";
import { BookableItemPageStory } from "./BookableItemPage.story";
import { bookableItemDetailsHandlers, bookableItemFixtures, bookerBookingAccess } from "./mocks/bookableItemsMocks";
import { BookableItemPage } from "./pageObjects/BookableItemPage";

const pageObj = new BookableItemPage();
const snapshotFingerprint = "b".repeat(64);
const auditEvent = {
  eventId: "a".repeat(64),
  timestamp: "2026-08-25T10:42:18Z",
  username: "ada",
  fullName: "Ada Lovelace",
  domain: "RECORD",
  action: "WRITE",
  description: "Updated booking configuration IN123",
  payload: { maxBookingDurationMinutes: 60 },
};

function auditPage(pageNumber = 1, totalPages = 1) {
  return {
    docs: [auditEvent],
    totalDocs: totalPages * 20,
    limit: 20,
    page: pageNumber,
    pagingCounter: (pageNumber - 1) * 20 + 1,
    totalPages,
    hasPrevPage: pageNumber > 1,
    hasNextPage: pageNumber < totalPages,
    prevPage: pageNumber > 1 ? pageNumber - 1 : null,
    nextPage: pageNumber < totalPages ? pageNumber + 1 : null,
    snapshotDate: "2026-08-25",
    snapshotFingerprint,
  };
}

function registerHandlers() {
  worker.use(
    ...bookableItemDetailsHandlers(),
    http.patch("/api/v2/booking-configurations/7", () => new HttpResponse(null, { status: 204 })),
  );
}

function useBookerConfiguration() {
  worker.use(
    http.get("/api/v2/booking-configurations", ({ request }) => {
      const where = new URL(request.url).searchParams.get("where") ?? "";
      if (!where.includes("IN123")) return undefined;
      return HttpResponse.json({
        docs: [{ ...bookableItemFixtures[0], ...bookerBookingAccess }],
        totalDocs: 1,
        limit: 2,
        page: 1,
        pagingCounter: 1,
        totalPages: 1,
        hasPrevPage: false,
        hasNextPage: false,
        prevPage: null,
        nextPage: null,
      });
    }),
  );
}

registerHandlers();

beforeEach(() => {
  window.history.replaceState({}, "", "/booking/bookable-items/IN123");
  registerHandlers();
});

afterEach(() => {
  window.history.replaceState({}, "", "/");
  cleanup();
});

describe("BookableItemPage", () => {
  test("creates a calendar link once and returns it on later opens", async () => {
    let active = false;
    let updatedAt: string | null = null;
    let sequence = 0;
    let currentToken: string | null = null;
    let getRequests = 0;
    let postRequests = 0;
    const feedPath = "/public/booking/calendars/feed.ics";
    const issue = () => {
      sequence += 1;
      currentToken = String.fromCharCode(96 + sequence).repeat(43);
      active = true;
      updatedAt = "2026-08-27T12:00:00.000Z";
      return `${window.location.origin}${feedPath}?token=${currentToken}`;
    };
    worker.use(
      http.get("/api/v2/booking-configurations/7/calendar-subscription", () => {
        getRequests += 1;
        return HttpResponse.json(
          active
            ? {
                active: true,
                updatedAt,
                subscriptionUrl: `${window.location.origin}${feedPath}?token=${currentToken}`,
              }
            : { active: false, updatedAt: null, subscriptionUrl: null },
        );
      }),
      http.post("/api/v2/booking-configurations/7/calendar-subscription", () => {
        postRequests += 1;
        const subscriptionUrl = issue();
        return HttpResponse.json({ active: true, updatedAt, subscriptionUrl });
      }),
      http.get(
        feedPath,
        ({ request }) =>
          new HttpResponse(null, {
            status: new URL(request.url).searchParams.get("token") === currentToken ? 200 : 404,
          }),
      ),
    );
    const first = render(<BookableItemPageStory />);
    await expect.element(pageObj.heading).toBeVisible();
    expect(getRequests).toBe(0);
    expect(postRequests).toBe(0);

    await pageObj.calendarTrigger.click();
    await expect.element(pageObj.calendarUrl).toBeVisible();
    expect(getRequests).toBe(1);
    expect(postRequests).toBe(1);
    expect(pageObj.calendarTrigger.element().getAttribute("aria-controls")).toBe(pageObj.calendarDialog.element().id);
    await expect.element(page.getByRole("link", { name: "Google Calendar" })).toHaveFocus();
    expect(page.getByRole("link", { name: "Apple" }).element().getAttribute("href")).toMatch(/^webcal:/);
    expect(page.getByRole("link", { name: "Other" }).element().getAttribute("href")).toMatch(/^webcal:/);
    await expect.element(pageObj.calendarUrl).toBeVisible();
    const originalUrl = (pageObj.calendarUrl.element() as HTMLInputElement).value;
    expect((await fetch(originalUrl)).status).toBe(200);
    expect(postRequests).toBe(1);
    await expectNoAxeViolations();

    first.unmount();
    render(<BookableItemPageStory />);
    await expect.element(pageObj.heading).toBeVisible();
    await pageObj.calendarTrigger.click();
    await expect.element(pageObj.calendarUrl).toBeVisible();
    expect((pageObj.calendarUrl.element() as HTMLInputElement).value).toBe(originalUrl);
    expect(postRequests).toBe(1);
    expect((await fetch(originalUrl)).status).toBe(200);
  });

  test("keeps the calendar control available to an ordinary readable user", async () => {
    useBookerConfiguration();
    render(<BookableItemPageStory hasSysAdminRole={false} />);
    await expect.element(pageObj.heading).toBeVisible();
    await expect.element(pageObj.calendarTrigger).toBeVisible();
    await pageObj.calendarTrigger.click();
    await expect.element(pageObj.calendarUrl).toBeVisible();
    await expectNoAxeViolations();
  });

  test("shows loading, retries status, and keeps an ambiguous mutation failure open", async () => {
    let statusRequests = 0;
    worker.use(
      http.get("/api/v2/booking-configurations/7/calendar-subscription", async () => {
        statusRequests += 1;
        if (statusRequests === 1) {
          await new Promise((resolve) => setTimeout(resolve, 100));
          return HttpResponse.json({ status: 503 }, { status: 503 });
        }
        return HttpResponse.json({ active: false, updatedAt: null, subscriptionUrl: null });
      }),
      http.post("/api/v2/booking-configurations/7/calendar-subscription", () =>
        HttpResponse.json({ status: 503 }, { status: 503 }),
      ),
    );
    render(<BookableItemPageStory />);
    await expect.element(pageObj.heading).toBeVisible();

    await pageObj.calendarTrigger.click();
    await expect.element(page.getByText("Loading calendar status.", { exact: true })).toBeVisible();
    await expect
      .element(page.getByRole("alert"))
      .toHaveTextContent("Calendar subscription status could not be loaded.");
    await pageObj.calendarDialog.getByRole("button", { name: "Retry" }).click();

    await expect.element(page.getByRole("alert")).toHaveTextContent("The calendar link could not be generated.");
    await expect.element(pageObj.calendarDialog).toBeVisible();
    await expect.element(pageObj.calendarUrl).not.toBeInTheDocument();
  });

  test("supports the calendar flow by keyboard and announces a successful copy", async () => {
    const subscriptionUrl = `${window.location.origin}/public/booking/calendars/feed.ics?token=${"k".repeat(43)}`;
    worker.use(
      http.get("/api/v2/booking-configurations/7/calendar-subscription", () =>
        HttpResponse.json({ active: false, updatedAt: null, subscriptionUrl: null }),
      ),
      http.post("/api/v2/booking-configurations/7/calendar-subscription", () =>
        HttpResponse.json({
          active: true,
          updatedAt: "2026-08-27T12:00:00.000Z",
          subscriptionUrl,
        }),
      ),
    );
    const clipboard = vi.spyOn(navigator.clipboard, "writeText").mockResolvedValue(undefined);
    try {
      render(<BookableItemPageStory />);
      await expect.element(pageObj.heading).toBeVisible();
      pageObj.calendarTrigger.element().focus();
      await userEvent.keyboard("{Enter}");
      await expect.element(page.getByRole("link", { name: "Google Calendar" })).toHaveFocus();
      const other = page.getByRole("link", { name: "Other" }).element();
      const calendarUrl = pageObj.calendarUrl.element();
      await userEvent.keyboard("{Tab}");
      expect([other, calendarUrl]).toContain(document.activeElement);
      const forwardFocus = document.activeElement;
      await userEvent.keyboard("{Shift>}{Tab}{/Shift}");
      expect(document.activeElement).not.toBe(forwardFocus);
      expect(pageObj.calendarDialog.element().contains(document.activeElement)).toBe(true);

      const copy = page.getByRole("button", { name: "Copy link" }).element();
      for (let step = 0; step < 6 && document.activeElement !== copy; step += 1) {
        await userEvent.keyboard("{Tab}");
      }
      expect(document.activeElement).toBe(copy);
      await userEvent.keyboard("{Enter}");
      await expect.element(page.getByText("Copied", { exact: true })).toBeVisible();
      await expect.element(page.getByRole("button", { name: "Copy link" })).toHaveFocus();
      expect(clipboard).toHaveBeenCalledOnce();

      await userEvent.keyboard("{Escape}");
      await expect.element(pageObj.calendarDialog).not.toBeInTheDocument();
      await expect.element(pageObj.calendarTrigger).toHaveFocus();
    } finally {
      clipboard.mockRestore();
    }
  });

  test("uses URL-backed Bookings, Details, and Audit log tabs", async () => {
    let auditRequests = 0;
    worker.use(
      http.get("/api/v2/booking-configurations/7/audit", () => {
        auditRequests += 1;
        return HttpResponse.json(auditPage());
      }),
    );
    render(<BookableItemPageStory />);

    await expect.element(pageObj.heading).toBeVisible();
    await expect.element(pageObj.bookingsTab).toHaveAttribute("aria-selected", "true");
    await expect.element(page.getByRole("heading", { name: "Upcoming events" })).toBeVisible();
    await expect.element(page.getByRole("heading", { name: "Past events" })).toBeVisible();
    expect(auditRequests).toBe(0);
    expect(window.location.search).toBe("");

    await pageObj.detailsTab.click();
    await expect.element(pageObj.detailsTab).toHaveAttribute("aria-selected", "true");
    await expect.element(page.getByText("Booking rules")).toBeVisible();
    await expect.poll(() => new URLSearchParams(window.location.search).get("tab")).toBe("details");

    await pageObj.auditTab.click();
    await expect
      .poll(() =>
        page
          .getByText("Ada Lovelace (ada)", { exact: true })
          .all()
          .some((candidate) => candidate.element().getClientRects().length > 0),
      )
      .toBe(true);
    const visibleActor = page
      .getByText("Ada Lovelace (ada)", { exact: true })
      .all()
      .find((candidate) => candidate.element().getClientRects().length > 0);
    expect(visibleActor?.element().closest('[data-slot="user-badge"]')).not.toBeNull();
    await expect.element(page.getByText("Results through Aug 25, 2026", { exact: true }).first()).toBeVisible();
    await expect.poll(() => auditRequests).toBe(1);
    await expect.poll(() => new URLSearchParams(window.location.search).get("tab")).toBe("audit");
  });

  test("keeps one snapshot while paging and discards it on Refresh", async () => {
    const requests: URL[] = [];
    worker.use(
      http.get("/api/v2/booking-configurations/7/audit", ({ request }) => {
        const url = new URL(request.url);
        requests.push(url);
        return HttpResponse.json(auditPage(Number(url.searchParams.get("page")), 2));
      }),
    );
    render(<BookableItemPageStory />);
    await expect.element(pageObj.heading).toBeVisible();
    await pageObj.auditTab.click();
    await expect.element(pageObj.nextAuditPage).toBeVisible();

    await pageObj.nextAuditPage.click();
    await expect.poll(() => requests.length).toBe(2);
    expect(Object.fromEntries(requests[1].searchParams)).toMatchObject({
      page: "2",
      snapshotDate: "2026-08-25",
      snapshotFingerprint,
    });
    await expect.element(pageObj.previousAuditPage).not.toBeDisabled();

    await pageObj.refreshAudit.click();
    await expect.poll(() => requests.length).toBe(3);
    await expect.element(pageObj.refreshAudit).toHaveFocus();
    expect(requests[2].searchParams.get("page")).toBe("1");
    expect(requests[2].searchParams.has("snapshotDate")).toBe(false);
    await expect.element(page.getByRole("status").first()).toHaveTextContent("Results through");
  });

  test("validates custom dates at the fields before requesting", async () => {
    let requests = 0;
    worker.use(
      http.get("/api/v2/booking-configurations/7/audit", () => {
        requests += 1;
        return HttpResponse.json(auditPage());
      }),
    );
    render(<BookableItemPageStory />);
    await expect.element(pageObj.heading).toBeVisible();
    await pageObj.auditTab.click();
    await expect.element(pageObj.auditFrom).toBeVisible();

    await userEvent.clear(pageObj.auditFrom);
    await pageObj.applyAuditRange.click();

    await expect.element(pageObj.auditFrom).toHaveAttribute("aria-invalid", "true");
    await expect.element(pageObj.auditFrom).toHaveFocus();
    expect(pageObj.auditFrom.element().getAttribute("aria-describedby")).toBe("audit-from-error");
    await expect.element(page.getByText(/^From: Choose a date/)).toBeVisible();
    expect(requests).toBe(1);

    await userEvent.fill(pageObj.auditFrom, "2026-08-01");
    await userEvent.fill(pageObj.auditTo, "2026-08-25");
    pageObj.applyAuditRange.element().focus();
    await userEvent.keyboard("{Enter}");
    await expect.poll(() => requests).toBe(2);
    await expect.element(pageObj.auditFrom).toHaveAttribute("aria-invalid", "false");
    await expectNoAxeViolations();
  });

  test("recovers from a changed snapshot and focuses the replacement results", async () => {
    const requests: URL[] = [];
    worker.use(
      http.get("/api/v2/booking-configurations/7/audit", ({ request }) => {
        const url = new URL(request.url);
        requests.push(url);
        if (url.searchParams.get("page") === "2") {
          return HttpResponse.json(
            { status: 409, code: "errors.api.v2.audit.snapshot.changed", detail: "Changed" },
            { status: 409 },
          );
        }
        return HttpResponse.json(auditPage(1, 2));
      }),
    );
    render(<BookableItemPageStory />);
    await expect.element(pageObj.heading).toBeVisible();
    await pageObj.auditTab.click();
    await expect.element(pageObj.nextAuditPage).toBeVisible();
    await pageObj.nextAuditPage.click();

    const alert = page.getByRole("alert");
    await expect.element(alert).toHaveTextContent("The audit results changed");
    await expect.element(alert).toHaveFocus();
    await expect.element(page.getByText("Ada Lovelace (ada)", { exact: true })).not.toBeInTheDocument();
    await page.getByRole("button", { name: "Restart from first page" }).click();

    await expect.poll(() => requests.length).toBe(3);
    expect(requests[2].searchParams.has("snapshotDate")).toBe(false);
    await expect.element(pageObj.auditResultsHeading).toHaveFocus();
    await expectNoAxeViolations();
  });

  test.each([
    [503, "errors.api.v2.audit.unavailable", "Audit log unavailable"],
    [400, "errors.api.v2.audit.results.tooMany", "Too many audit events"],
  ])("shows the distinct audit refusal for %s", async (status, code, title) => {
    worker.use(
      http.get("/api/v2/booking-configurations/7/audit", () =>
        HttpResponse.json({ status, code, detail: "Do not display" }, { status }),
      ),
    );
    render(<BookableItemPageStory />);
    await expect.element(pageObj.heading).toBeVisible();
    await pageObj.auditTab.click();

    const alert = page.getByRole("alert");
    await expect.element(alert).toHaveTextContent(title);
    await expect.element(alert).not.toHaveTextContent("Do not display");
    await expect.element(page.getByText("Ada Lovelace (ada)", { exact: true })).not.toBeInTheDocument();
    await expectNoAxeViolations();
  });

  test("preserves a dirty editor and hides its controls when another tab is active", async () => {
    render(<BookableItemPageStory />);
    await expect.element(pageObj.heading).toBeVisible();
    await pageObj.openEditor();

    await expect.element(pageObj.save).toBeVisible();
    await expect.element(pageObj.cancel).toBeVisible();
    expect(pageObj.save.element().closest('[data-slot="card-action"]')).not.toBeNull();
    await userEvent.fill(pageObj.maximumDuration, "60");
    const maximumDurationInput = pageObj.maximumDuration.element();

    await pageObj.auditTab.click();
    await expect.element(pageObj.detailsPanel).not.toBeVisible();
    await expect.element(page.getByRole("spinbutton", { name: "Maximum duration" })).not.toBeInTheDocument();
    expect(maximumDurationInput.closest("[hidden]")).not.toBeNull();
    expect(new URLSearchParams(window.location.search).get("edit")).toBe("true");

    await pageObj.detailsTab.click();
    await expect.element(pageObj.maximumDuration).toHaveValue(60);
    expect(new URLSearchParams(window.location.search).get("edit")).toBe("true");
  });

  test("keeps one accessible active panel and supports arrow-key tab navigation", async () => {
    render(<BookableItemPageStory />);
    await expect.element(pageObj.heading).toBeVisible();

    expect(pageObj.bookingsTab.element().getAttribute("aria-controls")).toBe(pageObj.bookingsPanel.element().id);
    expect(pageObj.detailsTab.element().getAttribute("aria-controls")).toBe(pageObj.detailsPanel.element().id);
    expect(pageObj.bookingsPanel.element().getAttribute("aria-labelledby")).toBe(pageObj.bookingsTab.element().id);
    expect(pageObj.detailsPanel.element().getAttribute("aria-labelledby")).toBe(pageObj.detailsTab.element().id);

    await pageObj.bookingsTab.click();
    await userEvent.keyboard("{ArrowRight}");
    await expect.element(pageObj.detailsTab).toHaveFocus();
    await userEvent.keyboard("{Enter}");
    await expect.element(pageObj.detailsTab).toHaveAttribute("aria-selected", "true");
    await expect.element(page.getByRole("tabpanel", { name: "Bookings" })).not.toBeInTheDocument();
    await expect.element(pageObj.detailsPanel).toBeVisible();

    await pageObj.auditTab.click();
    expect(pageObj.auditTab.element().getAttribute("aria-controls")).toBe(pageObj.auditPanel.element().id);
    await expect.element(pageObj.detailsPanel).not.toBeVisible();
    await expect.element(pageObj.auditPanel).toBeVisible();
  });

  test("manages access by keyboard with safe roles, announcements, responsive layout, and accessibility modes", async () => {
    const originalViewport = { width: window.innerWidth, height: window.innerHeight };
    await emulateForcedColors();
    await emulateReducedMotion();
    await page.viewport(320, 900);

    try {
      render(<BookableItemPageStory />);
      await expect.element(pageObj.heading).toBeVisible();
      pageObj.accessTab.element().focus();
      await userEvent.keyboard("{Enter}");
      await expect.element(pageObj.accessPanel).toBeVisible();
      expect(pageObj.accessTab.element().getAttribute("aria-controls")).toBe(pageObj.accessPanel.element().id);
      expect(pageObj.accessPanel.element().getAttribute("aria-labelledby")).toBe(pageObj.accessTab.element().id);

      // Picking a grantee from the finder stages it straight away; the role is then a menu.
      const search = page.getByRole("combobox", { name: "Add user or group" });
      await userEvent.fill(search, "gr");
      const graceOption = page.getByRole("option", { name: /Grace Hopper/ });
      await expect.element(graceOption).toBeVisible();
      await graceOption.click();
      const graceRole = page.getByRole("button", { name: "Direct role for Grace Hopper" });
      await expect.element(graceRole).toHaveTextContent("Booker");
      await graceRole.click();
      await page.getByRole("menuitem", { name: "Viewer" }).click();
      await expect.element(graceRole).toHaveTextContent("Viewer");
      // The audience row carries no remove action; the staged role change is what makes this dirty.
      await expect.element(page.getByRole("button", { name: "Remove All users" })).not.toBeInTheDocument();
      await expect.element(page.getByText("Unsaved changes", { exact: true })).toBeVisible();

      await page.getByRole("button", { name: "Cancel" }).click();
      const assignments = page.getByRole("list", { name: "Access assignments" });
      await expect.element(assignments.getByText("All users", { exact: true })).toBeVisible();
      await expect.element(assignments.getByText("Grace Hopper", { exact: true })).not.toBeInTheDocument();
      await expect.element(page.getByText("Unsaved access changes were cancelled.", { exact: true })).toBeVisible();

      await userEvent.fill(search, "gr");
      await page.getByRole("option", { name: /Grace Hopper/ }).click();
      await page.getByRole("button", { name: "Save changes" }).click();
      const savedStatus = page.getByText("Access changes saved.", { exact: true });
      await expect.element(savedStatus).toBeVisible();
      await expect.element(savedStatus).toHaveFocus();
      await expect.poll(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth).toBe(true);
      await expectNoAxeViolations();
    } finally {
      await page.viewport(originalViewport.width, originalViewport.height);
    }
  });

  test("blocks tab changes during PATCH and restores focus after saving", async () => {
    let releasePatch: (() => void) | undefined;
    worker.use(
      http.patch(
        "/api/v2/booking-configurations/7",
        () =>
          new Promise<HttpResponse<null>>((resolve) => {
            releasePatch = () => resolve(new HttpResponse(null, { status: 204 }));
          }),
      ),
    );
    render(<BookableItemPageStory />);
    await expect.element(pageObj.heading).toBeVisible();
    await pageObj.openEditor();
    await userEvent.fill(pageObj.maximumDuration, "60");
    await pageObj.save.click();

    await expect.element(pageObj.bookingsTab).toBeDisabled();
    await expect.element(pageObj.detailsTab).toBeDisabled();
    await expect.element(pageObj.auditTab).toBeDisabled();
    await expect.element(page.getByRole("status")).toHaveTextContent("Saving booking configuration.");
    await expect.poll(() => releasePatch !== undefined).toBe(true);
    releasePatch?.();

    await expect.element(pageObj.edit).toHaveFocus();
    await expect.element(pageObj.auditTab).not.toBeDisabled();
    await expect.element(page.getByRole("status")).toHaveTextContent("Booking configuration saved.");
  });

  test("focuses an invalid field and associates correction guidance", async () => {
    render(<BookableItemPageStory />);
    await expect.element(pageObj.heading).toBeVisible();
    await pageObj.openEditor();
    await userEvent.fill(pageObj.maximumDuration, "7");
    await pageObj.save.click();

    await expect.element(pageObj.maximumDuration).toHaveAttribute("aria-invalid", "true");
    await expect.element(pageObj.maximumDuration).toHaveFocus();
    await expect.element(page.getByText("Use 0 or a duration divisible by the selected time increment.")).toBeVisible();
    expect(pageObj.maximumDuration.element().getAttribute("aria-describedby")).toContain("maximum-duration-error");
  });

  test("keeps the editor available after a failed save", async () => {
    worker.use(
      http.patch("/api/v2/booking-configurations/7", () =>
        HttpResponse.json({ message: "Unable to save" }, { status: 500 }),
      ),
    );
    render(<BookableItemPageStory />);
    await expect.element(pageObj.heading).toBeVisible();
    await pageObj.openEditor();
    await userEvent.fill(pageObj.maximumDuration, "60");
    await pageObj.save.click();

    await expect.element(page.getByRole("alert")).toBeVisible();
    await expect.element(pageObj.maximumDuration).toHaveValue(60);
    await expect.element(pageObj.save).toHaveFocus();
    await expect.element(pageObj.auditTab).not.toBeDisabled();
  });

  test("discards a draft and restores focus after cancelling", async () => {
    render(<BookableItemPageStory />);
    await expect.element(pageObj.heading).toBeVisible();
    await pageObj.openEditor();
    await userEvent.fill(pageObj.maximumDuration, "60");
    await pageObj.cancel.click();

    await expect.element(pageObj.edit).toHaveFocus();
    await pageObj.edit.click();
    await expect.element(pageObj.maximumDuration).toHaveValue(0);
  });

  test("fits the identity, tabs, and inline editor at 320 CSS pixels", async () => {
    const originalViewport = { width: window.innerWidth, height: window.innerHeight };
    const longName = "Confocal microscope with an exceptionally long inventory record name for the imaging facility";
    worker.use(
      http.get("/api/v2/booking-configurations", () =>
        HttpResponse.json({
          docs: [
            {
              ...bookableItemFixtures[0],
              target: {
                ...bookableItemFixtures[0].target,
                value: { ...bookableItemFixtures[0].target.value, name: longName },
              },
            },
          ],
          totalDocs: 1,
          limit: 20,
          page: 1,
          pagingCounter: 1,
          totalPages: 1,
          hasPrevPage: false,
          hasNextPage: false,
          prevPage: null,
          nextPage: null,
        }),
      ),
    );
    await page.viewport(320, 900);

    try {
      render(<BookableItemPageStory />);
      const longHeading = page.getByRole("heading", { level: 1, name: longName });
      const globalId = page.getByText("IN123", { exact: true });
      await expect.element(longHeading).toBeVisible();
      await pageObj.openEditor();

      await expect.poll(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth).toBe(true);
      const headingStyle = getComputedStyle(longHeading.element());
      expect(longHeading.element().getBoundingClientRect().height).toBeLessThanOrEqual(
        Number.parseFloat(headingStyle.lineHeight) * 1.1,
      );
      expect(longHeading.element().parentElement).toBe(globalId.element().parentElement);
      await pageObj.calendarTrigger.click();
      await expect.element(pageObj.calendarUrl).toBeVisible();
      await expect.poll(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth).toBe(true);
      await page.getByRole("button", { name: "Close" }).click();
      await pageObj.auditTab.click();
      await expect.element(pageObj.auditFrom).toBeVisible();
      await expect.element(pageObj.auditTo).toBeVisible();
      await expect.element(pageObj.refreshAudit).toBeVisible();
      await expect.poll(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth).toBe(true);
      await expectNoAxeViolations();
    } finally {
      await page.viewport(originalViewport.width, originalViewport.height);
    }
  });

  test("keeps ordinary users read-only on a direct edit URL", async () => {
    window.history.replaceState({}, "", "/booking/bookable-items/IN123?tab=details&edit=true");
    useBookerConfiguration();
    render(<BookableItemPageStory hasSysAdminRole={false} />);

    await expect.element(page.getByText("Booking rules")).toBeVisible();
    await expect.element(pageObj.edit).not.toBeInTheDocument();
    await expect.element(pageObj.save).not.toBeInTheDocument();
    await expectNoAxeViolations();
  });
});
