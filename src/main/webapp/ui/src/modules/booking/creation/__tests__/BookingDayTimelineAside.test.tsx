import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { Suspense, useRef, useState } from "react";
import { afterAll, beforeAll, describe, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import { todayInTimeZone } from "@/modules/booking/domain/bookingDisplayPreferences";
import { type BookingWindowDraft, zonedDayBounds } from "@/modules/booking/domain/bookingTime";
import { BookingDayTimelineAside } from "../BookingDayTimelineAside";
import type { BookableItemOption } from "../bookableItemOption";
import { confocalBooking, confocalBookingPage, confocalTargetWithHours } from "./creationTestFixtures";

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

beforeAll(() => vi.stubGlobal("ResizeObserver", ResizeObserverStub));
afterAll(() => vi.unstubAllGlobals());

const target = confocalTargetWithHours("06:00", "22:00");

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

function scheduleBooking({ id, targetId, name }: { id: number; targetId: string; name: string }) {
  return confocalBooking({
    id,
    start: "2026-08-17T07:00:00Z",
    end: "2026-08-17T08:00:00Z",
    targetGlobalId: targetId,
    targetValueId: id,
    targetName: name,
    timezone: "Europe/Berlin",
    purpose: "Private purpose",
  });
}

function AsideWithForm({
  selectedTarget,
  selectedDraft,
  timezone,
  onChange,
}: {
  selectedTarget?: BookableItemOption;
  selectedDraft?: BookingWindowDraft;
  timezone: string;
  onChange: (next: BookingWindowDraft) => void;
}) {
  const formContainerRef = useRef<HTMLDivElement>(null);
  return (
    <div className="@container">
      <div ref={formContainerRef}>
        <form aria-hidden="true" />
      </div>
      <BookingDayTimelineAside
        target={selectedTarget}
        formContainerRef={formContainerRef}
        draft={selectedDraft ?? draft}
        timezone={timezone}
        token="test-token"
        onChange={onChange}
      />
    </div>
  );
}

function asideTree({
  client,
  selectedTarget = target,
  selectedDraft = draft,
  timezone = "Europe/Berlin",
  onChange = ignoreChange,
}: {
  client: QueryClient;
  selectedTarget?: BookableItemOption | undefined;
  selectedDraft?: BookingWindowDraft;
  timezone?: string;
  onChange?: (next: BookingWindowDraft) => void;
}) {
  return (
    <QueryClientProvider client={client}>
      <Suspense fallback={null}>
        <AsideWithForm
          selectedTarget={selectedTarget}
          selectedDraft={selectedDraft}
          timezone={timezone}
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
  return asideTree({
    client,
    selectedDraft: currentDraft,
    onChange: (next) => {
      onChange(next);
      setCurrentDraft(next);
    },
  });
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
      {
        ...target,
        openingHours: target.openingHours.map(
          (entry) =>
            [{ dayOfWeek: 1, start: "10:00", end: "12:00" }].find((day) => day.dayOfWeek === entry.dayOfWeek) ?? entry,
        ),
      },
      [
        { top: "0px", height: "560px" },
        { top: "672px", height: "672px" },
      ],
    ],
    [
      "a closed weekday",
      {
        ...target,
        openingHours: target.openingHours.filter((entry) =>
          [2, 3, 4, 5, 6, 7].some((dayOfWeek) => dayOfWeek === entry.dayOfWeek),
        ),
      },
      [{ top: "0px", height: "1344px" }],
    ],
  ])("shades the closed periods of %s on the draft day", async (_, selectedTarget, expected) => {
    server.use(http.get("/api/v2/bookings", () => HttpResponse.json(confocalBookingPage([]))));
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

  it.each([
    ["the item's zone for a viewer zone that is its alias", "Asia/Calcutta", "Asia/Kolkata"],
    ["the viewer's zone when it differs from the item's", "Europe/Berlin", "Europe/Berlin"],
  ])("names %s in the schedule heading", async (_, timezone, expected) => {
    server.use(http.get("/api/v2/bookings", () => HttpResponse.json(confocalBookingPage([]))));
    render(asideTree({ client: queryClient(), selectedTarget: { ...target, timezone: "Asia/Kolkata" }, timezone }));

    await expectVisibleOccurrence("booking:dayTimeline.vertical.empty");
    expect(screen.getAllByText(`${expected} · Confocal microscope`)[0]).toBeInTheDocument();
  });

  it("keeps the loading state visible until the day schedule resolves", async () => {
    const request = Promise.withResolvers<Response>();
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

    request.resolve(HttpResponse.json(confocalBookingPage([])));
    await expectVisibleOccurrence("booking:dayTimeline.vertical.empty");
  });

  it("offers retry after a schedule error and replaces the error with the loaded schedule", async () => {
    const user = userEvent.setup();
    let requests = 0;
    server.use(
      http.get("/api/v2/bookings", () => {
        requests += 1;
        return requests === 1 ? new HttpResponse(null, { status: 503 }) : HttpResponse.json(confocalBookingPage([]));
      }),
    );

    const client = queryClient();
    render(asideTree({ client }));

    expect(await screen.findByRole("alert")).toMatchTextContent("booking:dayTimeline.vertical.loadError");
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
        return HttpResponse.json(confocalBookingPage([]));
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
    const staleResponse = Promise.withResolvers<Response>();
    let staleSignal: AbortSignal | undefined;
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
          staleSignal = request.signal;
          requestedTargets.push("IN123");
          return staleResponse.promise;
        }
        requestedTargets.push("IN124");
        return HttpResponse.json(
          confocalBookingPage([scheduleBooking({ id: 2, targetId: "IN124", name: "Current target booking" })]),
        );
      }),
    );

    const client = queryClient();
    const view = render(asideTree({ client }));
    expect(await screen.findByRole("status")).toHaveTextContent("booking:dayTimeline.vertical.loading");
    expect(requestedTargets).toContain("IN123");

    view.rerender(asideTree({ client, selectedTarget: nextTarget }));
    await expectVisibleOccurrence("Current target booking");
    await waitFor(() => expect(staleSignal?.aborted).toBe(true));

    await act(async () => {
      staleResponse.resolve(
        HttpResponse.json(
          confocalBookingPage([scheduleBooking({ id: 1, targetId: "IN123", name: "Previous target booking" })]),
        ),
      );
    });
    expect(screen.queryByText("Previous target booking")).not.toBeInTheDocument();
    expect(requestedTargets).toContain("IN124");
  });

  it("keeps a cross-day draft movable and explains why its later end cannot snap to midnight", async () => {
    server.use(http.get("/api/v2/bookings", () => HttpResponse.json(confocalBookingPage([]))));
    const crossDayDraft: BookingWindowDraft = {
      startDate: "2026-08-17",
      startTime: "23:00",
      endDate: "2026-08-18",
      endTime: "01:00",
    };
    const client = queryClient();
    render(asideTree({ client, selectedDraft: crossDayDraft }));

    expect(screen.getByTestId("vertical-day-timeline-draft")).toBeVisible();
    expect(screen.getByRole("button", { name: /booking:dayTimeline.vertical.moveDraft/ })).toBeInTheDocument();
    // The item closes at 22:00, so no other start keeps the draft valid.
    expect(screen.getByRole("button", { name: /booking:dayTimeline.vertical.resizeStart/ })).toHaveAttribute(
      "aria-disabled",
      "true",
    );
    expect(screen.queryByRole("button", { name: "booking:dayTimeline.vertical.resizeEnd" })).not.toBeInTheDocument();
    const continuation = screen.getByRole("button", { name: "booking:timelineEdit.endAtMidnight" });
    expect(continuation).toHaveAttribute("aria-disabled", "true");
    expect(continuation).toHaveAccessibleDescription("booking:timelineEdit.unavailable.openingHours");
  });

  it("the floating schedule button opens and closes the sheet without changing the draft or refetching", async () => {
    const user = userEvent.setup();
    let requests = 0;
    const onChange = vi.fn();
    server.use(
      http.get("/api/v2/bookings", () => {
        requests += 1;
        return HttpResponse.json(confocalBookingPage([]));
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
        return HttpResponse.json(confocalBookingPage([]));
      }),
    );

    render(<StatefullyControlledAside client={queryClient()} onChange={onChange} />);
    await expectVisibleOccurrence("booking:dayTimeline.vertical.empty");

    screen.getByRole("button", { name: "booking:dayTimeline.vertical.moveDraft" }).focus();
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
