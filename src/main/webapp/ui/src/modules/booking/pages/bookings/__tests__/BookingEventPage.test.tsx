import { beforeAll as beforeBookingCatalog } from "vitest";
import appI18n from "@/modules/common/i18n";

beforeBookingCatalog(async () => {
  appI18n.addResourceBundle(
    "en-US",
    "booking",
    (await import("@/modules/common/i18n/locales/en-US/booking.json")).default,
  );
});

import { createRealI18nWrapper } from "@/__tests__/helpers/realI18n";
import bookingEnglish from "@/modules/common/i18n/locales/en-US/booking.json";
import commonEnglish from "@/modules/common/i18n/locales/en-US/common.json";
import "@/__tests__/__mocks__/matchMedia";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { RouterProvider } from "@tanstack/react-router";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { type ComponentType, type ReactNode, Suspense } from "react";
import { describe, expect, it } from "vitest";
import { OAUTH_TOKEN, oauthTokenHandler } from "@/__tests__/mocks/oauthTokenMocks";
import { server } from "@/__tests__/mswServer";
import type { BookingDetails } from "@/modules/booking/domain/booking";
import { bookingDisplayPreferencesQueryKey } from "@/modules/booking/domain/bookingDisplayPreferences";
import { inheritedBrowserBookingPreferences } from "../../preferences/bookingPreferencesFixtures";
import { bookingEventFixture, createBookingEventTestRouter } from "./bookingEventTestHarness";

function renderPage(
  path = "/booking/calendar/bookings/41",
  preferencesReady?: Promise<void>,
  preferences: unknown = inheritedBrowserBookingPreferences,
  wrapper?: ComponentType<{ children: ReactNode }>,
) {
  server.use(oauthTokenHandler(true));
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(["rspace.common.auth", "oauthToken", "v2"], OAUTH_TOKEN);
  if (preferencesReady) {
    server.use(
      http.get("/api/v2/users/me/booking-preferences", async () => {
        await preferencesReady;
        return HttpResponse.json(inheritedBrowserBookingPreferences);
      }),
    );
  } else {
    queryClient.setQueryData(bookingDisplayPreferencesQueryKey, preferences);
  }
  const router = createBookingEventTestRouter(path);
  return {
    ...render(
      <QueryClientProvider client={queryClient}>
        <Suspense fallback={null}>
          <RouterProvider router={router as never} />
        </Suspense>
      </QueryClientProvider>,
      { wrapper },
    ),
    router,
    queryClient,
  };
}

