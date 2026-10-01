import { ChevronDownIcon } from "lucide-react";
import { useEffect, useId, useState } from "react";
import { useTranslation } from "react-i18next";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import {
  formatBookingAgendaDate,
  formatBookingAgendaTimeRange,
  getBookingAgendaRelativeDay,
  getBookingAgendaTodayDate,
  groupBookingsByStartDate,
} from "@/modules/booking/domain/bookingAgenda";
import { BookingSummaryCard } from "./BookingSummaryCard";

type ControlledExpansion = {
  expandedIds: ReadonlySet<number>;
  onExpandedIdsChange: (ids: ReadonlySet<number>) => void;
};

type LocalExpansion = {
  expandedIds?: never;
  onExpandedIdsChange?: never;
};

export type BookingAgendaProps = {
  bookings: readonly BookingListDocument[];
  timeZone: string;
  now: number;
} & (ControlledExpansion | LocalExpansion);

export function BookingAgenda(props: BookingAgendaProps) {
  const { t, i18n } = useTranslation("booking");
  const { t: commonT } = useTranslation("common");
  const agendaId = useId();
  const [localExpandedIds, setLocalExpandedIds] = useState<ReadonlySet<number>>(() => new Set());
  const expandedIds = props.expandedIds ?? localExpandedIds;
  const locale = i18n.resolvedLanguage ?? i18n.language;
  const groups = groupBookingsByStartDate(props.bookings, props.timeZone);
  const todayDate = getBookingAgendaTodayDate(props.now, props.timeZone);
  const currentYear = Number(todayDate.slice(0, 4));

  useEffect(() => {
    const bookingIds = new Set(props.bookings.map(({ id }) => id));
    setLocalExpandedIds((current) => {
      const next = new Set([...current].filter((id) => bookingIds.has(id)));
      return next.size === current.size ? current : next;
    });
  }, [props.bookings]);

  function changeExpanded(bookingId: number, wasExpanded: boolean, isExpanded: boolean) {
    if (wasExpanded === isExpanded) return;
    const next = new Set(expandedIds);
    if (isExpanded) next.add(bookingId);
    else next.delete(bookingId);
    if (props.expandedIds !== undefined) props.onExpandedIdsChange(next);
    else setLocalExpandedIds(next);
  }

  return (
    <div data-slot="booking-agenda" className="min-w-0">
      {groups.map(({ date, bookings }) => {
        const relativeDay = getBookingAgendaRelativeDay(date, props.now, props.timeZone);
        const dayLabel =
          relativeDay === "today"
            ? t("dashboard.agenda.today")
            : relativeDay === "tomorrow"
              ? t("dashboard.agenda.tomorrow")
              : formatBookingAgendaDate(date, locale, currentYear);

        return (
          <section key={date} aria-labelledby={`${agendaId}-day-${date}`} className="min-w-0">
            <h3
              id={`${agendaId}-day-${date}`}
              className="sticky top-0 z-10 scroll-mt-8 border-b bg-background/95 px-3 py-1.5 text-xs font-semibold backdrop-blur"
            >
              {dayLabel}
            </h3>
            <ul className="m-0 list-none divide-y p-0">
              {bookings.map((booking) => {
                const itemName = booking.target?.value.name ?? commonT("values.unknownItem");
                const purpose = booking.privacy === "full" ? booking.purpose : null;
                const isExpanded = expandedIds.has(booking.id);
                const period = formatBookingAgendaTimeRange(booking.start, booking.end, props.timeZone, locale);

                return (
                  <li key={booking.id} className="min-w-0">
                    <details
                      open={isExpanded}
                      onToggle={(event) => changeExpanded(booking.id, isExpanded, event.currentTarget.open)}
                      className="group min-w-0"
                    >
                      <summary className="grid w-full scroll-mt-8 cursor-pointer list-none grid-cols-[minmax(0,7.5rem)_minmax(0,1fr)_1rem] items-start gap-x-2 px-3 py-2 text-left hover:bg-muted/40 focus-visible:outline focus-visible:outline-2 focus-visible:outline-ring [&::-webkit-details-marker]:hidden">
                        <span className="min-w-0 whitespace-normal break-words pt-0.5 text-xs tabular-nums">
                          {period}
                        </span>
                        <span className="min-w-0">
                          <span className="block truncate text-sm font-medium" title={itemName}>
                            {itemName}
                          </span>
                          {purpose ? (
                            <span className="block truncate text-xs text-muted-foreground" title={purpose}>
                              {purpose}
                            </span>
                          ) : null}
                        </span>
                        <ChevronDownIcon
                          aria-hidden="true"
                          className="mt-0.5 size-4 text-muted-foreground transition-transform duration-150 group-open:rotate-180 motion-reduce:transition-none"
                        />
                      </summary>
                      <div className="px-3 py-2">
                        <BookingSummaryCard booking={booking} timeZone={props.timeZone} variant="plain" />
                      </div>
                    </details>
                  </li>
                );
              })}
            </ul>
          </section>
        );
      })}
    </div>
  );
}
