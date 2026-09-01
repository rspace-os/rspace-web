import { ChevronRight, LockKeyhole, Minus, Plus, Wrench, X } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { addCalendarDays } from "@/modules/booking/domain/bookingTime";
import { Button } from "@/modules/common/ui/button";
import { InventoryItem, InventoryLocationLink } from "@/modules/common/ui/inventory-item";
import {
  Popover,
  PopoverClose,
  PopoverContent,
  PopoverDescription,
  PopoverTitle,
  PopoverTrigger,
} from "@/modules/common/ui/popover";
import { UserBadge } from "@/modules/common/ui/user-badge";
import { cn } from "@/modules/common/utils/cn";

const DAY_MINUTES = 24 * 60;
const MINIMUM_VISUAL_DURATION = 15;
const BASE_HOUR_WIDTH = 80;
const DENSITY_WIDTH_STEP = 12;
const EVENTS_PER_BASE_HOUR = 4;
const EVENT_CARD_HEIGHT = 72;
const EVENT_LANE_GAP = 8;
const EVENT_LANE_PITCH = EVENT_CARD_HEIGHT + EVENT_LANE_GAP;

type BaseEvent = {
  id: string;
  startMinute: number;
  endMinute: number;
};

export type DayTimelineItem = {
  name: string;
  globalId: string;
  location?: { name: string; globalId: string };
};

export type DayTimelineEvent =
  | (BaseEvent & {
      kind: "booking";
      privacy: "full";
      title: string;
      bookedBy: string;
      item: DayTimelineItem;
      canEdit: boolean;
      notes?: string;
    })
  | (BaseEvent & {
      kind: "booking";
      privacy: "busy";
    })
  | (BaseEvent & {
      kind: "blockout";
      title: string;
      item: DayTimelineItem;
      createdBy?: string;
      notes?: string;
    });

type DetailedDayTimelineEvent =
  | Extract<DayTimelineEvent, { kind: "blockout" }>
  | Extract<DayTimelineEvent, { kind: "booking"; privacy: "full" }>;

export type DayTimelineViewState = {
  zoom: number;
  centerMinute: number;
};

export type DayTimelineRange = Readonly<{ startMinute: number; endMinute: number }>;

type PositionedEvent = {
  event: DayTimelineEvent;
  startMinute: number;
  endMinute: number;
  visualEndMinute: number;
  lane: number;
};

function formatMinute(minute: number) {
  const wrapped = ((minute % DAY_MINUTES) + DAY_MINUTES) % DAY_MINUTES;
  return `${String(Math.floor(wrapped / 60)).padStart(2, "0")}:${String(wrapped % 60).padStart(2, "0")}`;
}

function dayOffset(minute: number) {
  return Math.floor(minute / DAY_MINUTES);
}

function formatDayOffset(offset: number) {
  return `(${offset > 0 ? "+" : ""}${offset})`;
}

function dateForMinute(date: string, minute: number) {
  return addCalendarDays(date, dayOffset(minute));
}

function formatDayDate(date: string) {
  const [year, month, day] = date.split("-");
  return `${day}-${month}-${year}`;
}

function formatMinuteWithDayOffset(minute: number) {
  const offset = dayOffset(minute);
  return `${formatMinute(minute)}${offset === 0 ? "" : ` ${formatDayOffset(offset)}`}`;
}

function period(event: DayTimelineEvent) {
  return `${formatMinuteWithDayOffset(event.startMinute)}–${formatMinuteWithDayOffset(event.endMinute)}`;
}

function positionEvents(events: ReadonlyArray<DayTimelineEvent>): Array<PositionedEvent> {
  const laneEnds: Array<number> = [];
  return events
    .filter((event) => event.endMinute > 0 && event.startMinute < DAY_MINUTES && event.endMinute > event.startMinute)
    .toSorted(
      (left, right) =>
        left.startMinute - right.startMinute || left.endMinute - right.endMinute || left.id.localeCompare(right.id),
    )
    .map((event) => {
      const startMinute = Math.max(0, event.startMinute);
      const endMinute = Math.min(DAY_MINUTES, event.endMinute);
      const visualEndMinute = Math.min(DAY_MINUTES, Math.max(endMinute, startMinute + MINIMUM_VISUAL_DURATION));
      const availableLane = laneEnds.findIndex((laneEnd) => laneEnd <= startMinute);
      const lane = availableLane === -1 ? laneEnds.length : availableLane;
      laneEnds[lane] = visualEndMinute;
      return { event, startMinute, endMinute, visualEndMinute, lane };
    });
}

