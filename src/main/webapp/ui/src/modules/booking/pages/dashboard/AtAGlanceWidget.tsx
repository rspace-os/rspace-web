import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { TZDate } from "react-day-picker";
import { useTranslation } from "react-i18next";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { Calendar } from "@/modules/common/ui/calendar";
import { Card, CardContent } from "@/modules/common/ui/card";
import { DashboardCalendarContext, DashboardCalendarDay } from "./DashboardCalendarDay";
import { DashboardEmpty, DashboardError } from "./DashboardFeedback";
import {
  calendarDateKey,
  dashboardMonthInterval,
  fetchMonthlyDashboardBookings,
  groupDashboardBookingsByDay,
  TooManyDashboardBookingsError,
} from "./dashboardBookings";
import {
  dashboardBooking,
  dateInTimeZone,
  formatCalendarDate,
  formatDayPickerDate,
  monthStartForTimeZone,
} from "./dashboardHelpers";

export function AtAGlanceWidget({
  requesterId,
  token,
  timeZone,
  today,
}: {
  requesterId: number;
  token: string;
  timeZone: string;
  today: string;
}) {
  const { t, i18n } = useTranslation("booking");
  const { t: commonT } = useTranslation("common");
  const [month, setMonth] = useState(() => monthStartForTimeZone(timeZone));
  const [openDayKey, setOpenDayKey] = useState<string | null>(null);
  const interval = useMemo(() => dashboardMonthInterval(month, timeZone), [month, timeZone]);
  const query = useQuery({
    queryKey: ["api-v2", "bookings", "dashboard", "monthly", requesterId, timeZone, interval.start, interval.end],
    queryFn: ({ signal }) =>
      fetchMonthlyDashboardBookings({ requesterId, start: interval.start, end: interval.end, token, signal }),
    enabled: token.length > 0,
    retry: false,
    refetchOnWindowFocus: true,
  });
  const bookingsByDay = useMemo(
    () =>
      query.isSuccess
        ? groupDashboardBookingsByDay(query.data.map(dashboardBooking), interval.dates, timeZone)
        : new Map<string, readonly BookingListDocument[]>(),
    [interval.dates, query.data, query.isSuccess, timeZone],
  );
  const displayLocale = i18n.resolvedLanguage ?? i18n.language;
  const monthLabel = new Intl.DateTimeFormat(displayLocale, { month: "long", year: "numeric", timeZone: "UTC" }).format(
    new Date(Date.UTC(month.getFullYear(), month.getMonth(), 1)),
  );
  const todayDate = useMemo(() => dateInTimeZone(today, timeZone), [today, timeZone]);
  const tooMany = query.error instanceof TooManyDashboardBookingsError;
  const retryLabel = commonT("actions.retry");
  // Calendar creates its Root override on render. Keep the grid mounted while
  // context updates the open day, so popovers retain dismissal and focus state.
  const calendar = useMemo(
    () => (
      <Calendar
        mode="single"
        month={month}
        today={todayDate}
        timeZone={timeZone}
        noonSafe
        onMonthChange={(nextMonth) => {
          setOpenDayKey(null);
          setMonth(new TZDate(nextMonth.getFullYear(), nextMonth.getMonth(), 1, timeZone));
        }}
        weekStartsOn={1}
        fixedWeeks
        className="w-full max-md:[&_.rdp-today]:bg-transparent max-md:[&_[role=gridcell]]:flex max-md:[&_[role=gridcell]]:h-8 max-md:[&_[role=gridcell]]:aspect-auto max-md:[&_[role=gridcell]]:items-center max-md:[&_[role=gridcell]]:justify-center max-md:[&_[role=gridcell]]:rounded-sm max-md:[&_button[data-day]]:h-full max-md:[&_button[data-day]]:w-full max-md:[&_button[data-day]]:aspect-auto max-md:[&_button[data-day]]:rounded-sm max-md:[&_button[data-day]]:py-0"
        formatters={{
          formatCaption: (captionDate) =>
            formatDayPickerDate(captionDate, displayLocale, { month: "long", year: "numeric" }),
          formatWeekdayName: (weekdayDate) => formatDayPickerDate(weekdayDate, displayLocale, { weekday: "short" }),
        }}
        labels={{
          labelDayButton: (date, modifiers) => {
            const dateLabel = formatCalendarDate(calendarDateKey(date), displayLocale);
            return modifiers.today ? t("dashboard.calendar.todayLabel", { date: dateLabel }) : dateLabel;
          },
          labelNext: () => t("dashboard.calendar.nextMonth"),
          labelPrevious: () => t("dashboard.calendar.previousMonth"),
        }}
        components={{ DayButton: DashboardCalendarDay }}
      />
    ),
    [month, todayDate, timeZone, displayLocale, t],
  );

  return (
    <section aria-labelledby="booking-dashboard-glance" className="min-w-0 space-y-3">
      <h2 id="booking-dashboard-glance" className="text-xl font-semibold tracking-tight">
        {t("dashboard.calendar.title")}
      </h2>
      <Card className="gap-0 py-0" aria-busy={query.isPending || query.isFetching}>
        <CardContent className="p-0">
          {query.isPending ? (
            <p role="status" className="sr-only">
              {t("dashboard.calendar.loading", { month: monthLabel })}
            </p>
          ) : null}
          <DashboardCalendarContext.Provider
            value={{ bookingsByDay, timeZone, locale: displayLocale, openDayKey, setOpenDayKey }}
          >
            {calendar}
          </DashboardCalendarContext.Provider>
        </CardContent>
      </Card>
      {query.isError ? (
        <DashboardError
          title={tooMany ? t("dashboard.calendar.tooMany.title") : t("dashboard.calendar.error.title")}
          description={
            tooMany ? t("dashboard.calendar.tooMany.description") : t("dashboard.calendar.error.description")
          }
          retryLabel={tooMany ? t("dashboard.calendar.viewAll") : retryLabel}
          onRetry={() => {
            if (tooMany) window.location.assign("/booking/my-bookings?period=upcoming");
            else void query.refetch();
          }}
        />
      ) : query.isSuccess && query.data.length === 0 ? (
        <DashboardEmpty>{t("dashboard.calendar.empty")}</DashboardEmpty>
      ) : null}
    </section>
  );
}
