import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { Suspense, useState } from "react";
import { afterAll, beforeAll, describe, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import { todayInTimeZone } from "@/modules/booking/domain/bookingDisplayPreferences";
import { type BookingWindowDraft, zonedDayBounds } from "@/modules/booking/domain/bookingTime";
import { BookingDayTimelineAside } from "../BookingDayTimelineAside";
import type { BookableItemOption } from "../bookableItemOption";

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

beforeAll(() => vi.stubGlobal("ResizeObserver", ResizeObserverStub));
afterAll(() => vi.unstubAllGlobals());

const target: BookableItemOption = {
  configurationId: 7,
  targetId: 123,
  globalId: "IN123",
  name: "Confocal microscope",
  timezone: "Europe/Berlin",
  slotGranularityMinutes: 5,
  openingStart: "06:00",
  openingEnd: "22:00",
  openDays: [1, 2, 3, 4, 5, 6, 7],
  openingExceptions: [],
  bufferBeforeMinutes: 0,
  bufferAfterMinutes: 0,
  maxBookingDurationMinutes: 0,
  allowDoubleBooking: false,
};

const draft: BookingWindowDraft = {
  startDate: "2026-08-17",
  startTime: "09:00",
  endDate: "2026-08-17",
  endTime: "10:00",
};

const ignoreChange = (_next: BookingWindowDraft) => {};

function queryClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

function emptyPage() {
  return HttpResponse.json({ docs: [], totalDocs: 0, totalPages: 0, page: 1, hasNextPage: false });
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}

function scheduleBooking({ id, targetId, name }: { id: number; targetId: string; name: string }) {
  return {
    id,
    version: 0,
    target: {
      relationTo: "booking-instruments",
      globalId: targetId,
      value: { id, name, deleted: false },
    },
    timezone: "Europe/Berlin",
    start: "2026-08-17T07:00:00Z",
    end: "2026-08-17T08:00:00Z",
    state: "CONFIRMED",
    kind: "BOOKING",
    privacy: "full",
    purpose: "Private purpose",
    bookedBy: "Ada Lovelace",
    canEdit: false,
    canCancel: false,
    createdAt: "2026-08-17T06:00:00Z",
    updatedAt: "2026-08-17T06:00:00Z",
  };
}

function pageWith(booking: ReturnType<typeof scheduleBooking>) {
  return HttpResponse.json({ docs: [booking], totalDocs: 1, totalPages: 1, page: 1, hasNextPage: false });
}

function asideTree({
  client,
  selectedTarget = target,
  selectedDraft = draft,
  onChange = ignoreChange,
}: {
  client: QueryClient;
  selectedTarget?: BookableItemOption | undefined;
  selectedDraft?: BookingWindowDraft;
  onChange?: (next: BookingWindowDraft) => void;
}) {
  return (
    <QueryClientProvider client={client}>
      <Suspense fallback={null}>
        <BookingDayTimelineAside
          target={selectedTarget}
          draft={selectedDraft}
          timezone="Europe/Berlin"
          token="test-token"
          onChange={onChange}
        />
      </Suspense>
    </QueryClientProvider>
  );
}

function StatefullyControlledAside({
  client,
  onChange,
}: {
  client: QueryClient;
  onChange: (next: BookingWindowDraft) => void;
}) {
  const [currentDraft, setCurrentDraft] = useState(draft);
  return (
    <QueryClientProvider client={client}>
      <Suspense fallback={null}>
        <BookingDayTimelineAside
          target={target}
          draft={currentDraft}
          timezone="Europe/Berlin"
          token="test-token"
          onChange={(next) => {
            onChange(next);
            setCurrentDraft(next);
          }}
        />
      </Suspense>
    </QueryClientProvider>
  );
}

async function expectVisibleOccurrence(text: string) {
  const occurrences = await screen.findAllByText(text);
  const visible = occurrences.find((element) => element.closest("[hidden]") === null);
  expect(visible).toBeVisible();
}

describe("BookingDayTimelineAside acceptance", () => {
  it.each([
    [
      "the shared hours",
      target,
      [
        { top: "0px", height: "336px" },
        { top: "1232px", height: "112px" },
      ],
    ],
    [
      "a weekday exception",
      { ...target, openingExceptions: [{ dayOfWeek: 1, start: "10:00", end: "12:00" }] },
      [
        { top: "0px", height: "560px" },
        { top: "672px", height: "672px" },
      ],
    ],
    ["a closed weekday", { ...target, openDays: [2, 3, 4, 5, 6, 7] }, [{ top: "0px", height: "1344px" }]],
  ])("shades the closed periods of %s on the draft day", async (_, selectedTarget, expected) => {
    server.use(http.get("/api/v2/bookings", () => emptyPage()));
    render(asideTree({ client: queryClient(), selectedTarget }));

    await expectVisibleOccurrence("booking:dayTimeline.vertical.empty");
    // 2026-08-17 is a Monday; the timeline is 56 px per hour.
    const canvas = screen.getAllByTestId("vertical-day-timeline-canvas")[0];
    expect(
      within(canvas)
        .getAllByTestId("vertical-day-timeline-closed-hours")
        .map((segment) => ({ top: segment.style.top, height: segment.style.height })),
    ).toEqual(expected);
  });

  it("keeps the loading state visible until the day schedule resolves", async () => {
    const request = deferred<Response>();
    let requests = 0;
    server.use(
      http.get("/api/v2/bookings", () => {
        requests += 1;
        return request.promise;
      }),
    );

    const client = queryClient();
    render(asideTree({ client }));

    expect(await screen.findByRole("status")).toHaveTextContent("booking:dayTimeline.vertical.loading");
    expect(requests).toBe(1);

    request.resolve(emptyPage());
    await expectVisibleOccurrence("booking:dayTimeline.vertical.empty");
  });

  it("offers retry after a schedule error and replaces the error with the loaded schedule", async () => {
    const user = userEvent.setup();
    let requests = 0;
    server.use(
      http.get("/api/v2/bookings", () => {
        requests += 1;
        return requests === 1 ? new HttpResponse(null, { status: 503 }) : emptyPage();
      }),
    );

    const client = queryClient();
    render(asideTree({ client }));

    expect(await screen.findByRole("alert")).toHaveTextContent("booking:dayTimeline.vertical.loadError");
    expect(requests).toBe(1);
    await user.click(screen.getByRole("button", { name: "booking:dayTimeline.vertical.retry" }));

    await expectVisibleOccurrence("booking:dayTimeline.vertical.empty");
    expect(requests).toBe(2);
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("falls back to today in the display timezone when the draft date is invalid", async () => {
    let where: string | null = null;
    server.use(
      http.get("/api/v2/bookings", ({ request }) => {
        where = new URL(request.url).searchParams.get("where");
        return emptyPage();
      }),
    );

    const client = queryClient();
    render(
      asideTree({
        client,
        selectedDraft: { ...draft, startDate: "not-a-date", endDate: "not-a-date" },
      }),
    );

    await expectVisibleOccurrence("booking:dayTimeline.vertical.empty");
    const date = todayInTimeZone("Europe/Berlin");
    const bounds = zonedDayBounds(date, "Europe/Berlin");
    expect(where).toContain(`start=lt=${bounds.end};end=gt=${bounds.start}`);
  });

  it("does not let an in-flight previous-target response replace the current schedule", async () => {
    const staleResponse = deferred<Response>();
    const requestedTargets: string[] = [];
    const nextTarget: BookableItemOption = {
      ...target,
      targetId: 124,
      globalId: "IN124",
      name: "Widefield microscope",
    };
    server.use(
      http.get("/api/v2/bookings", ({ request }) => {
        const where = new URL(request.url).searchParams.get("where") ?? "";
        if (where.includes("IN123")) {
          requestedTargets.push("IN123");
          return staleResponse.promise;
        }
        requestedTargets.push("IN124");
        return pageWith(scheduleBooking({ id: 2, targetId: "IN124", name: "Current target booking" }));
      }),
    );

    const client = queryClient();
    const view = render(asideTree({ client }));
    expect(await screen.findByRole("status")).toHaveTextContent("booking:dayTimeline.vertical.loading");
    expect(requestedTargets).toContain("IN123");

    view.rerender(asideTree({ client, selectedTarget: nextTarget }));
    await expectVisibleOccurrence("Current target booking");

    staleResponse.resolve(pageWith(scheduleBooking({ id: 1, targetId: "IN123", name: "Previous target booking" })));
    expect(screen.queryByText("Previous target booking")).not.toBeInTheDocument();
    expect(requestedTargets).toContain("IN124");
  });

  it("keeps a cross-day draft visible without day-canvas adjustment handles", async () => {
    server.use(http.get("/api/v2/bookings", emptyPage));
    const crossDayDraft: BookingWindowDraft = {
      startDate: "2026-08-17",
      startTime: "23:00",
      endDate: "2026-08-18",
      endTime: "01:00",
    };
    const client = queryClient();
    render(asideTree({ client, selectedDraft: crossDayDraft }));

    expect(screen.getByTestId("vertical-day-timeline-draft")).toBeVisible();
    expect(screen.queryByRole("button", { name: /booking:dayTimeline.vertical.moveDraft/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /booking:dayTimeline.vertical.resizeStart/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /booking:dayTimeline.vertical.resizeEnd/ })).not.toBeInTheDocument();
  });

  it("the floating schedule button opens and closes the sheet without changing the draft or refetching", async () => {
    const user = userEvent.setup();
    let requests = 0;
    const onChange = vi.fn();
    server.use(
      http.get("/api/v2/bookings", () => {
        requests += 1;
        return emptyPage();
      }),
    );

    const client = queryClient();
    render(asideTree({ client, onChange }));
    const toggle = await screen.findByRole("button", { name: "booking:dayTimeline.vertical.schedule" });
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    await user.click(toggle);
    expect(toggle).toHaveAttribute("aria-expanded", "true");
    const dialog = screen.getByRole("dialog", { name: "booking:dayTimeline.vertical.schedule" });
    expect(within(dialog).getByTestId("vertical-day-timeline-canvas")).toBeVisible();

    await user.keyboard("{Escape}");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    expect(toggle).toHaveFocus();
    await user.click(toggle);
    expect(toggle).toHaveAttribute("aria-expanded", "true");
    expect(within(screen.getByRole("dialog")).getByTestId("vertical-day-timeline-canvas")).toBeVisible();
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "common:actions.close" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(toggle).toHaveFocus();
    expect(onChange).not.toHaveBeenCalled();
    expect(requests).toBe(1);
  });

  it("keeps the day schedule query stable while the draft moves", async () => {
    const user = userEvent.setup();
    let requests = 0;
    const onChange = vi.fn();
    server.use(
      http.get("/api/v2/bookings", () => {
        requests += 1;
        return emptyPage();
      }),
    );

    render(<StatefullyControlledAside client={queryClient()} onChange={onChange} />);
    await expectVisibleOccurrence("booking:dayTimeline.vertical.empty");

    screen.getByRole("button", { name: /booking:dayTimeline.vertical.moveDraft/ }).focus();
    await user.keyboard("{ArrowDown}");

    expect(onChange).toHaveBeenCalledOnce();
    expect(onChange).toHaveBeenCalledWith({
      startDate: "2026-08-17",
      startTime: "09:05",
      endDate: "2026-08-17",
      endTime: "10:05",
    });
    expect(requests).toBe(1);
  });
});
