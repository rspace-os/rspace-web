import {
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  Outlet,
  RouterProvider,
} from "@tanstack/react-router";
import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useCallback, useEffect, useState } from "react";
import { describe, expect, it, vi } from "vitest";
import type { TableListAlert } from "@/modules/common/table-list/TableList";
import {
  BookingLocalNotices,
  BookingNoticesProvider,
  createBookingEventNotice,
  useBookingLocalNotices,
  useBookingNoticeHost,
  useBookingNotices,
} from "../BookingNotices";

function LocalNoticeDemo({ noticeCount = 2, deferDismiss = false }: { noticeCount?: number; deferDismiss?: boolean }) {
  const { alerts, notify, dismiss } = useBookingLocalNotices();
  const onDismiss = useCallback(
    (id: string) => {
      if (deferDismiss) {
        window.setTimeout(() => dismiss(id), 100);
        return;
      }
      dismiss(id);
    },
    [deferDismiss, dismiss],
  );

  useEffect(() => {
    if (noticeCount > 0) notify({ id: "first", message: "First notice" });
    if (noticeCount > 1) notify({ id: "second", message: "Second notice" });
  }, [noticeCount, notify]);

  return (
    <main tabIndex={-1}>
      <button type="button" onClick={() => dismiss("second")}>
        {"Programmatic cleanup"}
      </button>
      <button type="button" onClick={() => notify({ id: "second", message: "Second notice" })}>
        {"Repeat second notice"}
      </button>
      <button type="button">{"Outside focus"}</button>
      <BookingLocalNotices alerts={alerts} onDismiss={onDismiss} />
    </main>
  );
}

function Host({ deliver, ready }: { deliver: (alert: TableListAlert) => boolean; ready: boolean }) {
  useBookingNoticeHost("calendar", deliver, ready);
  return <p>{"Calendar host ready"}</p>;
}

function Producer({
  onDeliver,
  initiallyReady = true,
  initiallyAccepting = true,
}: {
  onDeliver: (alert: TableListAlert) => boolean;
  initiallyReady?: boolean;
  initiallyAccepting?: boolean;
}) {
  const notices = useBookingNotices();
  const [hostMounted, setHostMounted] = useState(false);
  const [hostReady, setHostReady] = useState(initiallyReady);
  const [accepting, setAccepting] = useState(initiallyAccepting);
  const deliver = useCallback(
    (alert: TableListAlert) => (accepting ? onDeliver(alert) : false),
    [onDeliver, accepting],
  );
  const alert = (id: string): TableListAlert => ({ id, message: `Notice ${id}` });

  return (
    <>
      <button type="button" onClick={() => notices.notify("calendar", alert("first"))}>
        {"Queue first"}
      </button>
      <button type="button" onClick={() => notices.notify("calendar", alert("second"))}>
        {"Queue second"}
      </button>
      <button type="button" onClick={() => setHostMounted((mounted) => !mounted)}>
        {hostMounted ? "Unmount host" : "Mount host"}
      </button>
      {hostMounted ? (
        <>
          <button type="button" onClick={() => setHostReady((ready) => !ready)}>
            {hostReady ? "Mark host unready" : "Mark host ready"}
          </button>
          <button type="button" onClick={() => setAccepting(true)}>
            {"Allow delivery"}
          </button>
          <Host deliver={deliver} ready={hostReady} />
        </>
      ) : null}
    </>
  );
}

function renderWithProvider(
  onDeliver: (alert: TableListAlert) => boolean,
  initiallyReady = true,
  initiallyAccepting = true,
) {
  const root = createRootRoute({
    component: () => (
      <BookingNoticesProvider>
        <Producer onDeliver={onDeliver} initiallyReady={initiallyReady} initiallyAccepting={initiallyAccepting} />
      </BookingNoticesProvider>
    ),
  });
  const router = createRouter({ routeTree: root, history: createMemoryHistory({ initialEntries: ["/"] }) });
  return render(<RouterProvider router={router as never} />);
}

