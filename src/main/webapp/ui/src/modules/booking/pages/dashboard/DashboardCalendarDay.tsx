import { type ComponentProps, createContext, type SetStateAction, useContext, useEffect, useMemo, useRef } from "react";
import { flushSync } from "react-dom";
import { useTranslation } from "react-i18next";
import { BookingSummaryList } from "@/modules/booking/components/BookingSummaryList";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
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

export function DashboardCalendarDay(props: ComponentProps<typeof CalendarDayButton>) {
  const { t } = useTranslation("booking");
  const context = useContext(DashboardCalendarContext);
  if (!context) throw new Error("DashboardCalendarDay must be rendered inside DashboardCalendarContext");
  const { bookingsByDay, timeZone, locale, openDayKey, setOpenDayKey } = context;
  const { day, children, modifiers, className, ...buttonProps } = props;
  const dateKey = calendarDateKey(day.date);
  const bookings = useMemo(() => (bookingsByDay.get(dateKey) ?? []).map(dashboardBooking), [bookingsByDay, dateKey]);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const popupRef = useRef<HTMLDivElement>(null);
  const openedByHover = useRef(false);

  useEffect(() => {
    if (modifiers.focused) buttonRef.current?.focus();
  }, [modifiers.focused]);

  if (bookings.length === 0) {
    return <CalendarDayButton {...props} className={cn(className, modifiers.today && "max-md:bg-muted")} />;
  }

  const dateLabel = formatCalendarDate(dateKey, locale);

  return (
    <Popover
      open={openDayKey === dateKey}
      onOpenChange={(nextOpen, eventDetails) => {
        openedByHover.current = nextOpen && eventDetails.reason === "trigger-hover";
        setOpenDayKey((current) => (nextOpen ? dateKey : current === dateKey ? null : current));
      }}
    >
      <PopoverTrigger
        openOnHover
        delay={0}
        closeDelay={0}
        {...buttonProps}
        onClick={(event) => {
          // Base UI closes a hover-opened popover on a click more than 500 ms after it opened. From the keyboard,
          // Enter and Space mean open, so close it first: Base UI then opens it as a keyboard press and moves focus in.
          // This handler runs before Base UI's own click handler.
          if (event.detail === 0 && openedByHover.current && openDayKey === dateKey) {
            openedByHover.current = false;
            flushSync(() => setOpenDayKey(null));
          }
          buttonProps.onClick?.(event);
        }}
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
        ref={popupRef}
        // Focus the dialog, so its date and booking count are read first. Focusing the first booking would
        // open that booking's time tooltip, which then takes the first Escape instead of this popover.
        initialFocus={popupRef}
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
        <BookingSummaryList bookings={bookings} timeZone={timeZone} className="min-h-[15.5rem]" />
      </PopoverContent>
    </Popover>
  );
}
