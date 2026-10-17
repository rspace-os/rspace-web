import { Wrench, X } from "lucide-react";
import { Fragment } from "react";
import { useTranslation } from "react-i18next";
import { InventoryItem, InventoryLocationLink } from "@/modules/common/ui/inventory-item";
import { PopoverClose, PopoverDescription, PopoverTitle } from "@/modules/common/ui/popover";
import { UnknownItem } from "@/modules/common/ui/unknown-item";
import { cn } from "@/modules/common/utils/cn";
import { BookingDetailRows } from "./BookingDetailRows";
import {
  type DayTimelineActionRenderers,
  type DayTimelineEvent,
  dateForMinute,
  formatDayDate,
  formatMinute,
  formatMinuteWithDayOffset,
} from "./DayTimelineEvent";

type DetailedDayTimelineEvent =
  | Extract<DayTimelineEvent, { kind: "blockout" }>
  | Extract<DayTimelineEvent, { kind: "booking"; privacy: "full" }>;

export function ExpandedEventCard({
  date,
  timezone,
  event,
  exactPeriod,
  renderEventActions,
  renderBlockoutActions,
}: DayTimelineActionRenderers & {
  date: string;
  timezone: string;
  event: DetailedDayTimelineEvent;
  exactPeriod: string;
}) {
  const { t } = useTranslation("booking");
  const isBlockout = event.kind === "blockout";
  const startDate = dateForMinute(date, timezone, event.startMinute);
  const endDate = dateForMinute(date, timezone, event.endMinute);
  const spansMultipleDates = startDate !== endDate;
  const labelledTitle =
    event.kind === "booking"
      ? t("dayTimeline.event.titleWithBooker", { title: event.title, booker: event.bookedBy })
      : event.title;
  const duration = Math.max(0, event.endMinute - event.startMinute);
  const actions =
    event.kind === "booking" ? renderEventActions?.(event, exactPeriod) : renderBlockoutActions?.(event, exactPeriod);
  const dateTimeRows = [
    { key: "start", label: t("bookings.form.start"), date: startDate, minute: event.startMinute },
    { key: "end", label: t("bookings.form.end"), date: endDate, minute: event.endMinute },
  ];

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
          <div className="flex flex-wrap items-baseline gap-x-1.5">
            <PopoverTitle className="font-semibold text-lg tabular-nums leading-tight">{exactPeriod}</PopoverTitle>
            <PopoverDescription className={cn("text-xs", isBlockout ? "text-amber-800" : "text-primary-foreground")}>
              {t("dayTimeline.expanded.duration", {
                hours: Math.floor(duration / 60),
                minutes: duration % 60,
              })}
            </PopoverDescription>
          </div>
          {spansMultipleDates ? (
            <dl className="mt-1.5 grid grid-cols-[2.5rem_minmax(0,1fr)] gap-x-2 gap-y-0.5 text-xs leading-4">
              {dateTimeRows.map(({ key, label, date: endpointDate, minute }) => (
                <Fragment key={key}>
                  <dt className={cn(isBlockout ? "text-amber-800" : "text-primary-foreground")}>{label}</dt>
                  <dd>
                    <time dateTime={`${endpointDate}T${formatMinute(date, timezone, minute)}`}>
                      {t("dayTimeline.expanded.dateTime", {
                        date: formatDayDate(endpointDate),
                        time: formatMinuteWithDayOffset(date, timezone, minute),
                      })}
                    </time>
                  </dd>
                </Fragment>
              ))}
            </dl>
          ) : (
            <time dateTime={startDate} className="mt-1.5 block text-xs leading-4">
              {formatDayDate(startDate)}
            </time>
          )}
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

      <dl className="divide-y divide-border text-sm">
        <div className="px-4 py-2">
          <dt className="sr-only">{t("dayTimeline.expanded.item")}</dt>
          <dd>
            {event.item.globalId ? (
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
            ) : (
              <UnknownItem size="xs" />
            )}
          </dd>
        </div>
        <BookingDetailRows
          actor={event.kind === "booking" ? event.bookedBy : event.createdBy}
          showActor={event.kind === "booking" || Boolean(event.createdBy)}
          actorLabel={
            event.kind === "booking"
              ? t("dayTimeline.expanded.bookedBy")
              : event.createdBy
                ? t("dayTimeline.expanded.createdBy")
                : ""
          }
          detailsLabel={isBlockout ? t("dayTimeline.expanded.notes") : t("dayTimeline.expanded.purpose")}
          details={event.notes}
          rowClassName="grid grid-cols-[4.5rem_1fr] gap-2 px-4 py-2"
        />
      </dl>

      {actions}
    </>
  );
}
