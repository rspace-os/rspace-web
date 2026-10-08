import { CalendarClockIcon, ChevronLeftIcon, ChevronRightIcon } from "lucide-react";
import {
  type ComponentProps,
  createContext,
  type SetStateAction,
  useContext,
  useEffect,
  useId,
  useMemo,
  useRef,
  useState,
} from "react";
import { useTranslation } from "react-i18next";
import { BookingSummaryAccordion } from "@/modules/booking/components/BookingSummaryAccordion";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { formatAgendaPeriod } from "@/modules/booking/domain/bookingTime";
import { Badge } from "@/modules/common/ui/badge";
import { Button } from "@/modules/common/ui/button";
import { CalendarDayButton } from "@/modules/common/ui/calendar";
import { Popover, PopoverContent, PopoverDescription, PopoverTitle, PopoverTrigger } from "@/modules/common/ui/popover";
import { cn } from "@/modules/common/utils/cn";
import { calendarDateKey } from "./dashboardBookings";
import { dashboardBooking, formatCalendarDate } from "./dashboardHelpers";

export type DashboardCalendarContextValue = {
  bookingsByDay: ReadonlyMap<string, readonly BookingListDocument[]>;
  timeZone: string;
  locale: string;
  openDayKey: string | null;
  setOpenDayKey: (value: SetStateAction<string | null>) => void;
};

export const DashboardCalendarContext = createContext<DashboardCalendarContextValue | null>(null);

function RestrictedBookingRow({ heading, period, label }: { heading: string; period: string; label: string }) {
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

export function DashboardCalendarDay(props: ComponentProps<typeof CalendarDayButton>) {
  const { t } = useTranslation("booking");
  const context = useContext(DashboardCalendarContext);
  if (!context) throw new Error("DashboardCalendarDay must be rendered inside DashboardCalendarContext");
  const { bookingsByDay, timeZone, locale, openDayKey, setOpenDayKey } = context;
  const { day, children, modifiers, className, ...buttonProps } = props;
  const dateKey = calendarDateKey(day.date);
  const bookings = useMemo(() => (bookingsByDay.get(dateKey) ?? []).map(dashboardBooking), [bookingsByDay, dateKey]);
  const bookingSignature = bookings.map((booking) => `${booking.id}:${booking.start}:${booking.end}`).join("|");
  const [requestedPage, setRequestedPage] = useState(0);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const accordionName = `${dateKey}-${useId()}`;

  useEffect(() => {
    setRequestedPage(0);
  }, [dateKey, bookingSignature]);
  useEffect(() => {
    if (modifiers.focused) buttonRef.current?.focus();
  }, [modifiers.focused]);

  if (bookings.length === 0) {
    return <CalendarDayButton {...props} className={cn(className, modifiers.today && "max-md:bg-muted")} />;
  }

  const pageCount = Math.ceil(bookings.length / 5);
  const page = Math.min(requestedPage, Math.max(pageCount - 1, 0));
  const firstBooking = page * 5;
  const visibleBookings = bookings.slice(firstBooking, firstBooking + 5);
  const dateLabel = formatCalendarDate(dateKey, locale);

  return (
    <Popover
      open={openDayKey === dateKey}
      onOpenChange={(nextOpen) => {
        setOpenDayKey((current) => (nextOpen ? dateKey : current === dateKey ? null : current));
      }}
    >
      <PopoverTrigger
        openOnHover
        delay={0}
        closeDelay={0}
        {...buttonProps}
        ref={buttonRef}
        render={<Button variant="ghost" />}
        data-day={dateKey}
        aria-label={t("dashboard.calendar.dayLabel", {
          date: dateLabel,
          count: bookings.length,
        })}
        className={cn(
          "relative aspect-square h-auto w-full min-w-(--cell-size) flex-col border-0 bg-blue-50 p-0 font-normal text-blue-900 dark:bg-blue-950 dark:text-blue-100",
          className,
        )}
      >
        {children}
        <Badge
          aria-hidden="true"
          className="absolute right-0.5 top-0.5 h-4 min-w-4 rounded-full px-1 py-0 text-[10px] leading-none"
        >
          {bookings.length > 99 ? "99+" : bookings.length}
        </Badge>
      </PopoverTrigger>
      <PopoverContent
        side="bottom"
        align="start"
        sideOffset={8}
        collisionPadding={8}
        sticky
        className="max-h-[min(32rem,var(--available-height))] w-80 max-w-[calc(100vw-1rem)] gap-3 overflow-y-auto overscroll-contain rounded-sm p-3 duration-0 data-closed:animate-none data-open:animate-none"
      >
        <div className="min-w-0">
          <PopoverTitle className="text-base font-semibold leading-tight">{dateLabel}</PopoverTitle>
          <PopoverDescription className="mt-1 text-xs">
            {t("dashboard.calendar.bookingCount", {
              count: bookings.length,
            })}
          </PopoverDescription>
        </div>
        <ul className="min-h-[15.5rem] space-y-1">
          {visibleBookings.map((booking) =>
            (() => {
              const itemName = booking.target?.value.name ?? t("calendar.feed.unknownItem");
              const period = formatAgendaPeriod(booking.start, booking.end, timeZone, locale);
              return booking.privacy === "busy" ? (
                <RestrictedBookingRow key={booking.id} heading={itemName} period={period} label={t("calendar.busy")} />
              ) : (
                <BookingSummaryAccordion
                  key={booking.id}
                  accordionName={accordionName}
                  heading={itemName}
                  summaryLabel={itemName}
                  period={period}
                  purpose={booking.purpose}
                  detailsBookingId={booking.id}
                />
              );
            })(),
          )}
        </ul>
        {pageCount > 1 ? (
          <div className="flex items-center justify-between border-t pt-2 text-xs text-muted-foreground">
            <span>
              {t("dashboard.calendar.range", {
                start: firstBooking + 1,
                end: Math.min(firstBooking + 5, bookings.length),
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
      </PopoverContent>
    </Popover>
  );
}