function peakHourlyDensity(events: ReadonlyArray<PositionedEvent>) {
  return Math.max(
    0,
    ...Array.from(
      { length: 24 },
      (_, hour) =>
        events.filter(({ startMinute, endMinute }) => startMinute < (hour + 1) * 60 && endMinute > hour * 60).length,
    ),
  );
}

function EventIcon({ event }: { event: DayTimelineEvent }) {
  if (event.kind === "blockout") return <Wrench className="size-3.5 shrink-0" aria-hidden="true" />;
  if (event.privacy === "busy") return <LockKeyhole className="size-3.5 shrink-0" aria-hidden="true" />;
  return null;
}

function ExpandedEventCard({
  date,
  event,
  exactPeriod,
  renderEventActions,
  renderBlockoutActions,
}: {
  date: string;
  event: DetailedDayTimelineEvent;
  exactPeriod: string;
  renderEventActions?: (event: Extract<DayTimelineEvent, { kind: "booking" }>, period: string) => React.ReactNode;
  renderBlockoutActions?: (event: Extract<DayTimelineEvent, { kind: "blockout" }>, period: string) => React.ReactNode;
}) {
  const { t } = useTranslation("booking");
  const isBlockout = event.kind === "blockout";
  const startDate = dateForMinute(date, event.startMinute);
  const endDate = dateForMinute(date, event.endMinute);
  const spansMultipleDates = startDate !== endDate;
  const labelledTitle = event.kind === "booking" ? `${event.title} · ${event.bookedBy}` : event.title;
  const duration = Math.max(0, event.endMinute - event.startMinute);
  const actions =
    event.kind === "booking" ? renderEventActions?.(event, exactPeriod) : renderBlockoutActions?.(event, exactPeriod);

  return (
    <>
      <div
        className={cn(
          "flex items-start justify-between gap-3 px-4 py-2.5",
          isBlockout ? "border-amber-300 border-b bg-amber-100 text-amber-950" : "bg-primary text-primary-foreground",
        )}
      >
        <div className="min-w-0">
          {isBlockout ? (
            <p className="flex items-center gap-1.5 font-medium text-[11px] text-amber-800 uppercase tracking-wide">
              <Wrench className="size-3" aria-hidden="true" />
              {event.title}
            </p>
          ) : null}
          <PopoverTitle className="font-semibold text-lg tabular-nums leading-tight">{exactPeriod}</PopoverTitle>
          {spansMultipleDates ? (
            <dl className="mt-1.5 grid grid-cols-[2.5rem_minmax(0,1fr)] gap-x-2 gap-y-0.5 text-xs leading-4">
              <dt className={cn(isBlockout ? "text-amber-800" : "text-primary-foreground")}>
                {t("bookings.form.start")}
              </dt>
              <dd>
                <time dateTime={`${startDate}T${formatMinute(event.startMinute)}`}>
                  {t("dayTimeline.expanded.dateTime", {
                    date: formatDayDate(startDate),
                    time: formatMinuteWithDayOffset(event.startMinute),
                  })}
                </time>
              </dd>
              <dt className={cn(isBlockout ? "text-amber-800" : "text-primary-foreground")}>
                {t("bookings.form.end")}
              </dt>
              <dd>
                <time dateTime={`${endDate}T${formatMinute(event.endMinute)}`}>
                  {t("dayTimeline.expanded.dateTime", {
                    date: formatDayDate(endDate),
                    time: formatMinuteWithDayOffset(event.endMinute),
                  })}
                </time>
              </dd>
            </dl>
          ) : (
            <time dateTime={startDate} className="mt-1.5 block text-xs leading-4">
              {formatDayDate(startDate)}
            </time>
          )}
          <PopoverDescription className={cn("mt-1 text-xs", isBlockout ? "text-amber-800" : "text-primary-foreground")}>
            {t("dayTimeline.expanded.duration", {
              hours: Math.floor(duration / 60),
              minutes: duration % 60,
            })}
          </PopoverDescription>
        </div>
        <PopoverClose
          type="button"
          aria-label={t("dayTimeline.event.hideDetails", { title: labelledTitle, period: exactPeriod })}
          className={cn(
            "-mr-1 grid size-7 shrink-0 place-items-center rounded-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/40",
            isBlockout ? "hover:bg-amber-200" : "hover:bg-primary-foreground/15",
          )}
        >
          <X className="size-4" aria-hidden="true" />
        </PopoverClose>
      </div>

      <dl className="divide-y divide-border px-4 text-sm">
        <div className="py-2">
          <dt className="sr-only">{t("dayTimeline.expanded.item")}</dt>
          <dd>
            <InventoryItem
              name={event.item.name}
              globalId={event.item.globalId}
              href={`/globalId/${event.item.globalId}`}
              idLinkLabel={t("dayTimeline.expanded.openItem", { globalId: event.item.globalId })}
              idPlacement="title"
              className="p-0"
            >
              {event.item.location ? (
                <InventoryLocationLink name={event.item.location.name} globalId={event.item.location.globalId} />
              ) : null}
            </InventoryItem>
          </dd>
        </div>
        {event.kind === "booking" ? (
          <div className="grid grid-cols-[4.5rem_1fr] gap-2 py-2">
            <dt className="text-muted-foreground text-xs">{t("dayTimeline.expanded.bookedBy")}</dt>
            <dd className="min-w-0">
              <UserBadge name={event.bookedBy} />
            </dd>
          </div>
        ) : event.createdBy ? (
          <div className="grid grid-cols-[4.5rem_1fr] gap-2 py-2">
            <dt className="text-muted-foreground text-xs">{t("dayTimeline.expanded.createdBy")}</dt>
            <dd className="min-w-0">
              <UserBadge name={event.createdBy} />
            </dd>
          </div>
        ) : null}
        <div className="grid grid-cols-[4.5rem_1fr] gap-2 py-2">
          <dt className="text-muted-foreground text-xs">
            {t(isBlockout ? "dayTimeline.expanded.notes" : "dayTimeline.expanded.purpose")}
          </dt>
          <dd className="text-xs leading-4">{event.notes}</dd>
        </div>
      </dl>

      {actions}
    </>
  );
}

