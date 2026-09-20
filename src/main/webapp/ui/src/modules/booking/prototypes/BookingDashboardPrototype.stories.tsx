// PROTOTYPE ONLY. Booking dashboard concept for review in Storybook.
/* biome-ignore-all lint/style/noJsxLiterals: throwaway prototype copy is intentionally not entering the translation catalog. */
import type { Meta, StoryObj } from "@storybook/tanstack-react";
import {
  CalendarClockIcon,
  CalendarDaysIcon,
  ChevronLeftIcon,
  ChevronRightIcon,
  ClipboardListIcon,
  MicroscopeIcon,
} from "lucide-react";
import * as React from "react";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { TableList } from "@/modules/common/table-list/TableList";
import { Badge } from "@/modules/common/ui/badge";
import { Button } from "@/modules/common/ui/button";
import { Calendar, CalendarDayButton } from "@/modules/common/ui/calendar";
import { Card, CardContent } from "@/modules/common/ui/card";
import { Popover, PopoverContent, PopoverDescription, PopoverTitle, PopoverTrigger } from "@/modules/common/ui/popover";
import { cn } from "@/modules/common/utils/cn";

type DashboardBooking = {
  id: number;
  target: string;
  start: string;
  end: string;
  purpose: string;
};

const upcomingBookings: readonly DashboardBooking[] = [
  {
    id: 41,
    target: "Confocal microscope",
    start: "Today, 14:00",
    end: "Today, 16:00",
    purpose: "Live-cell imaging",
  },
  {
    id: 44,
    target: "Flow cytometer",
    start: "Tomorrow, 09:30",
    end: "Tomorrow, 10:30",
    purpose: "Panel validation",
  },
  {
    id: 48,
    target: "Mass spectrometer",
    start: "4 Sep, 13:00",
    end: "4 Sep, 15:30",
    purpose: "Metabolomics batch 07",
  },
  {
    id: 52,
    target: "Cell culture room 2",
    start: "8 Sep, 08:00",
    end: "8 Sep, 12:00",
    purpose: "Organoid culture",
  },
];

const upcomingBookingsConfig = resolveCollectionConfig<DashboardBooking>({
  slug: "dashboard-upcoming-bookings",
  idField: "id",
  useAsTitle: "target",
  labels: {
    singularKey: "booking:myBookings.singular",
    pluralKey: "booking:myBookings.plural",
  },
  defaultColumns: ["target", "start", "end", "purpose"],
  fields: [
    { name: "id", type: "number", labelKey: "booking:myBookings.fields.id", list: false },
    { name: "target", type: "text", labelKey: "booking:myBookings.fields.target" },
    { name: "start", type: "text", labelKey: "booking:myBookings.fields.start" },
    { name: "end", type: "text", labelKey: "booking:myBookings.fields.end" },
    { name: "purpose", type: "text", labelKey: "booking:myBookings.fields.purpose" },
  ],
});

const tableFeatures = {
  filtering: false,
  sorting: false,
  pagination: false,
  columns: false,
} as const;

const quickActions = [
  {
    label: "Calendar",
    description: "See bookings by day",
    href: "/booking/calendar",
    icon: CalendarDaysIcon,
  },
  {
    label: "Find an instrument",
    description: "Search bookable items",
    href: "/booking/all-items",
    icon: MicroscopeIcon,
  },
  {
    label: "My Bookings",
    description: "Review your reservations",
    href: "/booking/my-bookings?period=upcoming",
    icon: ClipboardListIcon,
  },
] as const;

function QuickActionCard({ action, className }: { action: (typeof quickActions)[number]; className?: string }) {
  const Icon = action.icon;
  return (
    <a
      href={action.href}
      className={cn(
        "group block min-w-0 rounded-sm outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2",
        className,
      )}
    >
      <Card className="relative h-24 gap-0 overflow-hidden border-border/80 bg-card py-0 transition-colors group-hover:border-primary/45 group-hover:bg-accent/45">
        <CardContent className="relative z-10 flex h-full flex-col justify-start gap-0.5 px-4 py-2">
          <span className="max-w-[9rem] text-base font-semibold leading-tight text-foreground">{action.label}</span>
          <span className="max-w-[9rem] text-xs text-muted-foreground">{action.description}</span>
        </CardContent>
        <Icon
          className="pointer-events-none absolute -bottom-3 -right-3 size-16 stroke-[1.25] text-primary/20 transition-transform group-hover:-translate-x-1 group-hover:-translate-y-1"
          aria-hidden="true"
        />
      </Card>
    </a>
  );
}

function QuickActions({ layout }: { layout: "row" | "rail" }) {
  return (
    <section aria-labelledby={`quick-actions-${layout}`} className="min-w-0 space-y-3">
      <h2 id={`quick-actions-${layout}`} className="text-xl font-semibold tracking-tight">
        Quick Actions
      </h2>
      <div
        className={cn(
          "grid gap-3",
          layout === "row" && "sm:grid-cols-3",
          layout === "rail" && "grid-cols-1 sm:grid-cols-3 xl:grid-cols-1",
        )}
      >
        {quickActions.map((action) => (
          <QuickActionCard key={action.label} action={action} />
        ))}
      </div>
    </section>
  );
}

