import { Link, linkOptions } from "@tanstack/react-router";
import { CalendarDaysIcon, ClipboardListIcon, MicroscopeIcon } from "lucide-react";
import { useTranslation } from "react-i18next";
import { Card, CardContent } from "@/modules/common/ui/card";

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
