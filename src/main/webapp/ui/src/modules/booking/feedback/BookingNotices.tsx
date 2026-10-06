import { Link, useRouterState } from "@tanstack/react-router";
import {
  createContext,
  type ReactNode,
  useCallback,
  useContext,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { useTranslation } from "react-i18next";
import type { BookingMutation } from "@/modules/booking/domain/booking";
import { calendarEventFocusHref } from "@/modules/booking/pages/calendar/calendarEventFocus";
import { type TableListAlert, TableListAlertItem } from "@/modules/common/table-list/TableList";
import { buttonVariants } from "@/modules/common/ui/button";

export type BookingNoticeHost = "calendar" | "bookable-items" | "item-detail";

type BookingNoticesValue = {
  notify: (host: BookingNoticeHost, alert: TableListAlert) => void;
  register: (host: BookingNoticeHost, deliver: (alert: TableListAlert) => boolean) => () => void;
};

type PendingNotice = { host: BookingNoticeHost; alert: TableListAlert };

const BookingNoticesContext = createContext<BookingNoticesValue | null>(null);
const noopNotify = () => undefined;
const EMPTY_BOOKING_NOTICES: Pick<BookingNoticesValue, "notify"> = { notify: noopNotify };

function hostForPath(pathname: string): BookingNoticeHost | null {
  if (pathname === "/booking/calendar" || pathname.startsWith("/booking/calendar/")) return "calendar";
  if (pathname === "/booking/config/bookable-items" || pathname === "/booking/all-items") return "bookable-items";
  if (/^\/booking\/bookable-items\/IN\d+(?:\/|$)/.test(pathname)) return "item-detail";
  return null;
}

/** Keeps one result until its destination is ready, then delivers it once. */
export function BookingNoticesProvider({ children }: { children: ReactNode }) {
  const pathname = useRouterState({ select: (state) => state.location.pathname });
  const destination = hostForPath(pathname);
  const deliveries = useRef(new Map<BookingNoticeHost, (alert: TableListAlert) => boolean>());
  const pending = useRef<PendingNotice | null>(null);

  const notify = useCallback((host: BookingNoticeHost, alert: TableListAlert) => {
    const deliver = deliveries.current.get(host);
    if (deliver?.(alert)) return;
    pending.current = { host, alert };
  }, []);

  const register = useCallback((host: BookingNoticeHost, deliver: (alert: TableListAlert) => boolean) => {
    deliveries.current.set(host, deliver);
    if (pending.current?.host === host && deliver(pending.current.alert)) pending.current = null;
    return () => {
      if (deliveries.current.get(host) === deliver) deliveries.current.delete(host);
    };
  }, []);

  useEffect(() => {
    if (pending.current && pending.current.host !== destination) pending.current = null;
  }, [destination]);

  const value = useMemo(() => ({ notify, register }), [notify, register]);
  return <BookingNoticesContext.Provider value={value}>{children}</BookingNoticesContext.Provider>;
}

/** Outside BookingPage, notices intentionally do nothing so isolated controls stay easy to test. */
export function useBookingNotices(): Pick<BookingNoticesValue, "notify"> {
  return useContext(BookingNoticesContext) ?? EMPTY_BOOKING_NOTICES;
}

/** Registers a destination after its table or local notice area has mounted. */
export function useBookingNoticeHost(
  host: BookingNoticeHost,
  deliver: (alert: TableListAlert) => boolean,
  ready = true,
): void {
  const notices = useContext(BookingNoticesContext);
  useEffect(() => (ready ? notices?.register(host, deliver) : undefined), [notices, host, deliver, ready]);
}

export type BookingEventNoticeData = Pick<BookingMutation, "id" | "start" | "target">;

export function createBookingEventNotice({
  event,
  message,
  timeZone,
  searchStr,
  includeViewDetails = true,
  includeFocusOnCalendar = true,
}: {
  event: BookingEventNoticeData;
  message: string;
  timeZone: string;
  searchStr: string;
  includeViewDetails?: boolean;
  includeFocusOnCalendar?: boolean;
}): TableListAlert {
  return {
    id: `booking-${event.id}`,
    tone: "success",
    message,
    actions: (
      <BookingEventNoticeActions
        event={event}
        timeZone={timeZone}
        sourceSearchStr={searchStr}
        includeViewDetails={includeViewDetails}
        includeFocusOnCalendar={includeFocusOnCalendar}
      />
    ),
  };
}

function BookingEventNoticeActions({
  event,
  timeZone,
  sourceSearchStr,
  includeViewDetails,
  includeFocusOnCalendar,
}: {
  event: BookingEventNoticeData;
  timeZone: string;
  sourceSearchStr: string;
  includeViewDetails: boolean;
  includeFocusOnCalendar: boolean;
}) {
  const { t } = useTranslation("booking");
  const pathname = useRouterState({ select: (state) => state.location.pathname });
  const currentSearchStr = useRouterState({ select: (state) => state.location.searchStr });
  const searchStr = pathname === "/booking/calendar" ? currentSearchStr : sourceSearchStr;
  const focusHref = useMemo(
    () =>
      calendarEventFocusHref({
        id: event.id,
        start: event.start,
        targetGlobalId: event.target.globalId,
        timeZone,
        searchStr,
      }),
    [event.id, event.start, event.target.globalId, searchStr, timeZone],
  );
  return (
    <>
      {includeViewDetails ? (
        <Link
          to="/booking/calendar/bookings/$id"
          params={{ id: String(event.id) }}
          className={buttonVariants({ variant: "outline", size: "sm" })}
        >
          {t("bookings.feedback.viewDetails")}
        </Link>
      ) : null}
      {includeFocusOnCalendar ? (
        <Link to={focusHref} className={buttonVariants({ variant: "outline", size: "sm" })}>
          {t("bookings.feedback.focusOnCalendar")}
        </Link>
      ) : null}
    </>
  );
}

export function useBookingLocalNotices() {
  const [alerts, setAlerts] = useState<readonly BookingLocalNotice[]>([]);
  const nextRenderKey = useRef(0);
  const notify = useCallback((alert: TableListAlert) => {
    const nextAlert = { ...alert, renderKey: ++nextRenderKey.current };
    setAlerts((current) => [nextAlert, ...current.filter((currentAlert) => currentAlert.id !== alert.id)]);
    return true;
  }, []);
  const dismiss = useCallback((id: string) => {
    setAlerts((current) => current.filter((alert) => alert.id !== id));
  }, []);
  const clear = useCallback(() => setAlerts([]), []);
  return { alerts, notify, dismiss, clear };
}

type BookingLocalNotice = TableListAlert & { renderKey: number };

export function BookingLocalNotices({
  alerts,
  onDismiss,
}: {
  alerts: readonly BookingLocalNotice[];
  onDismiss: (id: string) => void;
}) {
  const { t } = useTranslation("common");
  const hostRef = useRef<HTMLDivElement>(null);
  const focusRequest = useRef<{ dismissedId: string; targetId: string | null } | null>(null);
  const dismiss = useCallback(
    (id: string) => {
      const focusedAlert = document.activeElement?.closest<HTMLElement>("[data-table-list-alert]");
      if (focusedAlert?.dataset.tableListAlert === id) {
        const index = alerts.findIndex((alert) => alert.id === id);
        const remaining = alerts.filter((alert) => alert.id !== id);
        const neighbour = remaining[Math.min(Math.max(index, 0), remaining.length - 1)];
        focusRequest.current = { dismissedId: id, targetId: neighbour?.id ?? null };
      }
      onDismiss(id);
    },
    [alerts, onDismiss],
  );

  useLayoutEffect(() => {
    const request = focusRequest.current;
    if (!request) return;
    if (alerts.some((alert) => alert.id === request.dismissedId)) {
      focusRequest.current = null;
      return;
    }

    focusRequest.current = null;
    const activeElement = document.activeElement;
    if (activeElement && activeElement !== document.body && activeElement.isConnected) return;

    const notice = request.targetId
      ? Array.from(hostRef.current?.querySelectorAll<HTMLElement>("[data-table-list-alert]") ?? []).find(
          (element) => element.dataset.tableListAlert === request.targetId,
        )
      : undefined;
    (notice ?? hostRef.current?.closest<HTMLElement>("main"))?.focus();
  }, [alerts]);

  return (
    <div ref={hostRef} aria-live="polite" aria-relevant="additions text">
      {alerts.length > 0 ? (
        <ul aria-label={t("tableList.alerts.label")} className="mb-3 space-y-2 pt-3">
          {alerts.map((alert) => (
            <TableListAlertItem key={alert.renderKey} alert={alert} onDismiss={dismiss} />
          ))}
        </ul>
      ) : null}
    </div>
  );
}