function UpcomingBookings({ className }: { className?: string }) {
  return (
    <section aria-labelledby="upcoming-bookings" className={cn("min-w-0 space-y-3", className)}>
      <div className="flex items-baseline justify-between gap-4">
        <h2 id="upcoming-bookings" className="text-xl font-semibold tracking-tight">
          Upcoming Bookings
        </h2>
        <a
          className="shrink-0 text-sm font-medium text-primary underline-offset-4 hover:underline"
          href="/booking/my-bookings?period=upcoming"
        >
          View all
        </a>
      </div>
      <Card className="overflow-hidden">
        <CardContent className="p-0">
          <TableList
            config={upcomingBookingsConfig}
            rows={upcomingBookings}
            getRowId={(row) => String(row.id)}
            features={tableFeatures}
            clientSide
            hideHeader
            queryString={false}
            reserveEmptyRows={false}
            variant="transparent"
          />
        </CardContent>
      </Card>
    </section>
  );
}

// Prototype fixtures represent the signed-in user's bookings, including past and overnight reservations.
const prototypeToday = new Date();
const regularCalendarBookings = [-1, 0, 1].flatMap((monthOffset) =>
  [3, 7, 7, 14, 20, 25, 28].map((date, index) => ({
    ...upcomingBookings[index % upcomingBookings.length],
    id: `${monthOffset}-${index}`,
    startsAt: new Date(
      prototypeToday.getFullYear(),
      prototypeToday.getMonth() + monthOffset,
      date,
      index === 2 ? 14 : 9,
    ),
    endsAt: new Date(
      prototypeToday.getFullYear(),
      prototypeToday.getMonth() + monthOffset,
      date + (index === 5 ? 1 : 0),
      index === 2 ? 16 : 11,
    ),
  })),
);
const stressCalendarBookings = [
  { date: 7, count: 10 },
  { date: 14, count: 5 },
  { date: 21, count: 99 },
  { date: 22, count: 100 },
].flatMap(({ date, count }) =>
  Array.from({ length: count }, (_, index) => {
    const booking = upcomingBookings[index % upcomingBookings.length];
    const hour = 7 + (Math.floor(index / 2) % 12);
    const minute = (index % 2) * 30;
    return {
      ...booking,
      id: `stress-${date}-${index}`,
      target: `${booking.target} ${index + 1}`,
      startsAt: new Date(prototypeToday.getFullYear(), prototypeToday.getMonth(), date, hour, minute),
      endsAt: new Date(prototypeToday.getFullYear(), prototypeToday.getMonth(), date, hour, minute + 45),
    };
  }),
);
const calendarBookings = [...regularCalendarBookings, ...stressCalendarBookings];
const bookingsPerPage = 5;

