import { beforeAll as beforeBookingCatalog } from "vitest";
import { bookingKeys } from "@/modules/booking/domain/bookingKeys";
import appI18n from "@/modules/common/i18n";

beforeBookingCatalog(async () => {
  appI18n.addResourceBundle(
    "en-US",
    "booking",
    (await import("@/modules/common/i18n/locales/en-US/booking.json")).default,
  );
});

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
import { busyBooking, collectionResponse, ownBooking } from "../../mocks/bookingCalendarFixtures";
import { bookingsOpenApi } from "../../my-bookings/mocks/bookingMocks";
import { inheritedBrowserBookingPreferences } from "../../preferences/bookingPreferencesFixtures";
import { restoreCalendarScrollTo, setupCalendarBookableItems, setupCalendarScrollTo } from "./calendarPageTestUtils";
import { renderCalendar } from "./calendarTestHarness";

beforeAll(() => setupCalendarScrollTo());
beforeEach(() => setupCalendarBookableItems());
afterAll(() => restoreCalendarScrollTo());

/** Chooses a layout in the View menu, then closes it; an open menu makes the rest of the page inert. */
async function chooseLayout(user: ReturnType<typeof userEvent.setup>, layout: string) {
  await user.click(await screen.findByRole("button", { name: /^View: / }));
  await user.click(await screen.findByRole("menuitemradio", { name: layout }));
  await user.keyboard("{Escape}");
  await waitFor(() => expect(screen.queryByRole("menu")).not.toBeInTheDocument());
}

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
        http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([booking]))),
        http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionPage([configuration]))),
        http.patch("/api/v2/bookings/41", async ({ request }) => {
          patches.push(await request.json());
          return HttpResponse.json({ ...ownBooking, version: 8, purpose: "Updated purpose" });
        }),
      );
      await renderCalendar();
      await chooseLayout(user, "Agenda");
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
      http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([ownBooking]))),
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
    await chooseLayout(user, "Agenda");
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
      ["Agenda", "Time grid", "By Item"].map((layout) => ({ failure, layout })),
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
        http.get("/api/v2/booking-calendar/events", async () => {
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
            return HttpResponse.json({ ...savedBooking, cancellationReason: null });
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
      await chooseLayout(user, layout);
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
            queryKey: bookingKeys.calendarEvents(),
          })
          .map(([, events]) => events),
      ).toContainEqual([expect.objectContaining({ version: 1, start: updated.start, end: updated.end })]);
      expect(within(card).getByRole("textbox", { name: "Purpose" })).toHaveValue("Keep my calendar draft");
      expect(within(card).getByLabelText("Start time")).toHaveValue(originalTime);
      expect(within(card).getByText("Server value")).toBeVisible();
      expect(within(card).getByRole("heading", { name: "11:00 AM (+1)–01:00 PM (+1)" })).toBeVisible();
      expect(within(card).getByText("08/18/2026")).toBeVisible();

      await user.click(within(card).getByRole("button", { name: "Save changes" }));
      await waitFor(() => expect(within(card).queryByRole("button", { name: "Save changes" })).not.toBeInTheDocument());
      expect(saved).toBe(true);
      expect(versions).toEqual(['"0"', '"1"']);
      expect(patches[0]).toEqual({ purpose: "Keep my calendar draft" });
      expect(patches[1]).toEqual({ purpose: "Keep my calendar draft" });
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
      http.get("/api/v2/booking-calendar/events", () =>
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
        return HttpResponse.json({ ...updated, version: 2, cancellationReason: null });
      }),
    );
    await renderCalendar();
    await chooseLayout(user, "Agenda");
    await user.click(await screen.findByRole("button", { name: /Show details for Confocal microscope/ }));
    const card = screen.getByRole("dialog");
    await user.click(within(card).getByRole("button", { name: "Edit" }));
    await user.type(await within(card).findByRole("textbox", { name: "Purpose" }), " first draft");
    await user.click(within(card).getByRole("button", { name: "Save changes" }));

    expect(await within(card).findByRole("alert")).toMatchTextContent(
      "Someone else changed this booking while you were editing. Your changes are kept",
    );
    await waitFor(() => expect(within(card).getByRole("button", { name: "Save changes" })).toBeEnabled());
    const purpose = within(card).getByRole("textbox", { name: "Purpose" });
    expect(purpose).toHaveValue(`${ownBooking.purpose} first draft`);
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
      http.get("/api/v2/booking-calendar/events", () =>
        HttpResponse.json(collectionResponse([{ ...ownBooking, canEdit: !forbidden }])),
      ),
      http.patch("/api/v2/bookings/41", () => {
        forbidden = true;
        return HttpResponse.json({ status: 403, code: "errors.api.v2.forbidden" }, { status: 403 });
      }),
    );
    await renderCalendar();
    await chooseLayout(user, "Agenda");
    await user.click(await screen.findByRole("button", { name: /Show details for Confocal microscope/ }));
    const card = screen.getByRole("dialog");
    await user.click(within(card).getByRole("button", { name: "Edit" }));
    const purpose = await within(card).findByRole("textbox", { name: "Purpose" });
    await user.type(purpose, " updated");
    await user.click(within(card).getByRole("button", { name: "Save changes" }));

    await waitFor(() => expect(within(card).queryByRole("textbox", { name: "Purpose" })).not.toBeInTheDocument());
    expect(within(card).queryByRole("button", { name: "Edit" })).not.toBeInTheDocument();
  });

  it("explains a server buffer rejection in the inline calendar editor and lists the named booking", async () => {
    server.use(
      http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([ownBooking]))),
      http.patch("/api/v2/bookings/41", () =>
        HttpResponse.json(
          {
            status: 409,
            code: "errors.api.v2.booking.buffer",
            detail: "private server detail",
            conflict: { id: 59, kind: "BOOKING", start: "2026-08-17T11:00:00Z", end: "2026-08-17T12:00:00Z" },
            bufferBeforeMinutes: 0,
            bufferAfterMinutes: 30,
          },
          { status: 409 },
        ),
      ),
    );
    const user = userEvent.setup();
    await renderCalendar();

    await chooseLayout(user, "Agenda");
    await user.click((await screen.findAllByRole("button", { name: /^Show details for Confocal microscope/ }))[0]);
    await user.click(await screen.findByRole("button", { name: "Edit" }));
    await user.type(await screen.findByRole("textbox", { name: "Purpose" }), " updated");
    const save = screen.getByRole("button", { name: "Save changes" });
    await user.click(save);

    const alert = await screen.findByRole("alert", {}, { timeout: 3_000 });
    await waitFor(() => expect(alert).toMatchTextContent("Too close to another booking"));
    expect(alert).not.toMatchTextContent("private server detail");
    expect(within(alert).getByRole("listitem")).toMatchTextContent("Booking #59");
    expect(save).toBeDisabled();
  });

  it("keeps an inline edit through a stale-version rejection and saves it against the refreshed version", async () => {
    let serverBooking: BookingListDocument = ownBooking;
    const saves: { ifMatch: string | null; payload: unknown }[] = [];
    server.use(
      http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([serverBooking]))),
      http.patch("/api/v2/bookings/41", async ({ request }) => {
        const ifMatch = request.headers.get("If-Match");
        const payload = (await request.json()) as Partial<BookingListDocument>;
        saves.push({ ifMatch, payload });
        if (ifMatch !== `"${serverBooking.version}"`) {
          return HttpResponse.json(
            { status: 412, code: "errors.api.v2.preconditionFailed", detail: "private server detail" },
            { status: 412 },
          );
        }
        serverBooking = { ...serverBooking, ...payload, version: serverBooking.version + 1 };
        return HttpResponse.json({ ...serverBooking, cancellationReason: null });
      }),
    );
    const user = userEvent.setup();
    await renderCalendar();

    await user.click((await screen.findAllByRole("button", { name: /^Show details for Confocal microscope/ }))[0]);
    await user.click(await screen.findByRole("button", { name: "Edit" }));
    await user.type(await screen.findByRole("textbox", { name: "Purpose" }), " updated");
    // Someone else edits the booking before this save reaches the server.
    serverBooking = { ...serverBooking, purpose: "Changed elsewhere", version: 1 };
    await user.click(screen.getByRole("button", { name: "Save changes" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toMatchTextContent("Someone else changed this booking while you were editing. Your changes are kept");
    expect(alert).not.toMatchTextContent("private server detail");
    await waitFor(() => expect(alert).toHaveFocus());
    expect(screen.getByRole("textbox", { name: "Purpose" })).toHaveValue("Cell imaging updated");
    await waitFor(() => expect(screen.getByRole("button", { name: "Save changes" })).toBeEnabled());

    // The explanation lasts while the draft is edited further; it is not the save's transient error.
    await user.type(screen.getByRole("textbox", { name: "Purpose" }), "!");
    expect(screen.getByRole("alert")).toMatchTextContent("Someone else changed this booking while you were editing.");
    await user.click(screen.getByRole("button", { name: "Save changes" }));

    await waitFor(() => expect(saves.map(({ ifMatch }) => ifMatch)).toEqual(['"0"', '"1"']));
    expect(saves[1].payload).toEqual({ purpose: "Cell imaging updated!" });
    await waitFor(() => expect(screen.queryByRole("textbox", { name: "Purpose" })).not.toBeInTheDocument());
  });

  it("shows the matching purpose on collapsed Agenda cards while a search is applied", async () => {
    const auroraBooking: BookingListDocument = { ...ownBooking, purpose: "Spring Aurora imaging run" };
    const otherPurpose: BookingListDocument = {
      ...ownBooking,
      id: 46,
      start: "2026-08-18T08:00:00Z",
      end: "2026-08-18T09:00:00Z",
      purpose: "Calibration",
    };
    server.use(
      http.get("/api/v2/booking-calendar/events", () =>
        HttpResponse.json(collectionResponse([auroraBooking, otherPurpose, busyBooking])),
      ),
    );
    const user = userEvent.setup();
    await renderCalendar("/booking/calendar?date=2026-08-17&layout=agenda&view=week&calendar-resources.q=aurora");

    const agenda = await screen.findByRole("region", { name: "Booking agenda" });
    const highlight = await within(agenda).findByText("Aurora", { selector: "mark" });
    expect(highlight.closest("[data-calendar-search-match]")).toHaveTextContent("PurposeSpring Aurora imaging run");
    // Only the card whose purpose matched shows it; the others keep their purpose (or busy state) hidden.
    expect(within(agenda).getAllByRole("article")).toHaveLength(3);
    expect(agenda.querySelectorAll("[data-calendar-search-match]")).toHaveLength(1);
    expect(within(agenda).queryByText("Calibration")).not.toBeInTheDocument();

    await user.clear(screen.getByRole("textbox", { name: "Search Calendar" }));
    await waitFor(() => expect(agenda.querySelectorAll("[data-calendar-search-match]")).toHaveLength(0));
    expect(within(agenda).queryByText("Spring Aurora imaging run", { exact: false })).not.toBeInTheDocument();
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
        if (url.pathname === "/api/v2/booking-catalogue/calendar")
          catalogueSearches.push(url.searchParams.get("q") ?? "");
      }),
      http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([ownBooking]))),
    );
    const user = userEvent.setup();
    await renderCalendar();
    await screen.findByRole("article", { name: /Confocal microscope · Ada Lovelace/ });

    expect(screen.queryByRole("textbox", { name: "Search Bookable Items" })).not.toBeInTheDocument();
    await user.type(screen.getByRole("textbox", { name: "Search Calendar" }), "Mass");

    expect(screen.getByRole("button", { name: "Jump to date" })).toBeVisible();
    expect(screen.getByRole("button", { name: "View: By Item · Day" })).toBeVisible();
    expect(screen.getByRole("region", { name: "Resource booking schedule" })).toBeVisible();
    await waitFor(() => expect(screen.queryByText("Confocal microscope")).not.toBeInTheDocument());
    expect(await screen.findByText("Mass spectrometer")).toBeVisible();
    expect(screen.queryByText("No records found")).not.toBeInTheDocument();
    expect(catalogueSearches.filter(Boolean)).toEqual(["Mass"]);
  });

  it("loads purpose-matching resources from server pagination rather than appending off-page events", async () => {
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
      http.get("/api/v2/booking-catalogue/calendar", ({ request }) => {
        const query = new URL(request.url).searchParams.get("q");
        const item = bookableItemFixtures[0];
        return HttpResponse.json({
          items: [
            {
              ...item,
              configurationId: item.id,
              targetId: query ? 999 : 123,
              targetType: "INSTRUMENT",
              globalId: query ? "IN999" : "IN123",
              name: query ? "Off-page microscope" : "Confocal microscope",
              location: null,
            },
          ],
          total: 1,
          page: 1,
          pageSize: 20,
          facets: { types: ["INSTRUMENT"] },
        });
      }),
      http.get("/api/v2/booking-calendar/events", ({ request }) => {
        const url = new URL(request.url);
        const matching = url.searchParams.get("q");
        if (matching) expect(url.searchParams.get("where")).toContain("target=in=(IN999)");
        return HttpResponse.json(collectionResponse([matching ? offPageEvent : ownBooking]));
      }),
    );

    const user = userEvent.setup();
    await renderCalendar();
    await screen.findByRole("region", { name: "Resource booking schedule" }, { timeout: 3_000 });
    const search = screen.getByRole("textbox", { name: "Search Calendar" });
    await user.clear(search);
    await user.type(search, "QuasarPurposeMarker");

    expect(await screen.findByRole("article", { name: /Off-page microscope/ }, { timeout: 3_000 })).toBeVisible();
    expect(await screen.findByText("1–1 of 1 record")).toBeVisible();
    await user.clear(search);
    await user.type(search, "IN999");
    expect(await screen.findByRole("article", { name: /Off-page microscope/ }, { timeout: 3_000 })).toBeVisible();
  });

  it("keeps resource pagination when a search matches more than one page", async () => {
    const catalogueRequests: { query: string | null; page: number }[] = [];
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
        incrementUnit: "MINUTES" as const,
        alignToIncrement: true,
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
    server.use(
      http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([ownBooking]))),
      http.get("/api/v2/booking-catalogue/calendar", ({ request }) => {
        const url = new URL(request.url);
        const query = url.searchParams.get("q");
        const page = Number(url.searchParams.get("page") ?? "1");
        const pageSize = Number(url.searchParams.get("limit") ?? "20");
        catalogueRequests.push({ query, page });
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
    await waitFor(() => expect(catalogueRequests).toContainEqual({ q: "No-event", page: 1 }), { timeout: 3_000 });

    expect(await screen.findByText("No-event microscope 1")).toBeVisible();
    expect(screen.getByText("1–20 of 21 records")).toBeVisible();
    const nextPage = screen.getByRole("button", { name: "Next page" });
    expect(nextPage).toBeEnabled();
    await user.click(nextPage);
    await waitFor(() => expect(catalogueRequests).toContainEqual({ query: "No-event", page: 2 }));
    await waitFor(() => expect(screen.getByText("No-event microscope 21")).toBeVisible());
    expect(catalogueRequests.at(-1)).toEqual({ query: "No-event", page: 2 });
  });

  it("shows an empty state when a calendar search has no matches", async () => {
    server.use(http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([ownBooking]))));

    const user = userEvent.setup();
    await renderCalendar();
    const search = await screen.findByRole("textbox", { name: "Search Calendar" });
    await user.clear(search);
    await user.type(search, "No calendar match");

    expect(await screen.findByText("No records found", {}, { timeout: 3_000 })).toBeVisible();
  });
});
