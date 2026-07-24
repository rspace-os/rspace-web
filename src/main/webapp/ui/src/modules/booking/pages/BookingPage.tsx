import { type AnyRoute, createRoute } from "@tanstack/react-router";
import { useTranslation } from "react-i18next";
import i18n from "@/modules/common/i18n";

// Intentional test aid: this keeps TanStack Router's pending transition UI visible
// while the booking-page prototype has no real loader work of its own.
const artificialBookingPageLoadMs = 1500;

function wait(ms: number): Promise<void> {
  return new Promise((resolve) => {
    globalThis.setTimeout(resolve, ms);
  });
}

export default function BookingPage() {
  const { t } = useTranslation("common");
  return (
    <div className="flex h-screen items-center justify-center text-foreground">
      <h1 className="text-xl font-semibold">{t("appBar.sections.booking.title")}</h1>
    </div>
  );
}

export function createBookingRoute<TParentRoute extends AnyRoute>(rootRoute: TParentRoute) {
  return createRoute({
    getParentRoute: () => rootRoute,
    path: "/booking",
    beforeLoad: () => ({ appBar: { currentPage: "booking" } }),
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
    loader: () => wait(artificialBookingPageLoadMs),
    component: BookingPage,
  });
}
