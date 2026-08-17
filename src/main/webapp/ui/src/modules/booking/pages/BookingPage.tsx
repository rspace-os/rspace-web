import { type AnyRoute, createRoute, Link, Outlet } from "@tanstack/react-router";
import {
  CalendarIcon,
  CheckSquareIcon,
  ChevronRightIcon,
  LayoutDashboardIcon,
  ListIcon,
  type LucideIcon,
  SettingsIcon,
} from "lucide-react";
import { useTranslation } from "react-i18next";
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

const items = [
  { key: "dashboard", icon: LayoutDashboardIcon },
  { key: "calendar", icon: CalendarIcon },
  { key: "myBookings", icon: ListIcon },
  {
    key: "administration",
    icon: SettingsIcon,
    children: [{ key: "settings" }, { key: "bookableItems", to: "/booking/config/bookable-items" }],
  },
  { key: "approvalQueue", icon: CheckSquareIcon },
] as const satisfies ReadonlyArray<{
  key: string;
  icon: LucideIcon;
  children?: ReadonlyArray<{ key: string; to?: string }>;
}>;

/** Content for the shared AppShell sidebar. The shell owns the surrounding layout. */
export function BookingSidebar() {
  const { t } = useTranslation("booking");
  const { data: currentUser } = useCurrentUserQuery();
  const labels = {
    dashboard: t("sidebar.dashboard"),
    calendar: t("sidebar.calendar"),
    myBookings: t("sidebar.myBookings"),
    administration: t("sidebar.administration"),
    settings: t("sidebar.settings"),
    bookableItems: t("sidebar.bookableItems"),
    approvalQueue: t("sidebar.approvalQueue"),
  };
  const visibleItems = currentUser.hasSysAdminRole ? items : items.filter((item) => item.key !== "administration");

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
                        <SidebarMenuSubButton
                          render={"to" in child ? <Link to={child.to} /> : <button type="button" />}
                        >
                          <span>{labels[child.key]}</span>
                        </SidebarMenuSubButton>
                      </SidebarMenuSubItem>
                    ))}
                  </SidebarMenuSub>
                </CollapsibleContent>
              </Collapsible>
            ) : (
              <SidebarMenuItem key={item.key}>
                {/* ponytail: no sub-routes exist yet; wire `render={<Link .../>}` when they do */}
                <SidebarMenuButton tooltip={labels[item.key]}>
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
    <div className="mx-auto w-full max-w-7xl">
      <Outlet />
    </div>
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
