import { createFileRoute } from "@tanstack/react-router";
import { Suspense } from "react";
import BookingPage from "@/modules/booking/pages/BookingPage";
import { BookingSidebar } from "@/modules/booking/pages/BookingSidebar";
import { BookingSidebarSkeleton } from "@/modules/booking/pages/BookingSidebarSkeleton";
import type { AppBarRouteContext } from "@/modules/common/app/AppShell";
import i18n from "@/modules/common/i18n";

export const Route = createFileRoute("/booking")({
  beforeLoad: () =>
    ({
      appBar: { currentPage: "booking" },
      sidebar: () => (
        <Suspense fallback={<BookingSidebarSkeleton />}>
          <BookingSidebar />
        </Suspense>
      ),
    }) satisfies AppBarRouteContext,
  head: () => ({
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
