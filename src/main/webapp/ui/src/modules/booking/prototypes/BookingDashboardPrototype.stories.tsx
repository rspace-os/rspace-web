// PROTOTYPE ONLY. Three booking dashboard layouts, switchable with ?variant=, in Storybook.
/* biome-ignore-all lint/style/noJsxLiterals: throwaway prototype copy is intentionally not entering the translation catalog. */
import type { Meta, StoryObj } from "@storybook/tanstack-react";
import { ArrowLeftIcon, ArrowRightIcon, CalendarDaysIcon, ClipboardListIcon, MicroscopeIcon } from "lucide-react";
import * as React from "react";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { TableList } from "@/modules/common/table-list/TableList";
import { Card, CardContent } from "@/modules/common/ui/card";
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
        <CardContent className="relative z-10 flex h-full flex-col justify-center gap-0.5 px-4 py-2">
          <span className="max-w-[9rem] text-base font-semibold leading-tight text-foreground">{action.label}</span>
          <span className="max-w-[9rem] text-xs text-muted-foreground">{action.description}</span>
        </CardContent>
        <Icon
          className="pointer-events-none absolute -bottom-3 -right-3 size-16 stroke-[1.25] text-primary/12 transition-transform group-hover:-translate-x-1 group-hover:-translate-y-1"
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

function BalancedLayout() {
  return (
    <div className="space-y-8">
      <QuickActions layout="row" />
      <UpcomingBookings />
    </div>
  );
}

function ActionStripLayout() {
  return (
    <div className="space-y-8">
      <div className="rounded-sm border bg-muted/35 p-4 sm:p-6">
        <QuickActions layout="row" />
      </div>
      <UpcomingBookings />
    </div>
  );
}

function BookingFirstLayout() {
  return (
    <div className="grid items-start gap-6 xl:grid-cols-[minmax(40rem,1fr)_17rem]">
      <UpcomingBookings className="xl:col-start-1 xl:row-start-1" />
      <div className="xl:col-start-2 xl:row-start-1">
        <QuickActions layout="rail" />
      </div>
    </div>
  );
}

const variants = [
  { key: "balanced", label: "Balanced top row", component: BalancedLayout },
  { key: "action-strip", label: "Action strip", component: ActionStripLayout },
  { key: "booking-first", label: "Booking first", component: BookingFirstLayout },
] as const;

type VariantKey = (typeof variants)[number]["key"];

function isVariantKey(value: string | null): value is VariantKey {
  return variants.some(({ key }) => key === value);
}

function initialVariant(): VariantKey {
  if (typeof window === "undefined") return "balanced";
  const requested = new URL(window.location.href).searchParams.get("variant");
  return isVariantKey(requested) ? requested : "balanced";
}

function PrototypeSwitcher({ current, onChange }: { current: VariantKey; onChange: (variant: VariantKey) => void }) {
  const currentIndex = variants.findIndex(({ key }) => key === current);
  const selectOffset = React.useCallback(
    (offset: number) => {
      const nextIndex = (currentIndex + offset + variants.length) % variants.length;
      onChange(variants[nextIndex].key);
    },
    [currentIndex, onChange],
  );

  React.useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      const target = event.target;
      if (
        target instanceof HTMLInputElement ||
        target instanceof HTMLTextAreaElement ||
        (target instanceof HTMLElement && target.isContentEditable)
      ) {
        return;
      }
      if (event.key === "ArrowLeft") selectOffset(-1);
      if (event.key === "ArrowRight") selectOffset(1);
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [selectOffset]);

  return (
    <nav
      aria-label="Prototype variants"
      className="fixed bottom-5 left-1/2 z-50 flex -translate-x-1/2 items-center gap-2 rounded-full bg-foreground px-2 py-1.5 text-background shadow-xl"
    >
      <button
        type="button"
        className="grid size-8 place-items-center rounded-full hover:bg-background/15 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-background"
        aria-label="Previous dashboard variant"
        onClick={() => selectOffset(-1)}
      >
        <ArrowLeftIcon className="size-4" aria-hidden="true" />
      </button>
      <output className="min-w-40 text-center text-xs font-medium" aria-live="polite">
        {currentIndex + 1} of {variants.length}: {variants[currentIndex].label}
      </output>
      <button
        type="button"
        className="grid size-8 place-items-center rounded-full hover:bg-background/15 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-background"
        aria-label="Next dashboard variant"
        onClick={() => selectOffset(1)}
      >
        <ArrowRightIcon className="size-4" aria-hidden="true" />
      </button>
    </nav>
  );
}

function BookingDashboardPrototype() {
  const [variant, setVariant] = React.useState<VariantKey>(initialVariant);
  const selected = variants.find(({ key }) => key === variant) ?? variants[0];
  const Layout = selected.component;

  const selectVariant = React.useCallback((next: VariantKey) => {
    setVariant(next);
    const url = new URL(window.location.href);
    url.searchParams.set("variant", next);
    window.history.replaceState(window.history.state, "", url);
  }, []);

  return (
    <I18nRoot namespaces={["booking", "common"]}>
      <main className="min-h-screen bg-background px-4 py-8 pb-24 text-foreground sm:px-8">
        <div className="mx-auto max-w-7xl space-y-7">
          <header>
            <h1 className="text-3xl font-semibold tracking-tight">Booking dashboard</h1>
          </header>
          <Layout />
        </div>
      </main>
      <PrototypeSwitcher current={variant} onChange={selectVariant} />
    </I18nRoot>
  );
}

const meta = {
  title: "Booking/Prototypes/Booking Dashboard",
  component: BookingDashboardPrototype,
  parameters: { layout: "fullscreen" },
} satisfies Meta<typeof BookingDashboardPrototype>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Dashboard: Story = {};
