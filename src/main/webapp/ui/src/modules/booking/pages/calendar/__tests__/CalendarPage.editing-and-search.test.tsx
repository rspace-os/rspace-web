import "@/__tests__/__mocks__/matchMedia";
import { act, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { afterAll, beforeAll, beforeEach, describe, expect, it } from "vitest";
import { currentUser } from "@/__tests__/helpers/currentUserFixture";
import { oauthTokenHandler } from "@/__tests__/mocks/oauthTokenMocks";
import { server } from "@/__tests__/mswServer";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { bookingDisplayPreferencesQueryKey } from "@/modules/booking/domain/bookingDisplayPreferences";
import {
  bookableItemFixtures,
  bookableItemsHandlers,
  bookableItemsOpenApi,
  collectionPage,
} from "../../bookable-items/mocks/bookableItemsMocks";
import { collectionResponse, ownBooking } from "../../mocks/bookingCalendarFixtures";
import { bookingsOpenApi } from "../../my-bookings/mocks/bookingMocks";
import { inheritedBrowserBookingPreferences } from "../../preferences/bookingPreferencesFixtures";
import {
  renderCalendar,
  restoreCalendarScrollTo,
  setupCalendarBookableItems,
  setupCalendarScrollTo,
} from "./calendarPageTestUtils";

beforeAll(() => setupCalendarScrollTo());
beforeEach(() => setupCalendarBookableItems());
afterAll(() => restoreCalendarScrollTo());

describe("CalendarPage", () => {
  it.each(["duration", "opening hours", "increment"])(
    "uses current %s rules only when reserved times change",
    async (rule) => {
      const user = userEvent.setup();
      const patches: unknown[] = [];
      const booking = {
        ...ownBooking,
        ...(rule === "increment"
          ? { start: ownBooking.start.replace(":00:00Z", ":02:00Z"), end: ownBooking.end.replace(":00:00Z", ":02:00Z") }
          : {}),
      };
      const configuration = {
        ...bookableItemFixtures[0],
        ...(rule === "duration" ? { maxBookingDurationMinutes: 60 } : {}),
        ...(rule === "opening hours"
          ? { openingHours: [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "11:00", end: "17:00" })) }
          : {}),
        ...(rule === "increment" ? { slotGranularityMinutes: 10 } : {}),
      };
      server.use(
        oauthTokenHandler(true),
        http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
        http.get("/api/v2/bookings", () => HttpResponse.json(collectionResponse([booking]))),
        http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionPage([configuration]))),
        http.patch("/api/v2/bookings/41", async ({ request }) => {
          patches.push(await request.json());
          return HttpResponse.json({ ...ownBooking, version: 8, purpose: "Updated purpose" });
        }),
      );
      await renderCalendar();
      await user.click(await screen.findByRole("button", { name: "Agenda" }));
      await user.click(await screen.findByRole("button", { name: /Show details for Confocal microscope/ }));
      const card = screen.getByRole("dialog");
      await user.click(within(card).getByRole("button", { name: "Edit" }));
      const purpose = await screen.findByRole("textbox", { name: "Purpose" });
      expect(
        screen.getByText(
          "This booking does not meet the current booking rules. You can update its purpose without changing its reserved times. Changing those times requires following the current rules.",
        ),
      ).toHaveAttribute("role", "status");
      await user.clear(purpose);
      await user.type(purpose, "Updated purpose");
      const end = within(card).getByLabelText("End time");
      const originalTime = end.getAttribute("value");
      await user.clear(end);
      await user.type(end, "12:45");
      await user.tab();
      await user.click(screen.getByRole("button", { name: "Save changes" }));
      expect(patches).toEqual([]);
      expect(
        screen.queryByText(
          "This booking does not meet the current booking rules. You can update its purpose without changing its reserved times. Changing those times requires following the current rules.",
        ),
      ).not.toBeInTheDocument();
      expect(end).toHaveAttribute("aria-invalid", "true");
      await user.clear(end);
      await user.type(end, originalTime ?? "");
      await user.tab();
      expect(
        screen.getByText(
          "This booking does not meet the current booking rules. You can update its purpose without changing its reserved times. Changing those times requires following the current rules.",
        ),
      ).toHaveAttribute("role", "status");
      await user.click(screen.getByRole("button", { name: "Save changes" }));
      await waitFor(() => expect(patches).toEqual([{ purpose: "Updated purpose" }]));
    },
  );

  it("saves a compliant reschedule after a maximum duration reduction", async () => {
    const user = userEvent.setup();
    const patches: unknown[] = [];
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/bookings", () => HttpResponse.json(collectionResponse([ownBooking]))),
      http.get("/api/v2/booking-configurations", () =>
        HttpResponse.json(collectionPage([{ ...bookableItemFixtures[0], maxBookingDurationMinutes: 60 }])),
      ),
      http.patch("/api/v2/bookings/41", async ({ request }) => {
        const patch = await request.json();
        patches.push(patch);
        return HttpResponse.json({ ...ownBooking, version: 8, end: "2026-08-17T09:00:00Z" });
      }),
    );
    await renderCalendar();
    await user.click(await screen.findByRole("button", { name: "Agenda" }));
    await user.click(await screen.findByRole("button", { name: /Show details for Confocal microscope/ }));
    const card = screen.getByRole("dialog");
    await user.click(within(card).getByRole("button", { name: "Edit" }));
    await screen.findByRole("textbox", { name: "Purpose" });
    const end = within(card).getByLabelText("End time");
    await user.clear(end);
    await user.type(end, "11:00");
    await user.tab();
    await user.click(screen.getByRole("button", { name: "Save changes" }));
    await waitFor(() => expect(patches).toEqual([{ end: "2026-08-17T09:00:00Z" }]));
  });

  it.each(
    ["network", "server"].flatMap((failure) =>
      ["Agenda", "Time grid", "Resources"].map((layout) => ({ failure, layout })),
    ),
  )(
    "preserves and saves the $layout draft after a lost $failure response moves the booking to another day",
    async ({ failure, layout }) => {
      const user = userEvent.setup();
      let committed = false;
      let saved = false;
      const versions: (string | null)[] = [];
      const patches: unknown[] = [];
      let finishRead: (() => void) | undefined;
      const reading = new Promise<void>((resolve) => {
        finishRead = resolve;
      });
      const updated = {
        ...ownBooking,
        version: 1,
        start: "2026-08-18T09:00:00Z",
        end: "2026-08-18T11:00:00Z",
        purpose: "Server value",
      };
      const savedBooking = { ...ownBooking, version: 2, purpose: "Keep my calendar draft" };
      server.use(
        oauthTokenHandler(true),
        http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
        http.get("/api/v2/bookings", async () => {
          if (committed) await reading;
          return HttpResponse.json(collectionResponse([saved ? savedBooking : committed ? updated : ownBooking]));
        }),
        http.patch("/api/v2/bookings/41", async ({ request }) => {
          versions.push(request.headers.get("If-Match"));
          patches.push(await request.json());
          if (committed) {
            if (request.headers.get("If-Match") !== '"1"') {
              return HttpResponse.json({ status: 412 }, { status: 412 });
            }
            saved = true;
            return HttpResponse.json(savedBooking);
          }
          committed = true;
          return failure === "network" ? HttpResponse.error() : HttpResponse.json({ status: 503 }, { status: 503 });
        }),
      );
      server.use(
        http.get("/api/v2/booking-configurations", () =>
          HttpResponse.json(collectionPage([{ ...bookableItemFixtures[0], maxBookingDurationMinutes: 60 }])),
        ),
      );
      const { queryClient } = await renderCalendar();
      act(() => {
        queryClient.setQueryData(bookingDisplayPreferencesQueryKey, {
          ...inheritedBrowserBookingPreferences,
          timezoneMode: "CUSTOM",
          customTimezone: "Europe/Berlin",
        });
      });
      await user.click(await screen.findByRole("button", { name: layout }));
      await user.click(await screen.findByRole("button", { name: /Show details for Confocal microscope/ }));
      const card = screen.getByRole("dialog");
      await user.click(within(card).getByRole("button", { name: "Edit" }));
      const purpose = await within(card).findByRole("textbox", { name: "Purpose" });
      await user.clear(purpose);
      await user.type(purpose, "Keep my calendar draft");
      const start = within(card).getByLabelText("Start time");
      const originalTime = start.getAttribute("value");
      const save = within(card).getByRole("button", { name: "Save changes" });
      await user.click(save);
      await waitFor(() => expect(committed).toBe(true));

      expect(save).toBeDisabled();
      expect(screen.queryByText("RSpace could not save the booking. Try again.")).not.toBeInTheDocument();
      expect(purpose).toHaveValue("Keep my calendar draft");
      finishRead?.();

      expect(await within(card).findByRole("alert")).toMatchTextContent(
        "RSpace could not confirm whether the booking was saved.",
      );
      expect(
        queryClient
          .getQueriesData<BookingListDocument[]>({
            queryKey: ["api-v2", "bookings", "calendar-events"],
          })
          .map(([, events]) => events),
      ).toContainEqual([expect.objectContaining({ version: 1, start: updated.start, end: updated.end })]);
      expect(within(card).getByRole("textbox", { name: "Purpose" })).toHaveValue("Keep my calendar draft");
      expect(within(card).getByLabelText("Start time")).toHaveValue(originalTime);
      expect(within(card).getByText("Server value")).toBeVisible();
      expect(within(card).getByRole("heading", { name: "11:00 (+1)–13:00 (+1)" })).toBeVisible();
      expect(within(card).getByText("18-08-2026")).toBeVisible();

      await user.click(within(card).getByRole("button", { name: "Save changes" }));
      await waitFor(() => expect(within(card).queryByRole("button", { name: "Save changes" })).not.toBeInTheDocument());
      expect(saved).toBe(true);
      expect(versions).toEqual(['"0"', '"1"']);
      expect(patches[0]).toEqual({ purpose: "Keep my calendar draft" });
      expect(patches[1]).toEqual({
        start: ownBooking.start,
        end: ownBooking.end,
        purpose: "Keep my calendar draft",
      });
    },
  );

  it.each([
    [412, "errors.api.v2.preconditionFailed"],
    [409, "errors.api.v2.booking.concurrentModification"],
  ])("rebases a %i rejection and saves with the refreshed version", async (status, code) => {
    const user = userEvent.setup();
    let rejected = false;
    let saved = false;
    const versions: (string | null)[] = [];
    const updated = { ...ownBooking, version: 1, purpose: "Concurrent edit" };
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/bookings", () =>
        HttpResponse.json(collectionResponse([saved ? { ...updated, version: 2 } : rejected ? updated : ownBooking])),
      ),
      http.patch("/api/v2/bookings/41", ({ request }) => {
        versions.push(request.headers.get("If-Match"));
        if (!rejected) {
          rejected = true;
          return HttpResponse.json({ status, code }, { status });
        }
        if (request.headers.get("If-Match") !== '"1"') {
          return HttpResponse.json({ status: 412 }, { status: 412 });
        }
        saved = true;
        return HttpResponse.json({ ...updated, version: 2 });
      }),
    );
    await renderCalendar();
    await user.click(await screen.findByRole("button", { name: "Agenda" }));
    await user.click(await screen.findByRole("button", { name: /Show details for Confocal microscope/ }));
    const card = screen.getByRole("dialog");
    await user.click(within(card).getByRole("button", { name: "Edit" }));
    await user.type(await within(card).findByRole("textbox", { name: "Purpose" }), " first draft");
    await user.click(within(card).getByRole("button", { name: "Save changes" }));

    const purpose = await within(card).findByDisplayValue("Concurrent edit");
    await user.type(purpose, " updated");
    await user.click(within(card).getByRole("button", { name: "Save changes" }));

    await waitFor(() => expect(within(card).queryByRole("button", { name: "Save changes" })).not.toBeInTheDocument());
    expect(saved).toBe(true);
    expect(versions).toEqual(['"0"', '"1"']);
  });

  it("closes the retained editor when reconciliation revokes edit permission", async () => {
    const user = userEvent.setup();
    let forbidden = false;
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/bookings", () =>
        HttpResponse.json(collectionResponse([{ ...ownBooking, canEdit: !forbidden }])),
      ),
      http.patch("/api/v2/bookings/41", () => {
        forbidden = true;
        return HttpResponse.json({ status: 403, code: "errors.api.v2.forbidden" }, { status: 403 });
      }),
    );
    await renderCalendar();
    await user.click(await screen.findByRole("button", { name: "Agenda" }));
    await user.click(await screen.findByRole("button", { name: /Show details for Confocal microscope/ }));
    const card = screen.getByRole("dialog");
    await user.click(within(card).getByRole("button", { name: "Edit" }));
    const purpose = await within(card).findByRole("textbox", { name: "Purpose" });
    await user.type(purpose, " updated");
    await user.click(within(card).getByRole("button", { name: "Save changes" }));

    await waitFor(() => expect(within(card).queryByRole("textbox", { name: "Purpose" })).not.toBeInTheDocument());
    expect(within(card).queryByRole("button", { name: "Edit" })).not.toBeInTheDocument();
  });

  it("uses one search for calendar events and bookable items", async () => {
    const catalogueSearches: string[] = [];
    server.use(
      http.get("/api/v2/openapi.json", () =>
        HttpResponse.json({
          paths: { ...bookableItemsOpenApi.paths, ...bookingsOpenApi.paths },
          components: bookableItemsOpenApi.components,
        }),
      ),
      ...bookableItemsHandlers((request) => {
        const url = new URL(request.url);
        if (url.pathname === "/api/v2/booking-catalogue") catalogueSearches.push(url.searchParams.get("q") ?? "");
      }),
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/bookings", () => HttpResponse.json(collectionResponse([ownBooking]))),
    );
    const user = userEvent.setup();
    await renderCalendar();
    await screen.findByRole("article", { name: /Confocal microscope · Ada Lovelace/ });

    expect(screen.queryByRole("textbox", { name: "Search Bookable Items" })).not.toBeInTheDocument();
    await user.type(screen.getByRole("textbox", { name: "Search Calendar" }), "Mass");

    expect(screen.getByRole("button", { name: "Jump to date" })).toBeVisible();
    expect(screen.getByRole("button", { name: "Time grid" })).toBeVisible();
    expect(screen.getByRole("region", { name: "Resource booking schedule" })).toBeVisible();
    await waitFor(() => expect(screen.queryByText("Confocal microscope")).not.toBeInTheDocument());
    expect(screen.getByText("Mass spectrometer")).toBeVisible();
    expect(screen.queryByText("No records found")).not.toBeInTheDocument();
    expect(catalogueSearches.filter(Boolean)).toEqual(["Mass"]);
  });

  it("keeps event matches from resources outside the current catalogue page", async () => {
    const ownTarget = ownBooking.target;
    if (!ownTarget) throw new Error("The calendar fixture must include a target");
    const offPageEvent: BookingListDocument = {
      ...ownBooking,
      id: 99,
      target: {
        ...ownTarget,
        globalId: "IN999",
        value: { ...ownTarget.value, id: 999, name: "Off-page microscope" },
      },
      purpose: "QuasarPurposeMarker",
    };
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/bookings", ({ request }) => {
        const where = new URL(request.url).searchParams.get("where") ?? "";
        return HttpResponse.json(
          collectionResponse(where.includes("target=in=") ? [ownBooking] : [ownBooking, offPageEvent]),
        );
      }),
    );

    const user = userEvent.setup();
    await renderCalendar();
    await screen.findByRole("region", { name: "Resource booking schedule" }, { timeout: 3_000 });
    const search = screen.getByRole("textbox", { name: "Search Calendar" });
    await user.clear(search);
    await user.type(search, "QuasarPurposeMarker");

    expect(await screen.findByRole("article", { name: /Off-page microscope/ }, { timeout: 3_000 })).toBeVisible();
    expect(await screen.findByText(/additional resource with a matching event/)).toBeVisible();
    await user.clear(search);
    await user.type(search, "IN999");
    expect(await screen.findByRole("article", { name: /Off-page microscope/ }, { timeout: 3_000 })).toBeVisible();
  });

  it("keeps resource pagination when a search matches more than one page", async () => {
    const catalogueItems = Array.from({ length: 21 }, (_, index) => {
      const fixture = bookableItemFixtures[index % bookableItemFixtures.length];
      return {
        configurationId: 100 + index,
        configurationVersion: fixture.configurationVersion,
        targetType: "INSTRUMENT",
        targetId: 1_000 + index,
        globalId: `IN${900 + index}`,
        name: `No-event microscope ${index + 1}`,
        timezone: fixture.timezone,
        slotGranularityMinutes: fixture.slotGranularityMinutes,
        openingHours: fixture.openingHours,
        bufferBeforeMinutes: fixture.bufferBeforeMinutes,
        bufferAfterMinutes: fixture.bufferAfterMinutes,
        maxBookingDurationMinutes: fixture.maxBookingDurationMinutes,
        allowDoubleBooking: fixture.allowDoubleBooking,
        effectiveRole: fixture.effectiveRole,
        capabilities: fixture.capabilities,
        location: null,
      };
    });
    const catalogueRequests: Array<{ q: string | null; page: number }> = [];
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/bookings", () => HttpResponse.json(collectionResponse([ownBooking]))),
      http.get("/api/v2/booking-catalogue", ({ request }) => {
        const url = new URL(request.url);
        const page = Number(url.searchParams.get("page") ?? "1");
        catalogueRequests.push({ q: url.searchParams.get("q"), page });
        const pageSize = Number(url.searchParams.get("limit") ?? "20");
        const start = (page - 1) * pageSize;
        return HttpResponse.json({
          items: catalogueItems.slice(start, start + pageSize),
          page,
          pageSize,
          total: catalogueItems.length,
          facets: { types: ["INSTRUMENT"] },
        });
      }),
    );

    const user = userEvent.setup();
    await renderCalendar();
    const search = await screen.findByRole("textbox", { name: "Search Calendar" });
    await user.type(search, "No-event");
    // The unfiltered first page lists the same resources; page only once the debounced search has been requested,
    // because applying a search returns to the first page.
    await waitFor(() => expect(catalogueRequests).toContainEqual({ q: "No-event", page: 1 }));

    expect(await screen.findByText("No-event microscope 1")).toBeVisible();
    expect(screen.getByText("1–20 of 21 records")).toBeVisible();
    const nextPage = screen.getByRole("button", { name: "Next page" });
    expect(nextPage).toBeEnabled();
    await user.click(nextPage);
    expect(await screen.findByText("No-event microscope 21")).toBeVisible();
    expect(catalogueRequests.at(-1)).toEqual({ q: "No-event", page: 2 });
  });

  it("shows an empty state when a calendar search has no matches", async () => {
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/bookings", () => HttpResponse.json(collectionResponse([ownBooking]))),
    );

    const user = userEvent.setup();
    await renderCalendar();
    const search = await screen.findByRole("textbox", { name: "Search Calendar" });
    await user.clear(search);
    await user.type(search, "No calendar match");

    expect(await screen.findByText("No records found")).toBeVisible();
  });
});
