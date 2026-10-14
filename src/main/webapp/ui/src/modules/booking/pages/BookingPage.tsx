import { type AnyRoute, createRoute, Link, linkOptions, Outlet, useRouterState } from "@tanstack/react-router";
import {
  CalendarIcon,
  CalendarPlusIcon,
  CheckSquareIcon,
  ChevronRightIcon,
  LayoutDashboardIcon,
  LibraryBigIcon,
  ListIcon,
  SettingsIcon,
  SlidersHorizontalIcon,
} from "lucide-react";
import { Fragment, type ReactNode, Suspense } from "react";
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
  SidebarMenuSkeleton,
  SidebarMenuSub,
  SidebarMenuSubButton,
  SidebarMenuSubItem,
} from "@/modules/common/ui/sidebar";
import { Heading } from "@/modules/common/ui/typography";

const breadcrumbLinkClassName =
  "rounded-xs text-muted-foreground underline-offset-4 hover:text-foreground hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring";

type BreadcrumbItem = {
  key: string;
  content: ReactNode;
};

function BookingBreadcrumbs() {
  const { t } = useTranslation("booking");
  const pathname = useRouterState({ select: (state) => state.location.pathname.replace(/\/+$/, "") });
  if (pathname === "/booking") return null;

  const current = (key: string, label: string): BreadcrumbItem => ({
    key,
    content: (
      <span aria-current="page" className="font-medium text-foreground">
        {label}
      </span>
    ),
  });
  const calendar: BreadcrumbItem = {
    key: "calendar",
    content: (
      <Link to="/booking/calendar" search={{}} className={breadcrumbLinkClassName}>
        {t("calendar.title")}
      </Link>
    ),
  };
  const allItems: BreadcrumbItem = {
    key: "all-items",
    content: (
      <Link to="/booking/all-items" search={{}} className={breadcrumbLinkClassName}>
        {t("allBookableItems.title")}
      </Link>
    ),
  };
  const administration: BreadcrumbItem = {
    key: "administration",
    content: <span className="text-muted-foreground">{t("sidebar.administration")}</span>,
  };
  const bookableItems: BreadcrumbItem = {
    key: "bookable-items",
    content: (
      <Link to="/booking/config/bookable-items" className={breadcrumbLinkClassName}>
        {t("sidebar.bookableItems")}
      </Link>
    ),
  };

  let pageItems: BreadcrumbItem[];
  if (pathname === "/booking/calendar") pageItems = [current("calendar", t("calendar.title"))];
  else if (pathname === "/booking/all-items") pageItems = [current("all-items", t("allBookableItems.title"))];
  else if (pathname === "/booking/calendar/bookings/add") {
    pageItems = [calendar, current("add-booking", t("bookings.addTitle"))];
  } else if (pathname === "/booking/my-bookings") pageItems = [current("my-bookings", t("myBookings.title"))];
  else if (pathname === "/booking/preferences") pageItems = [current("preferences", t("preferences.title"))];
  else if (pathname === "/booking/config/settings") {
    pageItems = [administration, current("settings", t("settings.title"))];
  } else if (pathname === "/booking/config/bookable-items") {
    pageItems = [administration, current("bookable-items", t("sidebar.bookableItems"))];
  } else if (pathname === "/booking/bookable-items/add") {
    pageItems = [administration, bookableItems, current("add-bookable-item", t("bookableItems.addTitle"))];
  } else {
    const eventMatch = pathname.match(/^\/booking\/calendar\/bookings\/([^/]+)(\/edit)?$/);
    const itemMatch = pathname.match(/^\/booking\/bookable-items\/[^/]+(?:\/[^/]+)?$/);
    if (eventMatch) {
      pageItems = [calendar];
      if (eventMatch[2]) {
        pageItems.push({
          key: "booking-details",
          content: (
            <Link
              to="/booking/calendar/bookings/$id"
              params={{ id: eventMatch[1] }}
              className={breadcrumbLinkClassName}
            >
              {t("bookings.details.title")}
            </Link>
          ),
        });
        pageItems.push(current("edit-booking", t("bookings.editTitle")));
      } else pageItems.push(current("booking-details", t("bookings.details.title")));
    } else if (itemMatch) {
      pageItems = [allItems, current("bookable-item-details", t("bookableItemDetails.title"))];
    } else return null;
  }

  const items: BreadcrumbItem[] = [
    {
      key: "booking",
      content: (
        <Link to="/booking" search={{}} className={breadcrumbLinkClassName}>
          {t("sidebar.label")}
        </Link>
      ),
    },
    ...pageItems,
  ];

  return (
    <nav aria-label={t("breadcrumbs.label")} className="-mb-2 px-4 pt-4 text-sm sm:-mb-4 sm:px-8 sm:pt-8">
      <ol className="flex flex-wrap items-center gap-1.5">
        {items.map((item, index) => (
          <Fragment key={item.key}>
            <li>{item.content}</li>
            {index < items.length - 1 ? (
              <li aria-hidden="true">
                <ChevronRightIcon className="size-3.5 text-muted-foreground" />
              </li>
            ) : null}
          </Fragment>
        ))}
      </ol>
    </nav>
  );
}

const items = (today: string) =>
  [
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
    dashboard: t("sidebar.dashboard"),
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

/** Dashboard is the Booking landing page; Calendar remains a separate route. */
export function createBookingIndexRoute<TParentRoute extends AnyRoute>(bookingRoute: TParentRoute) {
  return createRoute({
    getParentRoute: () => bookingRoute,
    path: "/",
    component: BookingDashboard,
  });
}

function BookingDashboard() {
  const { t } = useTranslation("booking");
  return (
    <main className="space-y-6 p-4 sm:p-8">
      <Heading level={3} as="h1">
        {t("sidebar.dashboard")}
      </Heading>
    </main>
  );
}

export default function BookingPage() {
  return (
    <BookingCreationStoreProvider>
      <div className="mx-auto w-full max-w-7xl">
        <BookingBreadcrumbs />
        <Outlet />
      </div>
      <Suspense fallback={null}>
        <CompactBookingCreationDialog />
      </Suspense>
    </BookingCreationStoreProvider>
  );
}

function BookingSidebarSkeleton() {
  const { t } = useTranslation("common");
  return (
    <SidebarGroup aria-busy="true">
      <p role="status" className="sr-only">
        {t("loading")}
      </p>
      <SidebarGroupContent aria-hidden="true">
        <SidebarMenu>
          {[0, 1, 2, 3, 4, 5].map((row) => (
            <SidebarMenuItem key={row}>
              <SidebarMenuSkeleton showIcon />
            </SidebarMenuItem>
          ))}
        </SidebarMenu>
      </SidebarGroupContent>
    </SidebarGroup>
  );
}

export function createBookingRoute<TParentRoute extends AnyRoute>(rootRoute: TParentRoute) {
  return createRoute({
    getParentRoute: () => rootRoute,
    path: "/booking",
    beforeLoad: () => ({
      appBar: { currentPage: "booking" },
      sidebar: () => (
        <Suspense fallback={<BookingSidebarSkeleton />}>
          <BookingSidebar />
        </Suspense>
      ),
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
