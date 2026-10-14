import { ChevronRight, LockKeyhole, Wrench } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { zonedDayBounds } from "@/modules/booking/domain/bookingTime";
import { Popover, PopoverContent, PopoverTrigger } from "@/modules/common/ui/popover";
import { UserBadge } from "@/modules/common/ui/user-badge";
import { cn } from "@/modules/common/utils/cn";
import {
  type DayTimelineEvent,
  dateForMinute,
  formatDayDate,
  formatMinute,
  formatMinuteWithDayOffset,
  period,
} from "./DayTimelineEvent";
import { ExpandedEventCard } from "./ExpandedEventCard";

function EventIcon({ event }: { event: DayTimelineEvent }) {
  if (event.kind === "blockout") return <Wrench className="size-3.5 shrink-0" aria-hidden="true" />;
  if (event.privacy === "busy") return <LockKeyhole className="size-3.5 shrink-0" aria-hidden="true" />;
  return null;
}

export function DayTimelineEventCard({
  event,
  date,
  timezone = "UTC",
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
  timezone?: string;
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
  const exactPeriod = period(event, date, timezone);
  const accessibleLabel = notes
    ? t("dayTimeline.event.labelWithNotes", { title: labelledTitle, period: exactPeriod, notes })
    : t("dayTimeline.event.label", { title: labelledTitle, period: exactPeriod });
  const isBlockout = event.kind === "blockout";
  const isBusy = event.kind === "booking" && event.privacy === "busy";
  const timeline = variant === "timeline";
  const expandsInPlace = isBusy && isExpanded;
  const startDate = dateForMinute(date, timezone, event.startMinute);
  const endDate = dateForMinute(date, timezone, event.endMinute);
  const compactDate =
    startDate === endDate ? formatDayDate(startDate) : `${formatDayDate(startDate)} - ${formatDayDate(endDate)}`;
  const compactPeriod = `${formatMinuteWithDayOffset(date, timezone, event.startMinute)} - ${formatMinuteWithDayOffset(date, timezone, event.endMinute)}`;
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
        dateTime={`${startDate}T${formatMinute(date, timezone, event.startMinute)}`}
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
      {event.endMinute > zonedDayBounds(date, timezone).elapsedMinutes && (
        <span className="absolute inset-y-0 right-0 w-1 bg-current" aria-hidden="true" />
      )}
    </article>
  );

  if (event.kind === "booking" && event.privacy === "busy") return compactCard;

  return (
    <Popover
      open={isExpanded}
      onOpenChange={(nextOpen, eventDetails) => {
        if (
          !nextOpen &&
          eventDetails.event.target instanceof Element &&
          eventDetails.event.target.closest("[data-timeline-window-editor]")
        ) {
          eventDetails.cancel();
          return;
        }
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
          timezone={timezone}
          event={event}
          exactPeriod={exactPeriod}
          renderEventActions={renderEventActions}
          renderBlockoutActions={renderBlockoutActions}
        />
      </PopoverContent>
    </Popover>
  );
}
