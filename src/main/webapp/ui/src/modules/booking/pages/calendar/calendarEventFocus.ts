import type { useLocation, useNavigate } from "@tanstack/react-router";
import * as React from "react";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { currentWallClock } from "@/modules/booking/domain/bookingTime";
import type { useCalendarEvents } from "./calendarEvents";
import type { CalendarLayout, CalendarView } from "./calendarLayoutUtils";

let focusRequestSequence = 0;

export const CALENDAR_EVENT_FOCUS_HIGHLIGHT_CLASS =
  "data-[calendar-event-focus-highlight=true]:ring-4 data-[calendar-event-focus-highlight=true]:ring-ring data-[calendar-event-focus-highlight=true]:ring-offset-2";

export const calendarEventFocusQueryKeys = ["focus", "focusRequest"] as const;

type CalendarEventFocusRequest = { id: number; request: string };

export function calendarEventFocusRequest(searchStr: string): CalendarEventFocusRequest | undefined {
  const params = new URLSearchParams(searchStr);
  const [focusKey, requestKey] = calendarEventFocusQueryKeys;
  const rawId = params.get(focusKey);
  const request = params.get(requestKey);
  if (!request || !rawId || !/^\d+$/.test(rawId)) return undefined;
  const id = Number(rawId);
  return Number.isSafeInteger(id) && id > 0 ? { id, request } : undefined;
}

export function calendarEventFocusRetryChanges(search: URLSearchParams, request: CalendarEventFocusRequest): void {
  const [focusKey, requestKey] = calendarEventFocusQueryKeys;
  search.set(focusKey, String(request.id));
  search.set(requestKey, `${request.request}-retry`);
}

function clearCalendarEventFocusSearch(search: URLSearchParams, request: string): boolean {
  const [, requestKey] = calendarEventFocusQueryKeys;
  if (search.get(requestKey) !== request) return false;
  const [focusKey] = calendarEventFocusQueryKeys;
  search.delete(focusKey);
  search.delete(requestKey);
  return true;
}

type FocusNavigate = ReturnType<typeof useNavigate>;
type FocusLocation = Pick<ReturnType<typeof useLocation>, "pathname" | "searchStr">;
type FocusHookState = {
  location: FocusLocation;
  navigate: FocusNavigate;
  focusRequest: CalendarEventFocusRequest | undefined;
  focusUnavailableRequest: CalendarEventFocusRequest | undefined;
  setFocusUnavailableRequest: React.Dispatch<React.SetStateAction<CalendarEventFocusRequest | undefined>>;
  focusFallback: "resources" | "overflow" | null;
  setFocusFallback: React.Dispatch<React.SetStateAction<"resources" | "overflow" | null>>;
  focusLifecycleRef: React.MutableRefObject<{ request: string; settled: boolean } | null>;
  focusHighlightRef: React.MutableRefObject<{ element: HTMLElement; timer: number } | null>;
  clearFocusRequest: (request: string) => void;
  settleFocusRequest: (request: string, unavailable: boolean) => void;
};

const CALENDAR_FOCUS_TIMEOUT_MS = 15_000;
const CALENDAR_FOCUS_HIGHLIGHT_MS = 2_500;

