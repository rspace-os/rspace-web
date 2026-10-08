import { CalendarClockIcon, ChevronLeftIcon, ChevronRightIcon } from "lucide-react";
import { type ReactNode, useEffect, useId, useState } from "react";
import { useTranslation } from "react-i18next";
import { BookingInstrumentTimeTooltip } from "@/modules/booking/components/BookingInstrumentTimeTooltip";
import { BookingSummaryAccordion } from "@/modules/booking/components/BookingSummaryAccordion";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { formatAgendaPeriod } from "@/modules/booking/domain/bookingTime";
import { Button } from "@/modules/common/ui/button";

const PAGE_SIZE = 5;

function RestrictedBookingRow({ heading, period, label }: { heading: string; period: ReactNode; label: string }) {
  return (
    <li className="flex min-w-0 items-center gap-2 rounded-sm border bg-background px-2 py-1.5">
      <span className="flex size-7 shrink-0 items-center justify-center rounded-sm bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-200">
        <CalendarClockIcon className="size-4" aria-hidden="true" />
      </span>
      <span className="min-w-0 flex-1">
        <span className="block truncate text-xs font-medium">{heading}</span>
        <span className="block truncate text-[11px] text-muted-foreground">
          {label}
          {" · "}
          {period}
        </span>
      </span>
    </li>
  );
}

/**
 * An invisible collapsed row. It fills a short last page to a full page's height, so the page buttons stay
 * in place; each line reserves one line box of its row's text, whatever the font.
 */
function PlaceholderRow() {
  return (
    <li aria-hidden="true" className="invisible flex min-w-0 items-center gap-2 rounded-sm border px-2 py-1.5">
      <span className="size-7 shrink-0" />
      <span className="min-w-0 flex-1">
        <span className="block h-lh text-xs font-medium" />
        <span className="block h-lh text-[11px]" />
      </span>
    </li>
  );
}

/**
 * Bookings as expandable summaries, five to a page, as the dashboard's At a glance day lists them.
 * A busy booking shows only its item and time.
 */
export function BookingSummaryList({
  bookings,
  timeZone,
  showActor = false,
}: {
  bookings: readonly BookingListDocument[];
  timeZone: string;
  /** Names who booked each booking, for a list that mixes several people's bookings. */
  showActor?: boolean;
}) {
  const { t } = useTranslation("booking");
  const accordionName = useId();
  const bookingSignature = bookings.map((booking) => `${booking.id}:${booking.start}:${booking.end}`).join("|");
  const [requestedPage, setRequestedPage] = useState(0);

  useEffect(() => {
    setRequestedPage(0);
  }, [bookingSignature]);

  const pageCount = Math.ceil(bookings.length / PAGE_SIZE);
  const page = Math.min(requestedPage, Math.max(pageCount - 1, 0));
  const firstBooking = page * PAGE_SIZE;
  const visibleBookings = bookings.slice(firstBooking, firstBooking + PAGE_SIZE);
  const placeholderCount = pageCount > 1 ? PAGE_SIZE - visibleBookings.length : 0;

  return (
    <>
      <ul className="space-y-1">
        {visibleBookings.map((booking) => {
          const itemName = booking.target?.value.name ?? t("calendar.feed.unknownItem");
          const period = formatAgendaPeriod(booking.start, booking.end, timeZone);
          const periodTooltip = {
            start: booking.start,
            end: booking.end,
            displayTimeZone: timeZone,
            instrumentTimeZone: booking.timezone,
          };
          if (booking.privacy === "busy") {
            return (
              <RestrictedBookingRow
                key={booking.id}
                heading={itemName}
                period={<BookingInstrumentTimeTooltip {...periodTooltip}>{period}</BookingInstrumentTimeTooltip>}
                label={t("calendar.busy")}
              />
            );
          }
          const maintenance = booking.kind === "MAINTENANCE";
          return (
            <BookingSummaryAccordion
              key={booking.id}
              accordionName={accordionName}
              heading={itemName}
              summaryLabel={itemName}
              period={period}
              periodTooltip={periodTooltip}
              purpose={booking.purpose}
              maintenance={maintenance}
              actor={showActor ? (maintenance ? booking.createdBy : booking.bookedBy) : undefined}
              detailsBookingId={booking.id}
            />
          );
        })}
        {Array.from({ length: placeholderCount }, (_, index) => (
          <PlaceholderRow key={`placeholder-${index}`} />
        ))}
      </ul>
      {pageCount > 1 ? (
        <div className="flex items-center justify-between border-t pt-2 text-xs text-muted-foreground">
          <span>
            {t("dashboard.calendar.range", {
              start: firstBooking + 1,
              end: Math.min(firstBooking + PAGE_SIZE, bookings.length),
              total: bookings.length,
            })}
          </span>
          <div className="flex gap-1">
            <Button
              type="button"
              aria-label={t("dashboard.calendar.previous")}
              size="icon-xs"
              variant="ghost"
              disabled={page === 0}
              onClick={() => setRequestedPage(page - 1)}
            >
              <ChevronLeftIcon aria-hidden="true" />
            </Button>
            <Button
              type="button"
              aria-label={t("dashboard.calendar.next")}
              size="icon-xs"
              variant="ghost"
              disabled={page + 1 === pageCount}
              onClick={() => setRequestedPage(page + 1)}
            >
              <ChevronRightIcon aria-hidden="true" />
            </Button>
          </div>
        </div>
      ) : null}
    </>
  );
}
