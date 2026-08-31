import { type AnyRoute, createRoute, Link, linkOptions, Outlet } from "@tanstack/react-router";
import {
  CalendarIcon,
  CalendarPlusIcon,
  CheckSquareIcon,
  ChevronRightIcon,
  LibraryBigIcon,
  ListIcon,
  SettingsIcon,
  SlidersHorizontalIcon,
} from "lucide-react";
import { useTranslation } from "react-i18next";
import { BookingCreationStoreProvider } from "@/modules/booking/creation/bookingCreationStore";
import { CompactBookingCreationDialog } from "@/modules/booking/creation/CompactBookingCreationDialog";
import { todayInTimeZone, useBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import i18n from "@/modules/common/i18n";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/modules/common/ui/collapsible";
import {
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarMenuSub,
  SidebarMenuSubButton,
  SidebarMenuSubItem,
} from "@/modules/common/ui/sidebar";

const items = (today: string) =>
  [
    {
      key: "calendar",
      icon: CalendarIcon,
      link: <Link {...linkOptions({ to: "/booking/calendar", search: () => ({ date: today }) })} />,
    },
    {
      key: "allItems",
      icon: LibraryBigIcon,
      link: <Link {...linkOptions({ to: "/booking/all-items", search: () => ({ date: today }) })} />,
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
    {
      key: "administration",
      icon: SettingsIcon,
      children: [
        { key: "settings", link: <Link {...linkOptions({ to: "/booking/config/settings" })} /> },
        { key: "bookableItems", link: <Link {...linkOptions({ to: "/booking/config/bookable-items" })} /> },
      ],
    },
    { key: "approvalQueue", icon: CheckSquareIcon },
  ] as const;

/** Content for the shared AppShell sidebar. The shell owns the surrounding layout. */
export function BookingSidebar() {
  const { t } = useTranslation("booking");
  const { data: currentUser } = useCurrentUserQuery();
  const preferences = useBookingDisplayPreferences();
  const sidebarItems = items(todayInTimeZone(preferences.timeZone));
  const labels = {
    calendar: t("sidebar.calendar"),
    allItems: t("sidebar.allItems"),
    addBooking: t("sidebar.addBooking"),
    myBookings: t("sidebar.myBookings"),
    preferences: t("sidebar.preferences"),
    administration: t("sidebar.administration"),
    settings: t("sidebar.settings"),
    bookableItems: t("sidebar.bookableItems"),
    approvalQueue: t("sidebar.approvalQueue"),
  };
  const visibleItems = currentUser.hasSysAdminRole
    ? sidebarItems
    : sidebarItems.filter((item) => item.key !== "administration");

  return (
    <SidebarGroup>
      <SidebarGroupLabel>{t("sidebar.label")}</SidebarGroupLabel>
      <SidebarGroupContent>
        <SidebarMenu>
          {visibleItems.map((item) =>
            "children" in item ? (
              // shadcn's Base UI sidebar-menu-collapsible example, with the Collapsible rendered as the
              // <li> so the menu stays a valid ul > li list, and Base UI's data-open in place of data-state.
              <Collapsible key={item.key} className="group/collapsible" defaultOpen render={<SidebarMenuItem />}>
                <CollapsibleTrigger render={<SidebarMenuButton tooltip={labels[item.key]} />}>
                  <item.icon />
                  <span>{labels[item.key]}</span>
                  <ChevronRightIcon className="ml-auto transition-transform group-data-open/collapsible:rotate-90" />
                </CollapsibleTrigger>
                <CollapsibleContent>
                  <SidebarMenuSub>
                    {item.children.map((child) => (
                      <SidebarMenuSubItem key={child.key}>
                        {/* an <a> without href has no role, so unrouted sub-items render as buttons */}
                        <SidebarMenuSubButton render={"link" in child ? child.link : <button type="button" />}>
                          <span>{labels[child.key]}</span>
                        </SidebarMenuSubButton>
                      </SidebarMenuSubItem>
                    ))}
                  </SidebarMenuSub>
                </CollapsibleContent>
              </Collapsible>
            ) : (
              <SidebarMenuItem key={item.key}>
                <SidebarMenuButton
                  tooltip={labels[item.key]}
                  render={"link" in item ? item.link : <button type="button" />}
                >
                  <item.icon />
                  <span>{labels[item.key]}</span>
                </SidebarMenuButton>
              </SidebarMenuItem>
            ),
          )}
        </SidebarMenu>
      </SidebarGroupContent>
    </SidebarGroup>
  );
}

export default function BookingPage() {
  return (
    <BookingCreationStoreProvider>
      <div className="mx-auto w-full max-w-7xl">
        <Outlet />
      </div>
      <CompactBookingCreationDialog />
    </BookingCreationStoreProvider>
  );
}

export function createBookingRoute<TParentRoute extends AnyRoute>(rootRoute: TParentRoute) {
  return createRoute({
    getParentRoute: () => rootRoute,
    path: "/booking",
    beforeLoad: () => ({
      appBar: { currentPage: "booking" },
      sidebar: () => <BookingSidebar />,
    }),
    head: () => ({
      // `common` loads eagerly at i18next init, so this synchronous lookup is safe
      // outside the component tree; other namespaces load lazily and would return the raw key here.
      meta: [
        {
          title: i18n.t("common:pageTitles.withProduct", {
            pageTitle: i18n.t("common:appBar.sections.booking.title"),
          }),
        },
      ],
    }),
    component: BookingPage,
  });
}