export function DayTimelineEventCard({
  event,
  date,
  compactCards = true,
  variant = "flow",
  expanded,
  onExpandedChange,
  collisionBoundary,
  expandedCardClassName,
  renderEventActions,
  renderBlockoutActions,
}: {
  event: DayTimelineEvent;
  date: string;
  compactCards?: boolean;
  variant?: "timeline" | "flow";
  expanded?: boolean;
  onExpandedChange?: (expanded: boolean) => void;
  collisionBoundary?: HTMLElement | null;
  expandedCardClassName?: string;
  renderEventActions?: (event: Extract<DayTimelineEvent, { kind: "booking" }>, period: string) => React.ReactNode;
  renderBlockoutActions?: (event: Extract<DayTimelineEvent, { kind: "blockout" }>, period: string) => React.ReactNode;
}) {
  const { t } = useTranslation("booking");
  const detailsId = `${React.useId()}-details`;
  const [internalExpanded, setInternalExpanded] = React.useState(false);
  const compactCardRef = React.useRef<HTMLElement>(null);
  const triggerRef = React.useRef<HTMLButtonElement>(null);
  const restoreFocusOnCloseRef = React.useRef(true);
  const isExpanded = expanded ?? internalExpanded;
  const setExpanded = onExpandedChange ?? setInternalExpanded;
  const title = event.kind === "blockout" || event.privacy === "full" ? event.title : t("dayTimeline.event.busy");
  const labelledTitle = event.kind === "booking" && event.privacy === "full" ? `${title} · ${event.bookedBy}` : title;
  const notes = event.kind === "booking" && event.privacy === "busy" ? undefined : event.notes;
  const exactPeriod = period(event);
  const accessibleLabel = notes
    ? t("dayTimeline.event.labelWithNotes", { title: labelledTitle, period: exactPeriod, notes })
    : t("dayTimeline.event.label", { title: labelledTitle, period: exactPeriod });
  const isBlockout = event.kind === "blockout";
  const isBusy = event.kind === "booking" && event.privacy === "busy";
  const timeline = variant === "timeline";
  const expandsInPlace = isBusy && isExpanded;
  const startDate = dateForMinute(date, event.startMinute);
  const endDate = dateForMinute(date, event.endMinute);
  const compactDate =
    startDate === endDate ? formatDayDate(startDate) : `${formatDayDate(startDate)} - ${formatDayDate(endDate)}`;
  const compactPeriod = `${formatMinuteWithDayOffset(event.startMinute)} - ${formatMinuteWithDayOffset(event.endMinute)}`;
  const toggleLabel = isExpanded
    ? t("dayTimeline.event.hideDetails", { title: labelledTitle, period: exactPeriod })
    : t("dayTimeline.event.showDetails", { title: labelledTitle, period: exactPeriod });

  React.useEffect(() => {
    if (!isBusy || !isExpanded) return;
    const closeOnOutsidePointer = (pointerEvent: PointerEvent) => {
      if (pointerEvent.target instanceof Node && !compactCardRef.current?.contains(pointerEvent.target)) {
        setExpanded(false);
        const openingAnother =
          pointerEvent.target instanceof Element && pointerEvent.target.closest("[data-event-id]") !== null;
        if (!openingAnother) {
          requestAnimationFrame(() => triggerRef.current?.focus());
        }
      }
    };
    document.addEventListener("pointerdown", closeOnOutsidePointer);
    return () => document.removeEventListener("pointerdown", closeOnOutsidePointer);
  }, [isBusy, isExpanded, setExpanded]);

  const compactCard = (
    <article
      ref={compactCardRef}
      aria-label={accessibleLabel}
      title={title}
      className={cn(
        "group flex w-full cursor-pointer flex-col items-stretch justify-start gap-0 overflow-hidden rounded-sm border px-1.5 py-1 text-xs leading-tight shadow-sm",
        timeline ? "absolute inset-y-0 left-0" : "relative min-h-12",
        !isBlockout && !isBusy && "border-blue-700 bg-blue-600 text-white",
        isBlockout && "border-amber-600 bg-amber-100 text-amber-950",
        isBusy && "border-slate-500 bg-slate-200 text-slate-900",
        expandsInPlace && "z-40 h-auto min-h-24 justify-start overflow-visible px-3 py-2 ring-3 ring-ring/40",
        expandsInPlace && timeline && "w-72",
      )}
    >
      <div className="min-w-0 pr-7">
        <span className="flex min-w-0 items-center gap-1">
          <EventIcon event={event} />
          <span className="min-w-0 flex-1 truncate font-semibold">{title}</span>
        </span>
        {event.kind === "booking" && event.privacy === "full" ? (
          <UserBadge
            name={event.bookedBy}
            density="compact"
            className="mt-0.5 max-w-full border-current/30 bg-background/90 text-foreground"
          />
        ) : null}
      </div>
      <time
        dateTime={`${startDate}T${formatMinute(event.startMinute)}`}
        title={compactCards ? `${compactDate}; ${compactPeriod}` : exactPeriod}
        hidden={expandsInPlace}
        data-event-time
        className="mt-auto min-w-0 font-medium tabular-nums leading-tight"
      >
        {compactCards ? (
          <>
            <span className="block truncate text-[10px]" data-event-date>
              {compactDate}
            </span>
            <span className="block truncate text-[11px]" data-event-time-range>
              {compactPeriod}
            </span>
          </>
        ) : (
          <span className="block truncate text-[11px]">{exactPeriod}</span>
        )}
      </time>
      {isBusy ? (
        <button
          ref={triggerRef}
          type="button"
          aria-controls={detailsId}
          aria-expanded={isExpanded}
          aria-label={toggleLabel}
          className="absolute inset-0 z-10 rounded-sm bg-transparent outline-none focus-visible:ring-3 focus-visible:ring-inset focus-visible:ring-ring/60"
          onClick={() => setExpanded(!isExpanded)}
          onKeyDown={(keyboardEvent) => {
            if (keyboardEvent.key !== "Escape" || !isExpanded) return;
            keyboardEvent.preventDefault();
            setExpanded(false);
            keyboardEvent.currentTarget.focus();
          }}
        />
      ) : (
        <PopoverTrigger
          ref={triggerRef}
          aria-controls={detailsId}
          aria-expanded={isExpanded}
          aria-label={toggleLabel}
          className="absolute inset-0 z-10 rounded-sm bg-transparent outline-none focus-visible:ring-3 focus-visible:ring-inset focus-visible:ring-ring/60"
        />
      )}
      <span
        aria-hidden="true"
        className={cn(
          "pointer-events-none absolute top-1 right-1 z-20 flex size-6 items-center justify-center rounded-full border border-current/20 bg-background/90 text-foreground opacity-0 shadow-sm transition-opacity group-hover:opacity-100 group-focus-within:opacity-100",
          !compactCards && "opacity-100",
          isExpanded && "opacity-100",
        )}
        data-event-expand-indicator
      >
        <ChevronRight className={cn("size-3.5 transition-transform", isExpanded && "rotate-90")} />
      </span>
      {isBusy ? (
        <div id={detailsId} hidden={!isExpanded} className="relative z-20 space-y-1 pt-1">
          <time className="block font-medium">{exactPeriod}</time>
          {isExpanded ? renderEventActions?.(event, exactPeriod) : null}
        </div>
      ) : null}
      {event.startMinute < 0 && <span className="absolute inset-y-0 left-0 w-1 bg-current" aria-hidden="true" />}
      {event.endMinute > DAY_MINUTES && (
        <span className="absolute inset-y-0 right-0 w-1 bg-current" aria-hidden="true" />
      )}
    </article>
  );

  if (event.kind === "booking" && event.privacy === "busy") return compactCard;

  return (
    <Popover
      open={isExpanded}
      onOpenChange={(nextOpen, eventDetails) => {
        if (!nextOpen) {
          const destination =
            eventDetails.event instanceof FocusEvent ? eventDetails.event.relatedTarget : eventDetails.event.target;
          restoreFocusOnCloseRef.current =
            !(destination instanceof Element) || destination.closest("[data-event-id]") === null;
        }
        setExpanded(nextOpen);
      }}
      onOpenChangeComplete={(open) => {
        if (!open && restoreFocusOnCloseRef.current) triggerRef.current?.focus();
      }}
      modal={false}
    >
      {compactCard}
      <PopoverContent
        id={detailsId}
        align="start"
        side="bottom"
        sideOffset={8}
        collisionPadding={0}
        collisionBoundary={collisionBoundary ?? undefined}
        sticky
        finalFocus={false}
        className={cn(
          "w-[min(22rem,var(--available-width))] max-w-none gap-0 overflow-hidden rounded-sm border p-0 shadow-xl ring-4",
          expandedCardClassName,
          isBlockout ? "border-amber-600 ring-amber-500/15" : "border-primary ring-ring/20",
        )}
      >
        <ExpandedEventCard
          date={date}
          event={event}
          exactPeriod={exactPeriod}
          renderEventActions={renderEventActions}
          renderBlockoutActions={renderBlockoutActions}
        />
      </PopoverContent>
    </Popover>
  );
}

