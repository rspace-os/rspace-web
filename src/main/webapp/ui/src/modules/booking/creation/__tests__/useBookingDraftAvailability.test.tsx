import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import type { PropsWithChildren } from "react";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import type { BookingWindowDraft } from "@/modules/booking/domain/bookingTime";
import type { BookingFormState } from "../BookingForm";
import type { BookableItemOption } from "../bookableItemOption";
import { useBookingDraftAvailability } from "../useBookingDraftAvailability";

const target: BookableItemOption = {
  configurationId: 7,
  targetId: 123,
  globalId: "IN123",
  name: "Confocal microscope",
  timezone: "Europe/Berlin",
  slotGranularityMinutes: 5,
  openingStart: "00:00",
  openingEnd: "24:00",
  openDays: [1, 2, 3, 4, 5, 6, 7],
  openingExceptions: [],
  bufferBeforeMinutes: 0,
  bufferAfterMinutes: 0,
  maxBookingDurationMinutes: 0,
  allowDoubleBooking: false,
};

const draft: BookingWindowDraft = {
  startDate: "2026-10-19",
  startTime: "10:00",
  endDate: "2026-10-19",
  endTime: "11:00",
};

function booking({
  id,
  start,
  end,
  kind = "BOOKING",
}: {
  id: number;
  start: string;
  end: string;
  kind?: "BOOKING" | "MAINTENANCE";
}) {
  return {
    id,
    version: 0,
    target: {
      relationTo: "booking-instruments",
      value: { id: 123, name: target.name, deleted: false },
      globalId: target.globalId,
    },
    timezone: target.timezone,
    start,
    end,
    state: "CONFIRMED",
    kind,
    privacy: "full",
    purpose: `Purpose ${id}`,
    bookedBy: "Ada Lovelace",
    canEdit: false,
    canCancel: false,
    createdAt: "2026-10-01T09:00:00Z",
    updatedAt: "2026-10-01T09:00:00Z",
  };
}

function page(docs: readonly unknown[]) {
  return HttpResponse.json({
    docs,
    totalDocs: docs.length,
    totalPages: docs.length ? 1 : 0,
    page: 1,
    hasNextPage: false,
  });
}

function formState({
  item = target,
  value = draft,
  start = "2026-10-19T08:00:00Z",
  end = "2026-10-19T09:00:00Z",
  eventKind = "BOOKING",
  meetsRules = true,
}: {
  item?: BookableItemOption;
  value?: BookingWindowDraft;
  start?: string;
  end?: string;
  eventKind?: "BOOKING" | "MAINTENANCE";
  meetsRules?: boolean;
} = {}): BookingFormState {
  return {
    target: item,
    draft: value,
    window: meetsRules ? { start, end } : undefined,
    enteredWindow: { start, end },
    purpose: "Purpose",
    eventKind,
    dirty: true,
  };
}

function renderAvailability({
  state = formState(),
  originalWindow,
  excludedBookingId,
}: {
  state?: BookingFormState;
  originalWindow?: { start: string; end: string };
  excludedBookingId?: number;
} = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const wrapper = ({ children }: PropsWithChildren) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
  const hook = renderHook(
    () =>
      useBookingDraftAvailability({
        formState: state,
        displayTimezone: "Europe/Berlin",
        token: "test-token",
        eventKind: state.eventKind,
        originalWindow,
        excludedBookingId,
      }),
    { wrapper },
  );
  return { ...hook, client };
}

