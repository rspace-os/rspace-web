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
import { collectionResponse, ownBooking } from "../../mocks/bookingCalendarFixtures";
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

      expect(await within(card).findByRole("alert")).toHaveTextContent(
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

  it("keeps bookable items and calendar controls when booking search has no matches", async () => {
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/bookings", () => HttpResponse.json(collectionResponse([ownBooking]))),
    );
    const user = userEvent.setup();
    await renderCalendar();
    await screen.findByRole("article", { name: /Confocal microscope · Ada Lovelace/ });

    await user.type(screen.getByRole("textbox", { name: "Search Calendar" }), "no matching booking");

    expect(screen.getByRole("button", { name: "Jump to date" })).toBeVisible();
    expect(screen.getByRole("button", { name: "Time grid" })).toBeVisible();
    expect(screen.getByRole("region", { name: "Resource booking schedule" })).toBeVisible();
    expect(screen.getByText("Mass spectrometer")).toBeVisible();
    expect(screen.queryByText("No records found")).not.toBeInTheDocument();
  });
});
