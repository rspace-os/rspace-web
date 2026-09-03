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
import { bookableItemDetailsHandlers, bookableItemFixtures } from "./mocks/bookableItemsMocks";
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
  test("uses one height for header action buttons and status badges", async () => {
    render(<BookableItemPageStory />);
    await expect.element(pageObj.heading).toBeVisible();
    await expect.element(page.getByRole("button", { name: "New Booking" })).toBeVisible();
    await expect.element(pageObj.calendarTrigger).toBeVisible();

    const actionBar = pageObj.heading
      .element()
      .closest("section")
      ?.querySelector<HTMLElement>('[data-slot="bookable-item-header-actions"]');
    expect(actionBar).not.toBeNull();
    const controls = Array.from(actionBar?.querySelectorAll<HTMLElement>('button, [data-slot="badge"]') ?? []);
    expect(controls.length).toBeGreaterThanOrEqual(5);
    expect(new Set(controls.map((control) => control.getBoundingClientRect().height))).toEqual(new Set([30]));
  });

  test("archives and restores from the keyboard-accessible lifecycle menu", async () => {
    let current = {
      ...bookableItemFixtures[0],
      state: "ACTIVE" as "ACTIVE" | "ARCHIVED",
      configurationVersion: 0 as number,
    };
    let archiveRequest: Request | undefined;
    let restoreRequest: Request | undefined;
    worker.use(
      http.get("/api/v2/booking-configurations", ({ request }) => {
        const where = new URL(request.url).searchParams.get("where") ?? "";
        return where.includes("IN123")
          ? HttpResponse.json({
              docs: [current],
              totalDocs: 1,
              limit: 20,
              page: 1,
              pagingCounter: 1,
              totalPages: 1,
              hasPrevPage: false,
              hasNextPage: false,
              prevPage: null,
              nextPage: null,
            })
          : undefined;
      }),
      http.delete("/api/v2/booking-configurations/7", ({ request }) => {
        archiveRequest = request;
        current = { ...current, state: "ARCHIVED", configurationVersion: 1 };
        return new HttpResponse(null, { status: 204 });
      }),
      http.patch("/api/v2/booking-configurations/7", async ({ request }) => {
        restoreRequest = request;
        current = { ...current, state: "ACTIVE", configurationVersion: 2 };
        return new HttpResponse(null, { status: 204 });
      }),
    );
    render(<BookableItemPageStory />);
    await expect.element(pageObj.heading).toBeVisible();

    const triggerBox = pageObj.lifecycleActions.element().getBoundingClientRect();
    expect(triggerBox.width).toBeGreaterThanOrEqual(44);
    pageObj.lifecycleActions.element().focus();
    await userEvent.keyboard("{Enter}");
    await expect.element(pageObj.archiveAction).toHaveFocus();
    await userEvent.keyboard("{Enter}");
    const archiveDialog = page.getByRole("alertdialog", { name: "Archive bookable item?" });
    await expect.element(archiveDialog).toBeVisible();
    await page.getByRole("button", { name: "Archive" }).click();

    await expect.poll(() => archiveRequest !== undefined).toBe(true);
    expect(archiveRequest?.headers.get("If-Match")).toBe('"0"');
    await expect.element(page.getByText("Archived", { exact: true })).toBeVisible();
    await expect.element(pageObj.lifecycleActions).toHaveFocus();

    await pageObj.lifecycleActions.click();
    await expect.element(pageObj.restoreAction).toBeVisible();
    await pageObj.restoreAction.click();

    await expect.poll(() => restoreRequest !== undefined).toBe(true);
    expect(restoreRequest?.headers.get("If-Match")).toBe('"1"');
    await expect(restoreRequest?.json()).resolves.toEqual({ state: "ACTIVE" });
    await expect.element(page.getByRole("button", { name: "New Booking" })).toBeVisible();
    await expect.element(pageObj.lifecycleActions).toHaveFocus();
    await expectNoAxeViolations();
  });

  test("keeps archived reading available while blocking writes and guarding permanent deletion", async () => {
    const archived = { ...bookableItemFixtures[0], state: "ARCHIVED", configurationVersion: 4 };
    let calendarCreates = 0;
    let permanentRequest: Request | undefined;
    worker.use(
      http.get("/api/v2/booking-configurations", ({ request }) => {
        const where = new URL(request.url).searchParams.get("where") ?? "";
        return where.includes("IN123")
          ? HttpResponse.json({
              docs: [archived],
              totalDocs: 1,
              limit: 20,
              page: 1,
              pagingCounter: 1,
              totalPages: 1,
              hasPrevPage: false,
              hasNextPage: false,
              prevPage: null,
              nextPage: null,
            })
          : undefined;
      }),
      http.post("/api/v2/booking-configurations/7/calendar-subscription", () => {
        calendarCreates += 1;
        return HttpResponse.json({ active: true, updatedAt: null, subscriptionUrl: null });
      }),
      http.delete("/api/v2/booking-configurations/7", ({ request }) => {
        permanentRequest = request;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    render(<BookableItemPageStory />);

    await expect.element(page.getByText("Archived", { exact: true })).toBeVisible();
    await expect.element(pageObj.edit).not.toBeInTheDocument();
    await expect.element(page.getByRole("button", { name: "New Booking" })).not.toBeInTheDocument();
    await pageObj.calendarTrigger.click();
    await expect
      .element(page.getByText("Calendar links cannot be generated while this booking configuration is archived."))
      .toBeVisible();
    expect(calendarCreates).toBe(0);
    await userEvent.keyboard("{Escape}");

    await pageObj.accessTab.click();
    await expect.element(pageObj.accessPanel).toBeVisible();
    await expect
      .poll(() =>
        page
          .getByText("Ada Lovelace", { exact: true })
          .all()
          .some((candidate) => candidate.element().getClientRects().length > 0),
      )
      .toBe(true);
    await expect.element(page.getByRole("combobox", { name: "Add user or group" })).not.toBeInTheDocument();
    await expect.element(page.getByRole("button", { name: /Direct role for/ })).not.toBeInTheDocument();

    await pageObj.lifecycleActions.click();
    await pageObj.permanentDeleteAction.click();
    const dialog = page.getByRole("alertdialog", { name: "Permanently delete configuration?" });
    const confirm = dialog.getByRole("button", { name: "Delete permanently" });
    const itemName = dialog.getByRole("textbox", { name: "Item name" });
    await expect.element(dialog).toHaveTextContent("access assignments");
    await expect.element(confirm).toBeDisabled();
    await userEvent.fill(itemName, "confocal microscope");
    await expect.element(confirm).toBeDisabled();
    await userEvent.fill(itemName, "Confocal microscope");
    await expect.element(confirm).not.toBeDisabled();
    await confirm.click();

    await expect.poll(() => permanentRequest !== undefined).toBe(true);
    expect(new URL(permanentRequest?.url ?? window.location.href).searchParams.get("permanent")).toBe("true");
    expect(permanentRequest?.headers.get("If-Match")).toBe('"4"');
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

  test("uses a distinct path for each tab", async () => {
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
    expect(window.location.pathname).toBe("/booking/bookable-items/IN123");

    await pageObj.detailsTab.click();
    await expect.element(pageObj.detailsTab).toHaveAttribute("aria-selected", "true");
    await expect.element(page.getByText("Booking rules")).toBeVisible();
    await expect.poll(() => window.location.pathname).toBe("/booking/bookable-items/IN123/details");

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
    await expect.poll(() => window.location.pathname).toBe("/booking/bookable-items/IN123/audit");

    await pageObj.accessTab.click();
    await expect.element(pageObj.accessTab).toHaveAttribute("aria-selected", "true");
    await expect.poll(() => window.location.pathname).toBe("/booking/bookable-items/IN123/access");

    await pageObj.bookingsTab.click();
    await expect.element(pageObj.bookingsTab).toHaveAttribute("aria-selected", "true");
    await expect.poll(() => window.location.pathname).toBe("/booking/bookable-items/IN123");
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

  test("mounts the access editor at 320 CSS pixels with keyboard navigation and no overflow", async () => {
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

      const search = page.getByRole("combobox", { name: "Add user or group" });
      await userEvent.fill(search, "gr");
      const graceOption = page.getByRole("option", { name: /Grace Hopper/ });
      await expect.element(graceOption).toBeVisible();
      await graceOption.click();
      const graceRole = page.getByRole("button", { name: "Direct role for Grace Hopper" });
      await expect.element(graceRole).toHaveTextContent("Booker");
      await graceRole.click();
      await expect.element(page.getByRole("menuitem", { name: "Viewer" })).toBeVisible();
      await userEvent.keyboard("{Escape}");
      await expect.element(graceRole).toHaveFocus();
      await expect.poll(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth).toBe(true);
      await pageObj.heading.hover();
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
});
