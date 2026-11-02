import { Link, linkOptions } from "@tanstack/react-router";
import {
  CalendarIcon,
  CalendarPlusIcon,
  LayoutDashboardIcon,
  LibraryBigIcon,
  ListIcon,
  SettingsIcon,
  SlidersHorizontalIcon,
} from "lucide-react";
import { useTranslation } from "react-i18next";
import { todayInTimeZone, useBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import {
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
} from "@/modules/common/ui/sidebar";

const items = (today: string, isSysAdmin: boolean) => {
  return [
    {
      key: "dashboard",
      icon: LayoutDashboardIcon,
      link: (
        <Link {...linkOptions({ to: "/booking", search: {}, activeOptions: { exact: true, includeSearch: false } })} />
      ),
    },
    {
      key: "calendar",
      icon: CalendarIcon,
      link: <Link {...linkOptions({ to: "/booking/calendar", search: () => ({ date: today }) })} />,
    },
    {
      key: "allItems",
      icon: LibraryBigIcon,
      link: <Link {...linkOptions({ to: "/booking/all-items" })} />,
    },
    {
      key: "addBooking",
      icon: CalendarPlusIcon,
      link: <Link {...linkOptions({ to: "/booking/calendar/bookings/add", search: () => ({ date: today }) })} />,
    },
    {
      key: "myBookings",
      icon: ListIcon,
      link: <Link {...linkOptions({ to: "/booking/my-bookings", search: { period: "upcoming" } })} />,
    },
    {
      key: "preferences",
      icon: SlidersHorizontalIcon,
      link: <Link {...linkOptions({ to: "/booking/preferences" })} />,
    },
    ...(isSysAdmin
      ? [{ key: "settings", icon: SettingsIcon, link: <Link to="/booking/config/settings" /> } as const]
      : []),
  ] as const;
};

/** Content for the shared AppShell sidebar. The shell owns the surrounding layout. */
export function BookingSidebar() {
  const { t } = useTranslation("booking");
  const labels = {
    dashboard: t("sidebar.dashboard"),
    calendar: t("sidebar.calendar"),
    allItems: t("sidebar.allItems"),
    addBooking: t("sidebar.addBooking"),
    myBookings: t("sidebar.myBookings"),
    preferences: t("sidebar.preferences"),
    settings: t("sidebar.settings"),
  };
  const { data: currentUser } = useCurrentUserQuery();
  const preferences = useBookingDisplayPreferences();
  const sidebarItems = items(todayInTimeZone(preferences.timeZone), currentUser.hasSysAdminRole);

  return (
    <SidebarGroup>
      <SidebarGroupLabel>{t("sidebar.label")}</SidebarGroupLabel>
      <SidebarGroupContent>
        <SidebarMenu>
          {sidebarItems.map((item) => (
            <SidebarMenuItem key={item.key}>
              <SidebarMenuButton tooltip={labels[item.key]} render={item.link}>
                <item.icon />
                <span>{labels[item.key]}</span>
              </SidebarMenuButton>
            </SidebarMenuItem>
          ))}
        </SidebarMenu>
      </SidebarGroupContent>
    </SidebarGroup>
  );
}
