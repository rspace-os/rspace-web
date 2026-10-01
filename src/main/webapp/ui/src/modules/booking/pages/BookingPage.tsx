import { type AnyRoute, createRoute, Outlet } from "@tanstack/react-router";
import { Suspense } from "react";
import { useTranslation } from "react-i18next";
import { BookingCreationStoreProvider } from "@/modules/booking/creation/bookingCreationStore";
import { CompactBookingCreationDialog } from "@/modules/booking/creation/CompactBookingCreationDialog";
import i18n from "@/modules/common/i18n";
import {
  SidebarGroup,
  SidebarGroupContent,
  SidebarMenu,
  SidebarMenuItem,
  SidebarMenuSkeleton,
} from "@/modules/common/ui/sidebar";
import { BookingBreadcrumbs } from "./BookingBreadcrumbs";
import { BookingSidebar } from "./BookingSidebar";
import BookingDashboardPage from "./dashboard/BookingDashboardPage";

export { BookingSidebar } from "./BookingSidebar";

/** Dashboard is the Booking landing page; Calendar remains a separate route. */
export function createBookingIndexRoute<TParentRoute extends AnyRoute>(bookingRoute: TParentRoute) {
  return createRoute({
    getParentRoute: () => bookingRoute,
    path: "/",
    component: BookingDashboardPage,
  });
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