/** Owns focus request state, listener cancellation and highlight cleanup. */
export function useCalendarEventFocus({
  location,
  navigate,
}: {
  location: FocusLocation;
  navigate: FocusNavigate;
}): FocusHookState {
  const focusRequest = React.useMemo(() => calendarEventFocusRequest(location.searchStr), [location.searchStr]);
  const [focusUnavailableRequest, setFocusUnavailableRequest] = React.useState<CalendarEventFocusRequest | undefined>();
  const [focusFallback, setFocusFallback] = React.useState<"resources" | "overflow" | null>(null);
  const focusLifecycleRef = React.useRef<{ request: string; settled: boolean } | null>(null);
  const focusHighlightRef = React.useRef<{ element: HTMLElement; timer: number } | null>(null);
  const clearFocusRequest = React.useCallback(
    (request: string) => {
      const search = new URLSearchParams(window.location.search);
      if (!clearCalendarEventFocusSearch(search, request)) return;
      const query = search.toString();
      void navigate({
        to: query ? `${window.location.pathname}?${query}` : window.location.pathname,
        replace: true,
        resetScroll: false,
      });
    },
    [navigate],
  );
  const settleFocusRequest = React.useCallback(
    (request: string, unavailable: boolean) => {
      const lifecycle = focusLifecycleRef.current;
      if (lifecycle?.request !== request || lifecycle.settled) return;
      lifecycle.settled = true;
      if (unavailable) setFocusUnavailableRequest(focusRequest);
      clearFocusRequest(request);
    },
    [clearFocusRequest, focusRequest],
  );
  React.useEffect(() => {
    const request = focusRequest?.request;
    if (!request) {
      focusLifecycleRef.current = null;
      return;
    }

    const lifecycle = { request, settled: false };
    focusLifecycleRef.current = lifecycle;
    setFocusUnavailableRequest(undefined);
    setFocusFallback(null);
    const cancel = (event: Event) => {
      if (event.target instanceof Element && event.target.closest("[data-calendar-focus-keep-pending]")) return;
      if (lifecycle.settled) return;
      lifecycle.settled = true;
      clearFocusRequest(request);
    };
    document.addEventListener("pointerdown", cancel, true);
    document.addEventListener("keydown", cancel, true);
    return () => {
      document.removeEventListener("pointerdown", cancel, true);
      document.removeEventListener("keydown", cancel, true);
    };
  }, [focusRequest?.request, clearFocusRequest]);
  React.useEffect(
    () => () => {
      const highlight = focusHighlightRef.current;
      if (highlight) {
        window.clearTimeout(highlight.timer);
        highlight.element.removeAttribute("data-calendar-event-focus-highlight");
      }
    },
    [],
  );
  return {
    location,
    navigate,
    focusRequest,
    focusUnavailableRequest,
    setFocusUnavailableRequest,
    focusFallback,
    setFocusFallback,
    focusLifecycleRef,
    focusHighlightRef,
    clearFocusRequest,
    settleFocusRequest,
  };
}

