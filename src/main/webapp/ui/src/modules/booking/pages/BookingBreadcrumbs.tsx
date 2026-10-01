import { Link, useRouterState } from "@tanstack/react-router";
import { ChevronRightIcon } from "lucide-react";
import { Fragment, type ReactNode } from "react";
import { useTranslation } from "react-i18next";

const breadcrumbLinkClassName =
  "rounded-xs text-muted-foreground underline-offset-4 hover:text-foreground hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring";

type BreadcrumbItem = {
  key: string;
  content: ReactNode;
};

export function BookingBreadcrumbs() {
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