describe("BookingEventPage", () => {
  it("uses the same page skeleton across preference suspension and the booking read", async () => {
    const preferences = Promise.withResolvers<void>();
    const booking = Promise.withResolvers<void>();
    let requested = false;
    server.use(
      http.get("/api/v2/bookings/41", async () => {
        requested = true;
        await booking.promise;
        return HttpResponse.json(bookingEventFixture);
      }),
    );
    renderPage("/booking/calendar/bookings/41", preferences.promise);
    try {
      const main = await screen.findByRole("main");
      expect(main).toHaveAttribute("aria-busy", "true");
      expect(requested).toBe(false);
      preferences.resolve();
      await waitFor(() => expect(requested).toBe(true));
      expect(screen.getByRole("main")).toHaveAttribute("class", main.className);
      expect(screen.getByRole("main")).toHaveAttribute("aria-busy", "true");
      expect(screen.queryByText("Cell imaging")).not.toBeInTheDocument();
    } finally {
      preferences.resolve();
      booking.resolve();
    }
    expect(await screen.findByText("Cell imaging")).toBeVisible();
  });

  it("reserves the page while loading without exposing resource controls", async () => {
    const response = Promise.withResolvers<Response>();
    server.use(http.get("/api/v2/bookings/41", () => response.promise));
    renderPage();

    try {
      expect(await screen.findByRole("main")).toHaveAttribute("aria-busy", "true");
      expect(screen.getByRole("status")).toHaveClass("sr-only");
      expect(screen.queryByRole("link")).not.toBeInTheDocument();
      expect(screen.queryByRole("button")).not.toBeInTheDocument();
    } finally {
      response.resolve(HttpResponse.json(bookingEventFixture));
    }
    expect(await screen.findByRole("heading", { name: "Confocal microscope" })).toBeVisible();
    expect(screen.getByRole("main")).not.toHaveAttribute("aria-busy", "true");
  });

  it.each([403, 404])("does not retain another booking while navigating to a denied booking (%s)", async (status) => {
    const response = Promise.withResolvers<Response>();
    server.use(
      http.get("/api/v2/bookings/41", () => HttpResponse.json(bookingEventFixture)),
      http.get("/api/v2/bookings/42", () => response.promise),
    );
    const { router } = renderPage();
    expect(await screen.findByText("Cell imaging")).toBeVisible();
    try {
      await router.navigate({ to: "/booking/calendar/bookings/$id", params: { id: "42" } });
      expect(await screen.findByRole("status")).toHaveClass("sr-only");
      expect(screen.queryByText("Cell imaging")).not.toBeInTheDocument();
      expect(screen.queryByText("Confocal microscope")).not.toBeInTheDocument();
      expect(screen.queryByRole("link", { name: "booking:bookings.actions.edit" })).not.toBeInTheDocument();
    } finally {
      response.resolve(new HttpResponse(null, { status }));
    }
    expect(await screen.findByText("booking:bookings.details.unavailableTitle")).toBeVisible();
    expect(screen.getByRole("main")).not.toHaveAttribute("aria-busy", "true");
  });

  it("renders the event readout, metadata aside, item return, and compact location", async () => {
    server.use(http.get("/api/v2/bookings/41", () => HttpResponse.json(bookingEventFixture)));
    renderPage();

    expect(await screen.findByRole("heading", { level: 1, name: "Confocal microscope" })).toHaveClass(
      "text-2xl",
      "font-semibold",
    );
    expect(screen.getByRole("heading", { name: "booking:bookings.details.title" })).toBeVisible();
    expect(screen.getByText("Cell imaging")).toBeVisible();
    expect(screen.getByText("Ada Lovelace (ada)")).toBeVisible();
    const aside = screen.getByRole("complementary", { name: "booking:bookings.details.aboutBooking" });
    expect(within(aside).getByText("booking:bookings.details.timesShownIn")).toBeVisible();
    expect(within(aside).getByText("booking:bookings.details.lastUpdated")).toBeVisible();
    expect(screen.getByRole("link", { name: "booking:bookings.details.returnToItemCalendar" })).toHaveAttribute(
      "href",
      "/booking/bookable-items/IN123",
    );
    expect(screen.getByRole("link", { name: "booking:bookings.details.viewItem" })).toHaveAttribute(
      "href",
      "/booking/bookable-items/IN123",
    );
    expect(screen.getByRole("link", { name: "Imaging lab" })).toHaveAttribute("href", "/globalId/IC456");
    expect(screen.getByRole("link", { name: "booking:bookings.actions.edit" })).toHaveAttribute(
      "href",
      "/booking/calendar/bookings/41/edit",
    );
  });

  it("keeps booking details readable when the parent container is unreadable", async () => {
    server.use(
      http.get("/api/v2/bookings/41", () =>
        HttpResponse.json({
          ...bookingEventFixture,
          location: null,
        }),
      ),
    );
    renderPage();

    expect(await screen.findByRole("heading", { level: 1, name: "Confocal microscope" })).toBeVisible();
    expect(screen.queryByRole("link", { name: "Imaging lab" })).not.toBeInTheDocument();
  });

  it.each([
    // Europe/Berlin repeats 02:00-03:00 on 2026-10-25; both bookings start at 02:30 and end at 03:30.
    ["earlier", "2026-10-25T00:30:00Z", /0?2:30\s?(AM)? \+02:00 – .*0?3:30\s?(AM)? \+01:00/],
    ["later", "2026-10-25T01:30:00Z", /0?2:30\s?(AM)? \+01:00 – .*0?3:30\s?(AM)? \+01:00/],
  ])("tells the %s occurrence of a repeated hour apart in the booking's times", async (_, start, when) => {
    server.use(
      http.get("/api/v2/bookings/41", () =>
        HttpResponse.json({ ...bookingEventFixture, start, end: "2026-10-25T02:30:00Z" }),
      ),
    );
    const wrapper = await createRealI18nWrapper({
      resources: { booking: bookingEnglish, common: commonEnglish },
      defaultNS: "booking",
    });
    renderPage(
      "/booking/calendar/bookings/41",
      undefined,
      {
        ...inheritedBrowserBookingPreferences,
        timezoneMode: "CUSTOM",
        customTimezone: "Europe/Berlin",
        overridden: true,
      },
      wrapper,
    );

    const label = await screen.findByText("When");
    expect(label.nextElementSibling).toMatchTextContent(when);
  });

  it("keeps role-lost details readable without configuration capabilities", async () => {
    server.use(
      http.get("/api/v2/bookings/41", () =>
        HttpResponse.json({
          ...bookingEventFixture,
          target: null,
          timezone: null,
          canViewConfiguration: false,
          canEdit: false,
          canCancel: false,
        }),
      ),
    );
    renderPage();

    expect(await screen.findByText("Cell imaging")).toBeVisible();
    expect(screen.getByText("common:values.unknownItem")).toBeVisible();
    expect(screen.queryByText("Confocal microscope")).not.toBeInTheDocument();
    expect(screen.queryByText("IN123", { exact: true })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "booking:bookings.details.returnToMyBookings" })).toHaveAttribute(
      "href",
      "/booking/my-bookings?period=upcoming",
    );
    expect(screen.queryByRole("link", { name: "Imaging lab" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "booking:bookings.details.viewItem" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "booking:bookings.actions.edit" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "booking:calendar.file.accessibleLabel" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "booking:bookings.actions.cancel" })).not.toBeInTheDocument();
  });

  it.each([
    ["its scheduling timezone is visible", "Europe/Berlin", true],
    ["its scheduling timezone is withheld", null, false],
  ])("warns about a clock change in the details only when %s", async (_, timezone, warned) => {
    server.use(
      http.get("/api/v2/bookings/41", () =>
        HttpResponse.json({ ...document, timezone, start: "2026-10-24T10:00:00Z", end: "2026-10-25T11:00:00Z" }),
      ),
    );
    renderPage();

    expect(await screen.findByText("Cell imaging")).toBeVisible();
    if (warned) {
      expect(screen.getByRole("note")).toHaveTextContent("booking:bookings.clockChange.daylightSavingDays");
    } else {
      expect(screen.queryByRole("note")).not.toBeInTheDocument();
    }
  });

  it("cancels in place and keeps the same readable URL", async () => {
    let current: BookingDetails = { ...bookingEventFixture };
    server.use(
      http.get("/api/v2/bookings/41", () => HttpResponse.json(current)),
      http.patch("/api/v2/bookings/41", () => {
        current = {
          ...current,
          version: 1,
          state: "CANCELLED",
          cancellationReason: "Instrument needs recalibration",
          canEdit: false,
          canCancel: false,
        };
        return HttpResponse.json(current);
      }),
    );
    const { router } = renderPage();
    const user = userEvent.setup();

    const trigger = await screen.findByRole("button", { name: "booking:bookings.actions.cancel" });
    await user.click(trigger);
    await user.click(screen.getByRole("button", { name: "booking:bookings.cancelDialog.keep" }));
    await waitFor(() => expect(screen.getByText("booking:bookings.details.confirmed")).toHaveFocus());
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();

    const badge = screen.getByText("booking:bookings.details.confirmed");
    const hiddenAtFocus: boolean[] = [];
    badge.addEventListener("focus", () => {
      hiddenAtFocus.push(Boolean(badge.closest('[aria-hidden="true"]')));
    });
    await user.click(trigger);
    const cancellationButtons = screen.getAllByRole("button", { name: "booking:bookings.actions.cancel" });
    await user.click(cancellationButtons[cancellationButtons.length - 1]);

    const cancelledBadge = await screen.findByText("booking:bookings.details.cancelled");
    await waitFor(() => expect(cancelledBadge).toHaveFocus());
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
    expect(hiddenAtFocus).toEqual([false]);
    expect(screen.getByText("booking:bookings.details.cancellationReason")).toBeVisible();
    expect(screen.getByText("Instrument needs recalibration")).toBeVisible();
    expect(within(screen.getByRole("main")).getByText("booking:bookings.details.bookingCancelled")).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/booking/calendar/bookings/41");
    expect(screen.queryByRole("button", { name: "booking:bookings.actions.cancel" })).not.toBeInTheDocument();
  });

  it.each([404, 408, 429])("conceals booking read status %s behind one unavailable state", async (status) => {
    let requests = 0;
    server.use(
      http.get("/api/v2/bookings/41", () => {
        requests += 1;
        return new HttpResponse(null, { status });
      }),
    );
    renderPage();

    expect(await screen.findByText("booking:bookings.details.unavailableTitle")).toBeVisible();
    expect(screen.getByText("booking:bookings.details.unavailableDescription")).toBeVisible();
    expect(screen.getByRole("link", { name: "booking:bookings.details.returnToMyBookings" })).toBeVisible();
    expect(screen.queryByRole("button", { name: "common:actions.retry" })).not.toBeInTheDocument();
    expect(requests).toBe(1);
  });

  it("conceals a busy-only response behind the unavailable state", async () => {
    server.use(
      http.get("/api/v2/bookings/41", () =>
        HttpResponse.json({
          ...bookingEventFixture,
          privacy: "busy",
          purpose: null,
          cancellationReason: null,
          bookedBy: null,
        }),
      ),
    );
    renderPage();

    expect(await screen.findByText("booking:bookings.details.unavailableTitle")).toBeVisible();
    expect(screen.queryByRole("button", { name: "common:actions.retry" })).not.toBeInTheDocument();
  });
});