describe("useBookingDraftAvailability", () => {
  it("checks conflicts as soon as both endpoints are entered, before the item's rules are met", async () => {
    server.use(
      http.get("/api/v2/bookings", () =>
        page([booking({ id: 1, start: "2026-10-19T08:30:00Z", end: "2026-10-19T09:30:00Z" })]),
      ),
    );
    const { result } = renderAvailability({ state: formState({ meetsRules: false }) });

    await waitFor(() => expect(result.current.checking).toBe(false));
    expect(result.current.conflicts).toHaveLength(1);
    expect(result.current.blocksSubmission).toBe(true);
  });

  it("does not treat the item's closed hours as an overlap", async () => {
    server.use(http.get("/api/v2/bookings", () => page([])));
    // 08:00Z to 09:00Z is 10:00 to 11:00 in Berlin, after this item closes.
    const { result } = renderAvailability({
      state: formState({ item: { ...target, openingStart: "08:00", openingEnd: "09:00" }, meetsRules: false }),
    });

    await waitFor(() => expect(result.current.checking).toBe(false));
    expect(result.current.violation).toBe(false);
    expect(result.current.conflicts).toHaveLength(0);
    expect(result.current.blocksSubmission).toBe(false);
  });

  it("allows ordinary overlap with a warning when double booking is enabled", async () => {
    server.use(
      http.get("/api/v2/bookings", () =>
        page([booking({ id: 1, start: "2026-10-19T08:30:00Z", end: "2026-10-19T09:30:00Z" })]),
      ),
    );
    const { result } = renderAvailability({ state: formState({ item: { ...target, allowDoubleBooking: true } }) });

    await waitFor(() => expect(result.current.checking).toBe(false));
    expect(result.current.conflicts).toHaveLength(1);
    expect(result.current.blocksSubmission).toBe(false);
    expect(result.current.conflictSeverity).toBe("warning");
  });

  it("blocks maintenance over an ordinary booking even when the item permits double booking", async () => {
    server.use(
      http.get("/api/v2/bookings", () =>
        page([booking({ id: 1, start: "2026-10-19T08:30:00Z", end: "2026-10-19T09:30:00Z" })]),
      ),
    );
    const { result } = renderAvailability({
      state: formState({ item: { ...target, allowDoubleBooking: true }, eventKind: "MAINTENANCE" }),
    });

    await waitFor(() => expect(result.current.checking).toBe(false));
    expect(result.current.blocksSubmission).toBe(true);
    expect(result.current.conflictSeverity).toBe("error");
  });

  it("applies the target buffer when deciding whether a booking overlaps", async () => {
    server.use(
      http.get("/api/v2/bookings", () =>
        page([booking({ id: 2, start: "2026-10-19T07:50:00Z", end: "2026-10-19T07:55:00Z" })]),
      ),
    );
    const { result } = renderAvailability({
      state: formState({ item: { ...target, bufferAfterMinutes: 10, allowDoubleBooking: true } }),
    });

    await waitFor(() => expect(result.current.checking).toBe(false));
    expect(result.current.conflicts).toHaveLength(1);
    expect(result.current.blocksSubmission).toBe(false);
    expect(result.current.conflictSeverity).toBe("warning");
  });

  it("excludes the booking being edited from its own availability conflicts", async () => {
    const current = booking({ id: 41, start: "2026-10-19T08:00:00Z", end: "2026-10-19T10:00:00Z" });
    server.use(http.get("/api/v2/bookings", () => page([current])));
    const { result } = renderAvailability({
      state: formState({ start: "2026-10-19T08:05:00Z", end: "2026-10-19T10:05:00Z" }),
      originalWindow: { start: current.start, end: current.end },
      excludedBookingId: current.id,
    });

    await waitFor(() => expect(result.current.checking).toBe(false));
    expect(result.current.violation).toBe(false);
    expect(result.current.conflicts).toEqual([]);
    expect(result.current.blocksSubmission).toBe(false);
  });

  it("allows submission with a warning when the availability request fails", async () => {
    server.use(http.get("/api/v2/bookings", () => new HttpResponse(null, { status: 503 })));
    const { result } = renderAvailability();

    await waitFor(() => expect(result.current.failed).toBe(true));
    expect(result.current.checking).toBe(false);
    expect(result.current.blocksSubmission).toBe(false);
  });
});