/** Registers the data-dependent focus resolution effects at their original position in CalendarContent. */
export function useCalendarEventFocusResolution({
  lifecycle,
  displayReady,
  events,
  layout,
  view,
  target,
  preferencesTimeZone,
  resourceStatus,
  resourceTargetIds,
  selectedDate,
  calendarSearch,
  itemWhere,
  eventWhere,
  mineOnly,
  myItemsOnly,
  setView,
  setLayout,
}: {
  lifecycle: FocusHookState;
  displayReady: boolean;
  events: ReturnType<typeof useCalendarEvents>;
  layout: CalendarLayout;
  view: CalendarView;
  target?: string;
  preferencesTimeZone: string;
  resourceStatus: string | undefined;
  resourceTargetIds: readonly string[];
  selectedDate: string;
  calendarSearch: string;
  itemWhere: string | null;
  eventWhere: string | null;
  mineOnly: boolean;
  myItemsOnly: boolean;
  setView: (view: CalendarView) => Promise<unknown>;
  setLayout: (layout: CalendarLayout) => Promise<unknown>;
}) {
  const {
    focusRequest,
    setFocusUnavailableRequest,
    focusLifecycleRef,
    focusHighlightRef,
    settleFocusRequest,
    setFocusFallback,
  } = lifecycle;
  const timeoutResourceStatus = resourceStatus;
  React.useEffect(() => {
    const request = focusRequest?.request;
    if (
      !request ||
      !displayReady ||
      events.isPending ||
      events.isFetching ||
      events.isError ||
      (layout === "resources" &&
        (timeoutResourceStatus === "loading" ||
          timeoutResourceStatus === "refreshing" ||
          timeoutResourceStatus === "error"))
    )
      return;
    const timeout = window.setTimeout(() => settleFocusRequest(request, true), CALENDAR_FOCUS_TIMEOUT_MS);
    return () => window.clearTimeout(timeout);
  }, [
    focusRequest?.request,
    displayReady,
    events.isPending,
    events.isFetching,
    events.isError,
    layout,
    timeoutResourceStatus,
    settleFocusRequest,
  ]);
  React.useEffect(() => {
    setFocusUnavailableRequest(undefined);
  }, [selectedDate, view, layout, target, calendarSearch, itemWhere, eventWhere, mineOnly, myItemsOnly]);
  React.useEffect(() => {
    const request = focusRequest;
    const lifecycleState = request && focusLifecycleRef.current;
    if (!request || lifecycleState?.request !== request.request || lifecycleState.settled) return;
    if (!displayReady || !target) return;
    if (layout === "resources") {
      if (resourceStatus === "loading" || resourceStatus === "refreshing" || resourceStatus === "error") return;
      if (!resourceTargetIds.includes(target)) {
        setFocusFallback("resources");
        void setView("day");
        void setLayout("time-grid");
        return;
      }
    }
    if (events.isError || events.isPending || events.isFetching) return;

    const event = events.data?.find((candidate: BookingListDocument) => candidate.id === request.id);
    if (!event || event.target?.globalId !== target || event.state === "CANCELLED") {
      settleFocusRequest(request.request, true);
      return;
    }

    const date = currentWallClock(event.start, preferencesTimeZone).date;
    const identity = `${date}:${event.id}`;
    const focusTask = window.setTimeout(() => {
      const pending = focusLifecycleRef.current;
      if (pending?.request !== request.request || pending.settled) return;
      const eventElement = document.querySelector<HTMLElement>(`[data-calendar-event-focus="${identity}"]`);
      if (!eventElement) {
        if (layout === "time-grid" && view === "week") {
          setFocusFallback("overflow");
          void setView("day");
          return;
        }
        settleFocusRequest(request.request, true);
        return;
      }

      const trigger =
        eventElement.querySelector<HTMLElement>("button, a[href], [tabindex]:not([tabindex='-1'])") ?? eventElement;
      if (!trigger.isConnected) return;
      const previousHighlight = focusHighlightRef.current;
      if (previousHighlight) {
        window.clearTimeout(previousHighlight.timer);
        previousHighlight.element.removeAttribute("data-calendar-event-focus-highlight");
      }
      eventElement.dataset.calendarEventFocusHighlight = "true";
      trigger.scrollIntoView({ block: "center" });
      trigger.focus({ preventScroll: true });
      focusHighlightRef.current = {
        element: eventElement,
        timer: window.setTimeout(() => {
          eventElement.removeAttribute("data-calendar-event-focus-highlight");
          focusHighlightRef.current = null;
        }, CALENDAR_FOCUS_HIGHLIGHT_MS),
      };
      settleFocusRequest(request.request, false);
    }, 0);
    return () => window.clearTimeout(focusTask);
  }, [
    displayReady,
    events.data,
    events.isError,
    events.isFetching,
    events.isPending,
    focusRequest,
    layout,
    preferencesTimeZone,
    resourceStatus,
    resourceTargetIds,
    setLayout,
    setView,
    settleFocusRequest,
    target,
    view,
  ]);
}

export function calendarEventFocusHref({
  id,
  start,
  targetGlobalId,
  timeZone,
  searchStr,
}: {
  id: number;
  start: string;
  targetGlobalId: string;
  timeZone: string;
  searchStr: string;
}): string {
  const search = new URLSearchParams(searchStr);
  search.set("date", currentWallClock(start, timeZone).date);
  search.set("target", targetGlobalId);
  search.set("focus", String(id));
  search.set("focusRequest", `${Date.now().toString(36)}-${(++focusRequestSequence).toString(36)}`);

  for (const key of [
    "calendar-resources.where",
    "calendar-resources.q",
    "calendar-events.where",
    "mineOnly",
    "myItemsOnly",
  ]) {
    search.delete(key);
  }

  return `/booking/calendar?${search.toString()}`;
}
