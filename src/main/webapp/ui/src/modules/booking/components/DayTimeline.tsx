import { ChevronRight, LockKeyhole, Minus, Plus, Wrench, X } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
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
import { cn } from "@/modules/common/utils/cn";

const DAY_MINUTES = 24 * 60;
const MINIMUM_VISUAL_DURATION = 15;
const BASE_HOUR_WIDTH = 80;
const DENSITY_WIDTH_STEP = 12;
const EVENTS_PER_BASE_HOUR = 4;

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
      notes?: string;
    });

type DetailedDayTimelineEvent =
  | Extract<DayTimelineEvent, { kind: "blockout" }>
  | Extract<DayTimelineEvent, { kind: "booking"; privacy: "full" }>;

export type DayTimelineViewState = {
  zoom: number;
  centerMinute: number;
};

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

function period(event: DayTimelineEvent) {
  return `${formatMinute(event.startMinute)}–${formatMinute(event.endMinute)}`;
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
}: {
  date: string;
  event: DetailedDayTimelineEvent;
  exactPeriod: string;
  renderEventActions?: (event: Extract<DayTimelineEvent, { kind: "booking" }>, period: string) => React.ReactNode;
}) {
  const { t, i18n } = useTranslation("booking");
  const isBlockout = event.kind === "blockout";
  const formattedDate = new Intl.DateTimeFormat(i18n.language, { dateStyle: "full", timeZone: "UTC" }).format(
    new Date(`${date}T12:00:00Z`),
  );
  const duration = Math.max(0, event.endMinute - event.startMinute);
  const actions = event.kind === "booking" ? renderEventActions?.(event, exactPeriod) : null;

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
          <PopoverDescription
            className={cn("mt-0.5 truncate text-xs", isBlockout ? "text-amber-800" : "text-primary-foreground")}
          >
            {t("dayTimeline.expanded.duration", {
              date: formattedDate,
              hours: Math.floor(duration / 60),
              minutes: duration % 60,
            })}
          </PopoverDescription>
        </div>
        <PopoverClose
          type="button"
          aria-label={t("dayTimeline.event.hideDetails", { title: event.title, period: exactPeriod })}
          className={cn(
            "-mr-1 grid size-7 shrink-0 place-items-center rounded-md outline-none focus-visible:ring-3 focus-visible:ring-ring/40",
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
            <dd className="truncate font-medium">{event.bookedBy}</dd>
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
  renderEventActions,
}: {
  event: DayTimelineEvent;
  date: string;
  compactCards?: boolean;
  variant?: "timeline" | "flow";
  expanded?: boolean;
  onExpandedChange?: (expanded: boolean) => void;
  collisionBoundary?: HTMLElement | null;
  renderEventActions?: (event: Extract<DayTimelineEvent, { kind: "booking" }>, period: string) => React.ReactNode;
}) {
  const { t } = useTranslation("booking");
  const detailsId = `${React.useId()}-details`;
  const [internalExpanded, setInternalExpanded] = React.useState(false);
  const isExpanded = expanded ?? internalExpanded;
  const setExpanded = onExpandedChange ?? setInternalExpanded;
  const title = event.kind === "blockout" || event.privacy === "full" ? event.title : t("dayTimeline.event.busy");
  const notes = event.kind === "booking" && event.privacy === "busy" ? undefined : event.notes;
  const exactPeriod = period(event);
  const accessibleLabel = notes
    ? t("dayTimeline.event.labelWithNotes", { title, period: exactPeriod, notes })
    : t("dayTimeline.event.label", { title, period: exactPeriod });
  const isBlockout = event.kind === "blockout";
  const isBusy = event.kind === "booking" && event.privacy === "busy";
  const timeline = variant === "timeline";
  const expandsInPlace = isBusy && isExpanded;

  const compactCard = (
    <article
      aria-label={accessibleLabel}
      className={cn(
        "group flex w-full flex-col items-stretch justify-center gap-0 overflow-hidden rounded-sm border px-1.5 text-xs shadow-sm",
        timeline ? "absolute inset-y-0 left-0" : "relative min-h-12",
        !isBlockout && !isBusy && "border-blue-700 bg-blue-600 text-white",
        isBlockout && "border-amber-600 bg-amber-100 text-amber-950",
        isBusy && "border-slate-500 bg-slate-200 text-slate-900",
        expandsInPlace && "z-40 h-auto min-h-24 justify-start overflow-visible px-3 py-2 ring-3 ring-ring/40",
        expandsInPlace && timeline && "w-72",
      )}
    >
      <span className={cn("flex min-w-0 items-center gap-1", (expandsInPlace || !compactCards) && "pr-7")}>
        <EventIcon event={event} />
        <span className="truncate font-semibold">{title}</span>
      </span>
      <time
        dateTime={formatMinute(event.startMinute)}
        hidden={expandsInPlace}
        data-event-time
        className="truncate text-[10px] leading-none"
      >
        {compactCards ? formatMinute(event.startMinute) : exactPeriod}
      </time>
      {isBusy ? (
        <button
          type="button"
          aria-controls={detailsId}
          aria-expanded={isExpanded}
          aria-label={
            isExpanded
              ? t("dayTimeline.event.hideDetails", { title, period: exactPeriod })
              : t("dayTimeline.event.showDetails", { title, period: exactPeriod })
          }
          className={cn(
            "absolute top-1/2 right-1 z-10 flex size-6 -translate-y-1/2 items-center justify-center rounded-full border border-current/20 bg-background/90 text-foreground opacity-0 shadow-sm outline-none hover:bg-background focus:opacity-100 focus-visible:ring-3 focus-visible:ring-ring/40 group-hover:opacity-100 group-focus-within:opacity-100",
            !compactCards && "opacity-100",
            isExpanded && "top-2 translate-y-0 opacity-100",
          )}
          onClick={() => setExpanded(!isExpanded)}
        >
          <ChevronRight className={cn("size-3.5 transition-transform", isExpanded && "rotate-90")} aria-hidden="true" />
        </button>
      ) : (
        <PopoverTrigger
          aria-controls={detailsId}
          aria-expanded={isExpanded}
          aria-label={
            isExpanded
              ? t("dayTimeline.event.hideDetails", { title, period: exactPeriod })
              : t("dayTimeline.event.showDetails", { title, period: exactPeriod })
          }
          className={cn(
            "absolute top-1/2 right-1 z-10 flex size-6 -translate-y-1/2 items-center justify-center rounded-full border border-current/20 bg-background/90 text-foreground opacity-0 shadow-sm outline-none hover:bg-background focus:opacity-100 focus-visible:ring-3 focus-visible:ring-ring/40 group-hover:opacity-100 group-focus-within:opacity-100",
            !compactCards && "opacity-100",
          )}
        >
          <ChevronRight className={cn("size-3.5 transition-transform", isExpanded && "rotate-90")} aria-hidden="true" />
        </PopoverTrigger>
      )}
      {isBusy ? (
        <div id={detailsId} hidden={!isExpanded} className="space-y-1 pt-1">
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
      onOpenChange={(nextExpanded, eventDetails) => {
        if (!nextExpanded && (eventDetails.reason === "outside-press" || eventDetails.reason === "focus-out")) {
          eventDetails.cancel();
          return;
        }
        setExpanded(nextExpanded);
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
        className={cn(
          "w-[min(22rem,var(--available-width))] max-w-none gap-0 overflow-hidden rounded-lg border p-0 shadow-xl ring-4",
          isBlockout ? "border-amber-600 ring-amber-500/15" : "border-primary ring-ring/20",
        )}
      >
        <ExpandedEventCard
          date={date}
          event={event}
          exactPeriod={exactPeriod}
          renderEventActions={renderEventActions}
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
  renderEventActions,
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
  renderEventActions?: (event: Extract<DayTimelineEvent, { kind: "booking" }>, period: string) => React.ReactNode;
}) {
  const { t, i18n } = useTranslation("booking");
  const scrollerRef = React.useRef<HTMLElement>(null);
  const [collisionBoundary, setCollisionBoundary] = React.useState<HTMLElement | null>(null);
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
            className="relative"
            style={{
              width: `${(DAY_MINUTES / visibleMinutes) * 100 * zoom}%`,
              minWidth: densityHourWidth * 24 * zoom,
              height: eventAreaTop + 16 + laneCount * 44 + (positionedEvents.length === 0 ? 0 : 80),
            }}
            data-testid="day-timeline-canvas"
            data-hour-width={densityHourWidth}
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
                    className="absolute z-10 h-9"
                    style={{
                      left: `${(startMinute / DAY_MINUTES) * 100}%`,
                      width: `${((visualEndMinute - startMinute) / DAY_MINUTES) * 100}%`,
                      top: eventAreaTop + lane * 44,
                    }}
                    data-event-id={event.id}
                    data-lane={lane}
                  >
                    <DayTimelineEventCard
                      event={event}
                      date={date}
                      compactCards={compactCards}
                      variant="timeline"
                      collisionBoundary={collisionBoundary}
                      renderEventActions={renderEventActions}
                    />
                  </li>
                );
              })}
            </ol>
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