function BookingsCalendarDay(props: React.ComponentProps<typeof CalendarDayButton>) {
  const { day, children, modifiers, locale: _locale, className, ...buttonProps } = props;
  const buttonRef = React.useRef<HTMLButtonElement>(null);
  React.useEffect(() => {
    if (modifiers.focused) buttonRef.current?.focus();
  }, [modifiers.focused]);
  const nextDay = new Date(day.date.getFullYear(), day.date.getMonth(), day.date.getDate() + 1);
  const bookings = calendarBookings.filter((booking) => booking.startsAt < nextDay && booking.endsAt > day.date);
  const [requestedPage, setRequestedPage] = React.useState(0);
  const pageCount = Math.ceil(bookings.length / bookingsPerPage);
  const page = Math.min(requestedPage, Math.max(pageCount - 1, 0));
  const firstBooking = page * bookingsPerPage;
  const visibleBookings = bookings.slice(firstBooking, firstBooking + bookingsPerPage);
  const accordionName = React.useId();
  const dateLabel = day.date.toLocaleDateString("en-GB", {
    weekday: "long",
    day: "numeric",
    month: "long",
    year: "numeric",
  });
  const formatTime = (date: Date) =>
    date.toLocaleString("en-GB", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" });

  if (!bookings.length) {
    return <CalendarDayButton {...props} className={cn(className, modifiers.today && "max-md:bg-muted")} />;
  }

  return (
    <Popover>
      <PopoverTrigger
        openOnHover
        delay={0}
        {...buttonProps}
        ref={buttonRef}
        render={<Button variant="ghost" />}
        aria-label={`${dateLabel}: ${bookings.length} ${bookings.length === 1 ? "booking" : "bookings"}`}
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
        className="max-h-[min(32rem,var(--available-height))] w-80 gap-3 overflow-y-auto rounded-sm p-3 duration-0 data-closed:animate-none data-open:animate-none"
      >
        <div>
          <PopoverTitle className="text-base font-semibold leading-tight">{dateLabel}</PopoverTitle>
          <PopoverDescription className="mt-1 text-xs">
            {bookings.length} {bookings.length === 1 ? "booking" : "bookings"}
          </PopoverDescription>
        </div>
        <ul className={cn("space-y-1", pageCount > 1 && "min-h-[15.5rem]")}>
          {visibleBookings.map((booking) => (
            <li key={booking.id} className="min-w-0 overflow-hidden rounded-sm border bg-background">
              <details name={accordionName} className="group">
                <summary className="flex min-w-0 cursor-pointer list-none items-center gap-2 px-2 py-1.5 outline-none focus-visible:ring-3 focus-visible:ring-inset focus-visible:ring-ring/50 [&::-webkit-details-marker]:hidden">
                  <span className="flex size-7 shrink-0 items-center justify-center rounded-sm bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-200">
                    <CalendarClockIcon className="size-4" aria-hidden="true" />
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="block text-xs font-medium">{booking.target}</span>
                    <span className="block text-[11px] text-muted-foreground">
                      {formatTime(booking.startsAt)} – {formatTime(booking.endsAt)}
                    </span>
                  </span>
                  <ChevronRightIcon
                    className="size-4 shrink-0 text-muted-foreground transition-transform group-open:rotate-90"
                    aria-hidden="true"
                  />
                </summary>
                <dl className="border-t px-2 text-xs">
                  <div className="grid grid-cols-[4.5rem_1fr] gap-2 py-2">
                    <dt className="text-muted-foreground">Purpose</dt>
                    <dd>{booking.purpose}</dd>
                  </div>
                </dl>
              </details>
            </li>
          ))}
        </ul>
        {pageCount > 1 && (
          <div className="flex items-center justify-between border-t pt-2 text-xs text-muted-foreground">
            <span>
              {firstBooking + 1}–{Math.min(firstBooking + bookingsPerPage, bookings.length)} of {bookings.length}
            </span>
            <div className="flex gap-1">
              <Button
                aria-label="Previous bookings"
                size="icon-xs"
                variant="ghost"
                disabled={page === 0}
                onClick={() => setRequestedPage(page - 1)}
              >
                <ChevronLeftIcon aria-hidden="true" />
              </Button>
              <Button
                aria-label="Next bookings"
                size="icon-xs"
                variant="ghost"
                disabled={page + 1 === pageCount}
                onClick={() => setRequestedPage(page + 1)}
              >
                <ChevronRightIcon aria-hidden="true" />
              </Button>
            </div>
          </div>
        )}
      </PopoverContent>
    </Popover>
  );
}

function AtAGlance() {
  const [month, setMonth] = React.useState(prototypeToday);
  return (
    <section aria-labelledby="at-a-glance" className="min-w-0 space-y-3">
      <h2 id="at-a-glance" className="text-xl font-semibold tracking-tight">
        At a glance
      </h2>
      <Card className="gap-0 py-0">
        <CardContent className="p-0">
          <Calendar
            mode="single"
            month={month}
            onMonthChange={setMonth}
            weekStartsOn={1}
            fixedWeeks
            className="w-full max-md:[&_.rdp-today]:bg-transparent max-md:[&_[role=gridcell]]:flex max-md:[&_[role=gridcell]]:h-8 max-md:[&_[role=gridcell]]:aspect-auto max-md:[&_[role=gridcell]]:items-center max-md:[&_[role=gridcell]]:justify-center max-md:[&_[role=gridcell]]:rounded-sm max-md:[&_button[data-day]]:h-full max-md:[&_button[data-day]]:w-full max-md:[&_button[data-day]]:aspect-auto max-md:[&_button[data-day]]:rounded-sm max-md:[&_button[data-day]]:py-0"
            components={{ DayButton: BookingsCalendarDay }}
          />
        </CardContent>
      </Card>
    </section>
  );
}

function BookingsOverview() {
  return (
    <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_20rem]">
      <UpcomingBookings />
      <AtAGlance />
    </div>
  );
}

function BalancedLayout() {
  return (
    <div className="space-y-8">
      <QuickActions layout="row" />
      <BookingsOverview />
    </div>
  );
}

function NewBookingDashboardPrototype() {
  return (
    <I18nRoot namespaces={["booking", "common"]}>
      <main className="min-h-screen bg-background px-4 py-8 text-foreground sm:px-8">
        <div className="mx-auto max-w-7xl space-y-7">
          <header>
            <h1 className="text-3xl font-semibold tracking-tight">Booking dashboard</h1>
          </header>
          <BalancedLayout />
        </div>
      </main>
    </I18nRoot>
  );
}

const meta = {
  title: "Booking/Prototypes/Booking Dashboard",
  parameters: { layout: "fullscreen" },
} satisfies Meta;

export default meta;
type Story = StoryObj<typeof meta>;

export const NewPrototype: Story = { render: () => <NewBookingDashboardPrototype /> };