function renderEventNotice(initialEntry: string, sourceSearchStr: string, includeFocusOnCalendar = true) {
  const notice = createBookingEventNotice({
    event: {
      id: 41,
      start: "2026-10-05T09:00:00Z",
      target: { relationTo: "booking-instruments", value: 123, globalId: "IN123" },
    },
    message: "Booking saved",
    timeZone: "UTC",
    searchStr: sourceSearchStr,
    includeFocusOnCalendar,
  });
  const root = createRootRoute({ component: Outlet });
  const booking = createRoute({ getParentRoute: () => root, path: "/booking", component: Outlet });
  const calendar = createRoute({
    getParentRoute: () => booking,
    path: "/calendar",
    component: () => <>{notice.actions}</>,
  });
  const eventDetails = createRoute({
    getParentRoute: () => calendar,
    path: "/bookings/$id",
    component: () => null,
  });
  const itemDetails = createRoute({
    getParentRoute: () => booking,
    path: "/bookable-items/$globalId",
    component: () => <>{notice.actions}</>,
  });
  const router = createRouter({
    routeTree: root.addChildren([booking.addChildren([calendar.addChildren([eventDetails]), itemDetails])]),
    history: createMemoryHistory({ initialEntries: [initialEntry] }),
  });
  render(<RouterProvider router={router as never} />);
  return router;
}

