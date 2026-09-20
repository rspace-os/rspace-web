import { useQuery } from "@tanstack/react-query";
import { Link, linkOptions } from "@tanstack/react-router";
import {
  CalendarClockIcon,
  CalendarDaysIcon,
  ChevronLeftIcon,
  ChevronRightIcon,
  ClipboardListIcon,
  EyeIcon,
  MicroscopeIcon,
} from "lucide-react";
import {
  type ComponentProps,
  createContext,
  type ReactNode,
  type SetStateAction,
  Suspense,
  useContext,
  useEffect,
  useId,
  useMemo,
  useRef,
  useState,
} from "react";
import { TZDate } from "react-day-picker";
import { useTranslation } from "react-i18next";
import { BookingSummaryAccordion } from "@/modules/booking/components/BookingSummaryAccordion";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { todayInTimeZone, useBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import { formatAgendaPeriod } from "@/modules/booking/domain/bookingTime";
import { useAlignedMinute } from "@/modules/booking/hooks/useAlignedMinute";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { TableList } from "@/modules/common/table-list/TableList";
import type { TableListFeatures, TableListUiColumn } from "@/modules/common/table-list/tableListState";
import { Badge } from "@/modules/common/ui/badge";
import { Button, buttonVariants } from "@/modules/common/ui/button";
import { Calendar, CalendarDayButton } from "@/modules/common/ui/calendar";
import { Card, CardContent } from "@/modules/common/ui/card";
import { Popover, PopoverContent, PopoverDescription, PopoverTitle, PopoverTrigger } from "@/modules/common/ui/popover";
import { Skeleton } from "@/modules/common/ui/skeleton";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/modules/common/ui/tooltip";
import { Heading } from "@/modules/common/ui/typography";
import { cn } from "@/modules/common/utils/cn";
import { bookingListConfig } from "../my-bookings/bookingList";
import {
  calendarDateKey,
  dashboardMonthInterval,
  fetchMonthlyDashboardBookings,
  fetchUpcomingDashboardBookings,
  groupDashboardBookingsByDay,
  TooManyDashboardBookingsError,
} from "./dashboardBookings";

const dashboardTableFeatures: TableListFeatures<BookingListDocument> = {
  filtering: false,
  sorting: false,
  pagination: false,
  columns: false,
};

const dashboardPagePadding = "space-y-8 p-4 sm:p-8";

function monthStartForTimeZone(timeZone: string): Date {
  const [year, month] = todayInTimeZone(timeZone).split("-").map(Number);
  return new TZDate(year, month - 1, 1, timeZone);
}

function dateInTimeZone(date: string, timeZone: string): Date {
  const [year, month, day] = date.split("-").map(Number);
  return new TZDate(year, month - 1, day, timeZone);
}

function formatDayPickerDate(date: Date, locale: string, options: Intl.DateTimeFormatOptions): string {
  const dateKey = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
  return new Intl.DateTimeFormat(locale, { ...options, timeZone: "UTC" }).format(new Date(`${dateKey}T00:00:00Z`));
}

function formatCalendarDate(dateKey: string, locale: string): string {
  return new Intl.DateTimeFormat(locale, {
    weekday: "long",
    day: "numeric",
    month: "long",
    year: "numeric",
    timeZone: "UTC",
  }).format(new Date(`${dateKey}T00:00:00Z`));
}

/** Busy responses are intentionally reduced before passing them to any display component. */
function dashboardBooking(row: BookingListDocument): BookingListDocument {
  return row.privacy === "busy" ? { ...row, canViewConfiguration: false, purpose: null } : row;
}

function DashboardError({
  title,
  description,
  retryLabel,
  onRetry,
}: {
  title: string;
  description: string;
  retryLabel: string;
  onRetry: () => void;
}) {
  return (
    <div role="alert" className="space-y-3 p-6 text-sm">
      <p className="font-medium text-foreground">{title}</p>
      <p className="text-muted-foreground">{description}</p>
      <Button type="button" variant="outline" size="sm" onClick={onRetry}>
        {retryLabel}
      </Button>
    </div>
  );
}

function DashboardEmpty({ children }: { children: ReactNode }) {
  return <p className="p-6 text-sm text-muted-foreground">{children}</p>;
}

type QuickActionCardProps = {
  label: string;
  description: string;
  icon: typeof CalendarDaysIcon;
};

function QuickActionCard({ label, description, icon: Icon }: QuickActionCardProps) {
  return (
    <Card className="relative h-24 gap-0 overflow-hidden border-border/80 bg-card py-0 transition-colors group-hover:border-primary/45 group-hover:bg-accent/45">
      <CardContent className="relative z-10 flex h-full flex-col justify-start gap-0.5 px-4 py-2">
        <span className="max-w-[12rem] text-base font-semibold leading-tight text-foreground">{label}</span>
        <span className="max-w-[12rem] text-xs text-muted-foreground">{description}</span>
      </CardContent>
      <Icon
        className="pointer-events-none absolute -bottom-3 -right-3 size-16 stroke-[1.25] text-primary/20 transition-transform group-hover:-translate-x-1 group-hover:-translate-y-1"
        aria-hidden="true"
      />
    </Card>
  );
}

export function DashboardQuickActions({ today }: { today: string }) {
  const { t } = useTranslation("booking");
  const actions = [
    {
      key: "calendar",
      label: t("dashboard.quickActions.calendar.label"),
      description: t("dashboard.quickActions.calendar.description"),
      icon: CalendarDaysIcon,
      link: linkOptions({ to: "/booking/calendar", search: { date: today } }),
    },
    {
      key: "all-items",
      label: t("dashboard.quickActions.allItems.label"),
      description: t("dashboard.quickActions.allItems.description"),
      icon: MicroscopeIcon,
      link: linkOptions({ to: "/booking/all-items", search: { date: today } }),
    },
    {
      key: "my-bookings",
      label: t("dashboard.quickActions.myBookings.label"),
      description: t("dashboard.quickActions.myBookings.description"),
      icon: ClipboardListIcon,
      link: linkOptions({ to: "/booking/my-bookings", search: { period: "upcoming" } }),
    },
  ] as const;

  return (
    <section aria-labelledby="booking-dashboard-quick-actions" className="min-w-0 space-y-3">
      <h2 id="booking-dashboard-quick-actions" className="text-xl font-semibold tracking-tight">
        {t("dashboard.quickActions.title")}
      </h2>
      <div className="grid gap-3 sm:grid-cols-3">
        {actions.map((action) => (
          <Link
            key={action.key}
            {...action.link}
            className="group block min-w-0 rounded-sm outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
          >
            <QuickActionCard label={action.label} description={action.description} icon={action.icon} />
          </Link>
        ))}
      </div>
    </section>
  );
}

function UpcomingBookingTable({ rows, timeZone }: { rows: readonly BookingListDocument[]; timeZone: string }) {
  const { t } = useTranslation("booking");
  const config = useMemo(() => resolveCollectionConfig(bookingListConfig(timeZone)), [timeZone]);
  const uiColumns = useMemo<readonly TableListUiColumn<BookingListDocument>[]>(
    () => [
      {
        id: "details",
        label: t("myBookings.actions.label"),
        card: { placement: "footer" },
        renderCell: (row) =>
          row.privacy === "full" ? (
            <Tooltip>
              <TooltipTrigger
                render={
                  <Link
                    to="/booking/calendar/bookings/$id"
                    params={{ id: String(row.id) }}
                    aria-label={t("myBookings.actions.viewDetails")}
                    className={buttonVariants({ variant: "outline", size: "icon-lg" })}
                    data-slot="button"
                  />
                }
              >
                <EyeIcon aria-hidden="true" />
              </TooltipTrigger>
              <TooltipContent role="tooltip">{t("myBookings.actions.viewDetails")}</TooltipContent>
            </Tooltip>
          ) : null,
      },
    ],
    [t],
  );

  return (
    <TableList
      config={config}
      rows={rows}
      getRowId={(row) => String(row.id)}
      features={dashboardTableFeatures}
      clientSide
      hideHeader
      queryString={false}
      reserveEmptyRows={false}
      variant="transparent"
      uiColumns={uiColumns}
    />
  );
}

function UpcomingBookingSkeleton() {
  return (
    <div className="divide-y" aria-hidden="true">
      {Array.from({ length: 5 }, (_, index) => (
        <div key={index} className="flex items-center gap-4 px-4 py-3">
          <div className="min-w-0 flex-1 space-y-2">
            <Skeleton className="h-4 w-2/5" />
            <Skeleton className="h-3 w-3/5" />
          </div>
          <Skeleton className="h-8 w-20" />
        </div>
      ))}
    </div>
  );
}

export function UpcomingBookingsWidget({
  requesterId,
  token,
  timeZone,
  asOf,
}: {
  requesterId: number;
  token: string;
  timeZone: string;
  asOf: number;
}) {
  const { t } = useTranslation("booking");
  const { t: commonT } = useTranslation("common");
  const asOfIso = useMemo(() => new Date(asOf).toISOString(), [asOf]);
  const query = useQuery({
    queryKey: ["api-v2", "bookings", "dashboard", "upcoming", requesterId, timeZone, asOfIso],
    queryFn: ({ signal }) => fetchUpcomingDashboardBookings({ requesterId, asOf: asOfIso, token, signal }),
    enabled: token.length > 0,
    retry: false,
    refetchOnWindowFocus: true,
  });
  const rows = useMemo(() => (query.isSuccess ? query.data.map(dashboardBooking) : []), [query.data, query.isSuccess]);
  const retryLabel = commonT("actions.retry");

  return (
    <section aria-labelledby="booking-dashboard-upcoming" className="min-w-0 space-y-3">
      <div className="flex items-baseline justify-between gap-4">
        <h2 id="booking-dashboard-upcoming" className="text-xl font-semibold tracking-tight">
          {t("dashboard.upcoming.title")}
        </h2>
        <Link
          to="/booking/my-bookings"
          search={{ period: "upcoming" }}
          className="shrink-0 text-sm font-medium text-primary underline-offset-4 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          {t("dashboard.upcoming.viewAll")}
        </Link>
      </div>
      <Card className="overflow-hidden">
        <CardContent className="p-0">
          {query.isPending ? (
            <div aria-busy="true">
              <p role="status" className="sr-only">
                {t("dashboard.upcoming.loading")}
              </p>
              <UpcomingBookingSkeleton />
            </div>
          ) : query.isError ? (
            <DashboardError
              title={t("dashboard.upcoming.error.title")}
              description={t("dashboard.upcoming.error.description")}
              retryLabel={retryLabel}
              onRetry={() => void query.refetch()}
            />
          ) : rows.length === 0 ? (
            <DashboardEmpty>{t("dashboard.upcoming.empty")}</DashboardEmpty>
          ) : (
            <div aria-busy={query.isFetching}>
              <UpcomingBookingTable rows={rows} timeZone={timeZone} />
            </div>
          )}
        </CardContent>
      </Card>
    </section>
  );
}

type DashboardCalendarContextValue = {
  bookingsByDay: ReadonlyMap<string, readonly BookingListDocument[]>;
  timeZone: string;
  locale: string;
  openDayKey: string | null;
  setOpenDayKey: (value: SetStateAction<string | null>) => void;
};

const DashboardCalendarContext = createContext<DashboardCalendarContextValue | null>(null);

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

function DashboardCalendarDay(props: ComponentProps<typeof CalendarDayButton>) {
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
            booking.privacy === "busy" ? (
              <RestrictedBookingRow
                key={booking.id}
                heading={booking.target.value.name}
                period={formatAgendaPeriod(booking.start, booking.end, timeZone, locale)}
                label={t("calendar.busy")}
              />
            ) : (
              <BookingSummaryAccordion
                key={booking.id}
                accordionName={accordionName}
                heading={booking.target.value.name}
                summaryLabel={booking.target.value.name}
                period={formatAgendaPeriod(booking.start, booking.end, timeZone, locale)}
                purpose={booking.purpose}
                detailsBookingId={booking.id}
              />
            ),
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

function DashboardSkeleton() {
  const { t } = useTranslation("common");
  return (
    <main className={dashboardPagePadding} aria-busy="true">
      <p role="status" className="sr-only">
        {t("loading")}
      </p>
      <Skeleton className="h-10 w-48" />
      <div className="space-y-8" aria-hidden="true">
        <Skeleton className="h-24 w-full" />
        <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_20rem]">
          <Skeleton className="h-80 w-full" />
          <Skeleton className="h-80 w-full" />
        </div>
      </div>
    </main>
  );
}

function BookingDashboardContent() {
  const { data: currentUser } = useCurrentUserQuery();
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const preferences = useBookingDisplayPreferences();
  const asOf = useAlignedMinute();
  const today = todayInTimeZone(preferences.timeZone, new Date(asOf));

  return (
    <main className={dashboardPagePadding}>
      <Heading level={3} as="h1">
        {useTranslation("booking").t("sidebar.dashboard")}
      </Heading>
      <div className="space-y-8">
        <DashboardQuickActions today={today} />
        <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_20rem]">
          <UpcomingBookingsWidget
            requesterId={currentUser.id}
            token={token}
            timeZone={preferences.timeZone}
            asOf={asOf}
          />
          <AtAGlanceWidget requesterId={currentUser.id} token={token} timeZone={preferences.timeZone} today={today} />
        </div>
      </div>
    </main>
  );
}

export default function BookingDashboardPage() {
  return (
    <Suspense fallback={<DashboardSkeleton />}>
      <BookingDashboardContent />
    </Suspense>
  );
}