function NowMarker({ minute, edge, label }: { minute: number; edge: "inside" | "before" | "after"; label: string }) {
  const pinned = edge !== "inside";
  return (
    <div
      className={cn(
        "pointer-events-none absolute top-8 bottom-0 z-30 w-0.5 bg-red-600",
        pinned && edge === "before" && "left-0",
        pinned && edge === "after" && "right-0",
      )}
      style={pinned ? undefined : { left: `${(minute / DAY_MINUTES) * 100}%` }}
      data-testid="day-timeline-now"
      data-edge={edge}
    >
      <time
        dateTime={formatMinute(minute)}
        aria-current="time"
        className={cn(
          "absolute bottom-full left-1/2 mb-1 -translate-x-1/2 px-2 py-0.5 text-[11px] font-semibold whitespace-nowrap text-red-700",
          edge === "before" && "left-2 translate-x-0",
          edge === "after" && "right-2 left-auto translate-x-0",
        )}
      >
        {label}
      </time>
    </div>
  );
}

export function DayTimeline({
  date,
  timezone,
  events,
  startWindow,
  endWindow,
  nowMinute,
  viewState,
  onViewStateChange,
  showZoomControls = true,
  showScrollbar = true,
  hourWidth,
  compactCards = true,
  variant = "detail",
  itemName,
  expandedCardClassName,
  renderEventActions,
  renderBlockoutActions,
  snapIncrementMinutes = 5,
  creationDisabled = false,
  onRangeSelect,
}: {
  date: string;
  timezone: string;
  events: ReadonlyArray<DayTimelineEvent>;
  startWindow: number;
  endWindow: number;
  nowMinute?: number;
  viewState?: DayTimelineViewState;
  onViewStateChange?: (viewState: DayTimelineViewState) => void;
  showZoomControls?: boolean;
  showScrollbar?: boolean;
  hourWidth?: number;
  compactCards?: boolean;
  variant?: "detail" | "table-row";
  itemName?: string;
  expandedCardClassName?: string | ((event: DayTimelineEvent) => string | undefined);
  renderEventActions?: (event: Extract<DayTimelineEvent, { kind: "booking" }>, period: string) => React.ReactNode;
  renderBlockoutActions?: (event: Extract<DayTimelineEvent, { kind: "blockout" }>, period: string) => React.ReactNode;
  snapIncrementMinutes?: number;
  creationDisabled?: boolean;
  onRangeSelect?: (range: DayTimelineRange, trigger: HTMLElement) => void;
}) {
  const { t, i18n } = useTranslation("booking");
  const scrollerRef = React.useRef<HTMLElement>(null);
  const [collisionBoundary, setCollisionBoundary] = React.useState<HTMLElement | null>(null);
  const [dragRange, setDragRange] = React.useState<{ from: number; to: number } | null>(null);
  const [expandedEventId, setExpandedEventId] = React.useState<string | null>(null);
  const instanceId = React.useId();
  const headingId = `${instanceId}-heading`;
  const [internalViewState, setInternalViewState] = React.useState<DayTimelineViewState>({
    zoom: 1,
    centerMinute: (startWindow + endWindow) / 2,
  });
  const activeViewState = viewState ?? internalViewState;
  const { zoom, centerMinute } = activeViewState;
  const positionedEvents = positionEvents(events);
  const laneCount = Math.max(1, ...positionedEvents.map(({ lane }) => lane + 1));
  const density = peakHourlyDensity(positionedEvents);
  const densityHourWidth =
    hourWidth ?? BASE_HOUR_WIDTH + Math.max(0, density - EVENTS_PER_BASE_HOUR) * DENSITY_WIDTH_STEP;
  const visibleMinutes = endWindow - startWindow;
  const nowEdge =
    nowMinute === undefined ? null : nowMinute < startWindow ? "before" : nowMinute > endWindow ? "after" : "inside";

  React.useLayoutEffect(() => {
    const scroller = scrollerRef.current;
    if (!scroller) return;
    const target = (centerMinute / DAY_MINUTES) * scroller.scrollWidth - scroller.clientWidth / 2;
    scroller.scrollLeft = Math.min(scroller.scrollWidth - scroller.clientWidth, Math.max(0, target));
  }, [centerMinute, zoom]);

  const updateViewState = (nextViewState: DayTimelineViewState) => {
    if (viewState === undefined) setInternalViewState(nextViewState);
    onViewStateChange?.(nextViewState);
  };

  const changeZoom = (delta: number) => {
    const scroller = scrollerRef.current;
    const nextCenterMinute = scroller
      ? ((scroller.scrollLeft + scroller.clientWidth / 2) / scroller.scrollWidth) * DAY_MINUTES
      : centerMinute;
    updateViewState({ zoom: Math.min(4, Math.max(1, zoom + delta)), centerMinute: nextCenterMinute });
  };

  const syncScrollPosition = () => {
    const scroller = scrollerRef.current;
    if (!scroller) return;
    const nextCenterMinute = ((scroller.scrollLeft + scroller.clientWidth / 2) / scroller.scrollWidth) * DAY_MINUTES;
    if (Math.abs(nextCenterMinute - centerMinute) > 0.5) {
      updateViewState({ zoom, centerMinute: nextCenterMinute });
    }
  };

  const formattedDate = new Intl.DateTimeFormat(i18n.language, { dateStyle: "full", timeZone: "UTC" }).format(
    new Date(`${date}T12:00:00Z`),
  );
  const nowLabel =
    nowMinute === undefined || nowEdge === null
      ? null
      : nowEdge === "before"
        ? t("dayTimeline.now.current", { time: formatMinute(nowMinute) })
        : nowEdge === "after"
          ? t("dayTimeline.now.afterWindow", { time: formatMinute(nowMinute) })
          : t("dayTimeline.now.current", { time: formatMinute(nowMinute) });
  const tableRow = variant === "table-row";
  const eventAreaTop = tableRow ? 32 : 64;
  const setScrollerRef = React.useCallback((node: HTMLElement | null) => {
    scrollerRef.current = node;
    setCollisionBoundary(node);
  }, []);
  const snapMinute = (minute: number) =>
    Math.min(DAY_MINUTES, Math.max(0, Math.round(minute / snapIncrementMinutes) * snapIncrementMinutes));
  const minuteAt = (clientX: number, canvas: HTMLElement) => {
    const box = canvas.getBoundingClientRect();
    return snapMinute(((clientX - box.left) / box.width) * DAY_MINUTES);
  };
  const freeCanvas = (target: EventTarget | null) =>
    target instanceof HTMLElement && !target.closest("article, button, a");

  return (
    <section aria-labelledby={headingId} className={cn("text-foreground", tableRow ? "space-y-0" : "space-y-4")}>
      <header className={cn(tableRow && "sr-only")}>
        <div>
          <h2 id={headingId} className="text-xl font-semibold">
            <time dateTime={date}>{formattedDate}</time>
          </h2>
          <p className="text-sm text-muted-foreground">{timezone}</p>
        </div>
      </header>
      {showZoomControls && !tableRow && (
        <div className="flex justify-end">
          <fieldset className="flex items-center gap-1">
            <legend className="sr-only">{t("dayTimeline.zoom.legend")}</legend>
            <Button
              type="button"
              variant="outline"
              size="icon-sm"
              aria-label={t("dayTimeline.zoom.out")}
              disabled={zoom === 1}
              onClick={() => changeZoom(-0.5)}
            >
              <Minus />
            </Button>
            <Button
              type="button"
              variant="outline"
              size="icon-sm"
              aria-label={t("dayTimeline.zoom.in")}
              disabled={zoom === 4}
              onClick={() => changeZoom(0.5)}
            >
              <Plus />
            </Button>
          </fieldset>
        </div>
      )}

      <div className="relative">
        <section
          ref={setScrollerRef}
          aria-label={
            itemName
              ? t("dayTimeline.itemScrollLabel", { itemName, date: formattedDate, timezone })
              : t("dayTimeline.scrollLabel", { date: formattedDate, timezone })
          }
          className={cn(
            "overflow-x-auto rounded-sm border bg-card shadow-sm",
            !showScrollbar && "[scrollbar-width:none] [&::-webkit-scrollbar]:hidden",
          )}
          data-testid="day-timeline-scroller"
          onScroll={syncScrollPosition}
          // biome-ignore lint/a11y/noNoninteractiveTabindex: Keyboard users need to focus and horizontally scroll the timeline.
          tabIndex={0}
        >
          <div
            className={cn("relative select-none touch-none", onRangeSelect && !creationDisabled && "cursor-crosshair")}
            style={{
              width: `${(DAY_MINUTES / visibleMinutes) * 100 * zoom}%`,
              minWidth: densityHourWidth * 24 * zoom,
              height: eventAreaTop + 16 + laneCount * EVENT_LANE_PITCH + (positionedEvents.length === 0 ? 0 : 80),
            }}
            data-testid="day-timeline-canvas"
            data-hour-width={densityHourWidth}
            data-creation-disabled={creationDisabled || undefined}
            onPointerDown={(event) => {
              if (!onRangeSelect || creationDisabled || !freeCanvas(event.target)) return;
              const from = Math.min(DAY_MINUTES - snapIncrementMinutes, minuteAt(event.clientX, event.currentTarget));
              if (event.nativeEvent.isTrusted) event.currentTarget.setPointerCapture(event.pointerId);
              setDragRange({ from, to: from + snapIncrementMinutes });
            }}
            onPointerMove={(event) => {
              if (!dragRange || creationDisabled) return;
              setDragRange({ ...dragRange, to: minuteAt(event.clientX, event.currentTarget) });
            }}
            onPointerUp={(event) => {
              if (!dragRange || !onRangeSelect || creationDisabled) {
                setDragRange(null);
                return;
              }
              const startMinute = Math.min(dragRange.from, dragRange.to);
              const endMinute = Math.max(dragRange.from, dragRange.to);
              setDragRange(null);
              if (endMinute - startMinute < snapIncrementMinutes) return;
              onRangeSelect({ startMinute, endMinute }, scrollerRef.current ?? event.currentTarget);
            }}
            onPointerCancel={() => setDragRange(null)}
          >
            <div
              className="pointer-events-none absolute inset-0 grid grid-cols-[repeat(24,minmax(0,1fr))]"
              aria-hidden="true"
            >
              {Array.from({ length: 24 }, (_, hour) => (
                <div key={hour} className={cn("relative border-l border-border/80", hour % 2 === 0 && "bg-muted/25")}>
                  <span className={cn("absolute left-2 text-[11px] font-medium", tableRow ? "top-2" : "top-10")}>
                    {formatMinute(hour * 60)}
                  </span>
                </div>
              ))}
            </div>
            <ol className="absolute inset-0 list-none">
              {positionedEvents.map(({ event, startMinute, visualEndMinute, lane }) => {
                return (
                  <li
                    key={event.id}
                    className="absolute z-10"
                    style={{
                      left: `${(startMinute / DAY_MINUTES) * 100}%`,
                      width: `${((visualEndMinute - startMinute) / DAY_MINUTES) * 100}%`,
                      top: eventAreaTop + lane * EVENT_LANE_PITCH,
                      height: EVENT_CARD_HEIGHT,
                    }}
                    data-event-id={event.id}
                    data-lane={lane}
                  >
                    <DayTimelineEventCard
                      event={event}
                      date={date}
                      compactCards={compactCards}
                      variant="timeline"
                      expanded={expandedEventId === event.id}
                      onExpandedChange={(expanded) =>
                        setExpandedEventId((current) => (expanded ? event.id : current === event.id ? null : current))
                      }
                      collisionBoundary={collisionBoundary}
                      expandedCardClassName={
                        typeof expandedCardClassName === "function"
                          ? expandedCardClassName(event)
                          : expandedCardClassName
                      }
                      renderEventActions={renderEventActions}
                      renderBlockoutActions={renderBlockoutActions}
                    />
                  </li>
                );
              })}
            </ol>
            {dragRange ? (
              <div
                aria-hidden="true"
                className="pointer-events-none absolute z-40 rounded-sm border-2 border-primary bg-primary/20"
                style={{
                  top: eventAreaTop,
                  bottom: 0,
                  left: `${(Math.min(dragRange.from, dragRange.to) / DAY_MINUTES) * 100}%`,
                  width: `${(Math.abs(dragRange.to - dragRange.from) / DAY_MINUTES) * 100}%`,
                }}
              />
            ) : null}
            {nowMinute !== undefined && nowEdge === "inside" && nowLabel && (
              <NowMarker minute={nowMinute} edge="inside" label={nowLabel} />
            )}
          </div>
        </section>
        {nowMinute !== undefined && nowEdge !== null && nowEdge !== "inside" && nowLabel && (
          <NowMarker minute={nowMinute} edge={nowEdge} label={nowLabel} />
        )}
      </div>
    </section>
  );
}