describe("BookingNotices", () => {
  it("delivers only the pending result once its addressed host mounts", async () => {
    const user = userEvent.setup();
    const onDeliver = vi.fn(() => true);
    renderWithProvider(onDeliver);

    await user.click(await screen.findByRole("button", { name: "Queue first" }));
    await user.click(await screen.findByRole("button", { name: "Queue second" }));
    expect(onDeliver).not.toHaveBeenCalled();

    await user.click(await screen.findByRole("button", { name: "Mount host" }));
    await screen.findByText("Calendar host ready");
    expect(onDeliver).toHaveBeenCalledTimes(1);
    expect(onDeliver).toHaveBeenCalledWith({ id: "second", message: "Notice second" });

    await user.click(await screen.findByRole("button", { name: "Unmount host" }));
    await user.click(await screen.findByRole("button", { name: "Mount host" }));
    await screen.findByText("Calendar host ready");
    expect(onDeliver).toHaveBeenCalledTimes(1);
  });

  it("retains a notice when a mounted host is not ready, then delivers after readiness changes", async () => {
    const user = userEvent.setup();
    const onDeliver = vi.fn(() => true);
    renderWithProvider(onDeliver, false, false);

    await user.click(await screen.findByRole("button", { name: "Queue first" }));
    await user.click(screen.getByRole("button", { name: "Mount host" }));
    expect(onDeliver).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: "Mark host ready" }));
    expect(await screen.findByText("Calendar host ready")).toBeVisible();
    expect(onDeliver).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: "Allow delivery" }));
    expect(onDeliver).toHaveBeenCalledOnce();
    expect(onDeliver).toHaveBeenCalledWith({ id: "first", message: "Notice first" });
  });

  it("is a no-op outside the booking provider", async () => {
    function IsolatedControl() {
      const notices = useBookingNotices();
      return (
        <button type="button" onClick={() => notices.notify("calendar", { id: "isolated", message: "Saved" })}>
          {"Notify"}
        </button>
      );
    }

    const user = userEvent.setup();
    render(<IsolatedControl />);
    await user.click(await screen.findByRole("button", { name: "Notify" }));
  });

  it("moves focus to the adjacent notice after keyboard dismissal", async () => {
    const user = userEvent.setup();
    render(<LocalNoticeDemo />);

    const dismissedAlert = await screen.findByRole("listitem", { name: "Second notice" });
    const dismissButton = within(dismissedAlert).getByRole("button");
    act(() => dismissButton.focus());

    await user.keyboard("{Enter}");

    expect(dismissedAlert).not.toBeInTheDocument();
    expect(screen.getByRole("listitem", { name: "First notice" })).toHaveFocus();
  });

  it("moves focus to the page when the final notice is dismissed", async () => {
    const user = userEvent.setup();
    render(<LocalNoticeDemo noticeCount={1} />);

    const alert = await screen.findByRole("listitem", { name: "First notice" });
    const dismissButton = within(alert).getByRole("button");
    act(() => dismissButton.focus());
    await user.keyboard("{Enter}");

    expect(screen.queryByRole("listitem")).not.toBeInTheDocument();
    expect(screen.getByRole("main")).toHaveFocus();
  });

  it("keeps focus on the acting control during programmatic cleanup", async () => {
    const user = userEvent.setup();
    render(<LocalNoticeDemo />);

    await screen.findByRole("listitem", { name: "Second notice" });
    const cleanupButton = screen.getByRole("button", { name: "Programmatic cleanup" });
    await user.click(cleanupButton);

    expect(screen.queryByRole("listitem", { name: "Second notice" })).not.toBeInTheDocument();
    expect(cleanupButton).toHaveFocus();
  });

  it("re-renders a repeated local notice while keeping focus on the acting control", async () => {
    const user = userEvent.setup();
    render(<LocalNoticeDemo />);

    const before = await screen.findByRole("listitem", { name: "Second notice" });
    const repeat = screen.getByRole("button", { name: "Repeat second notice" });
    await user.click(repeat);

    const after = screen.getByRole("listitem", { name: "Second notice" });
    expect(after).not.toBe(before);
    expect(repeat).toHaveFocus();
  });

  it("does not move focus back when the user moves elsewhere before dismissal completes", async () => {
    const user = userEvent.setup();
    render(<LocalNoticeDemo deferDismiss />);

    const dismissedAlert = await screen.findByRole("listitem", { name: "Second notice" });
    const dismissButton = within(dismissedAlert).getByRole("button");
    const outsideButton = screen.getByRole("button", { name: "Outside focus" });
    act(() => dismissButton.focus());
    await user.keyboard("{Enter}");
    expect(dismissedAlert).toBeInTheDocument();

    await user.click(outsideButton);
    await waitFor(() => expect(screen.queryByRole("listitem", { name: "Second notice" })).not.toBeInTheDocument());

    expect(outsideButton).toHaveFocus();
  });

  it("uses the current calendar search when focusing after the view changes", async () => {
    const router = renderEventNotice(
      "/booking/calendar?layout=agenda&view=week&unrelated=before",
      "layout=source&view=day&unrelated=source",
    );
    const focusLink = await screen.findByRole("link", { name: "booking:bookings.feedback.focusOnCalendar" });

    await act(async () => {
      router.history.push("/booking/calendar?layout=time-grid&view=month&unrelated=current");
    });

    await waitFor(() => {
      const updatedSearch = new URL(focusLink.getAttribute("href") ?? "", window.location.origin).searchParams;
      expect(updatedSearch.get("layout")).toBe("time-grid");
    });
    const search = new URL(focusLink.getAttribute("href") ?? "", window.location.origin).searchParams;
    expect(search.get("layout")).toBe("time-grid");
    expect(search.get("view")).toBe("month");
    expect(search.get("unrelated")).toBe("current");
    expect(search.get("target")).toBe("IN123");
    expect(search.get("focus")).toBe("41");
  });

  it("uses the captured source search when a notice is rendered outside the calendar", async () => {
    renderEventNotice("/booking/bookable-items/IN123?tab=events", "layout=source&view=day&unrelated=source");

    const focusLink = await screen.findByRole("link", { name: "booking:bookings.feedback.focusOnCalendar" });
    const search = new URL(focusLink.getAttribute("href") ?? "", window.location.origin).searchParams;
    expect(search.get("layout")).toBe("source");
    expect(search.get("view")).toBe("day");
    expect(search.get("unrelated")).toBe("source");
  });

  it("uses the captured search on an event detail route beneath the calendar", async () => {
    renderEventNotice(
      "/booking/calendar/bookings/41?layout=current&view=month",
      "layout=source&view=day&unrelated=source",
    );

    const focusLink = await screen.findByRole("link", { name: "booking:bookings.feedback.focusOnCalendar" });
    const search = new URL(focusLink.getAttribute("href") ?? "", window.location.origin).searchParams;
    expect(search.get("layout")).toBe("source");
    expect(search.get("view")).toBe("day");
    expect(search.get("unrelated")).toBe("source");
  });

  it("can omit the calendar action when the item is not viewable", async () => {
    renderEventNotice("/booking/bookable-items/IN123", "layout=source", false);

    expect(screen.queryByRole("link", { name: "booking:bookings.feedback.focusOnCalendar" })).not.toBeInTheDocument();
  });
});
