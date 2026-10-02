import { Link } from "@tanstack/react-router";
import { CalendarClockIcon, ChevronRightIcon, WrenchIcon } from "lucide-react";
import { cloneElement } from "react";
import { useTranslation } from "react-i18next";
import {
  BookingInstrumentTimeTooltip,
  type BookingInstrumentTimeTooltipProps,
} from "@/modules/booking/components/BookingInstrumentTimeTooltip";
import { buttonVariants } from "@/modules/common/ui/button";
import { InventoryItem, InventoryLocationLink } from "@/modules/common/ui/inventory-item";
import { UserBadge } from "@/modules/common/ui/user-badge";
import { cn } from "@/modules/common/utils/cn";

export type BookingSummaryItem = {
  name: string;
  globalId: string;
  location?: { name: string; globalId: string };
};

export type BookingSummaryAccordionProps = {
  accordionName: string;
  heading: string;
  summaryLabel?: string;
  period: string;
  periodTooltip?: Omit<BookingInstrumentTimeTooltipProps, "children" | "trigger">;
  purpose: string | null;
  maintenance?: boolean;
  item?: BookingSummaryItem;
  actor?: string | null;
  detailsBookingId?: number;
};

export function BookingSummaryAccordion({
  accordionName,
  heading,
  summaryLabel,
  period,
  periodTooltip,
  purpose,
  maintenance = false,
  item,
  actor,
  detailsBookingId,
}: BookingSummaryAccordionProps) {
  const { t } = useTranslation("booking");
  const summaryTrigger = (
    <summary
      aria-label={t("dayTimeline.event.showDetails", { title: heading, period })}
      className="flex min-w-0 cursor-pointer list-none items-center gap-2 px-2 py-1.5 outline-none focus-visible:ring-3 focus-visible:ring-inset focus-visible:ring-ring/50 [&::-webkit-details-marker]:hidden"
    />
  );
  const summaryContent = (
    <>
      <span
        className={cn(
          "flex size-7 shrink-0 items-center justify-center rounded-sm",
          maintenance
            ? "bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-200"
            : "bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-200",
        )}
      >
        {maintenance ? (
          <WrenchIcon className="size-4" aria-hidden="true" />
        ) : (
          <CalendarClockIcon className="size-4" aria-hidden="true" />
        )}
      </span>
      <span className="min-w-0 flex-1">
        <span className="block truncate text-xs font-medium">{summaryLabel ?? heading}</span>
        <span className="block truncate text-[11px] text-muted-foreground">{period}</span>
      </span>
      <ChevronRightIcon
        className="size-4 shrink-0 text-muted-foreground transition-transform group-open:rotate-90"
        aria-hidden="true"
      />
    </>
  );
  const summary = periodTooltip ? (
    <BookingInstrumentTimeTooltip {...periodTooltip} trigger={summaryTrigger}>
      {summaryContent}
    </BookingInstrumentTimeTooltip>
  ) : (
    cloneElement(summaryTrigger, undefined, summaryContent)
  );

  return (
    <li className="min-w-0 overflow-hidden rounded-sm border bg-background">
      <details name={accordionName} className="group">
        {summary}
        <div className="border-border border-t">
          <dl className="divide-y divide-border px-2 text-sm">
            {item ? (
              <div className="py-2">
                <dt className="sr-only">{t("dayTimeline.expanded.item")}</dt>
                <dd>
                  <InventoryItem
                    name={item.name}
                    globalId={item.globalId}
                    href={`/globalId/${item.globalId}`}
                    idLinkLabel={t("dayTimeline.expanded.openItem", { globalId: item.globalId })}
                    idPlacement="title"
                    className="p-0"
                  >
                    {item.location ? (
                      <InventoryLocationLink name={item.location.name} globalId={item.location.globalId} />
                    ) : null}
                  </InventoryItem>
                </dd>
              </div>
            ) : null}
            {actor ? (
              <div className="grid grid-cols-[4.5rem_1fr] gap-2 py-2">
                <dt className="text-xs text-muted-foreground">
                  {t(maintenance ? "dayTimeline.expanded.createdBy" : "dayTimeline.expanded.bookedBy")}
                </dt>
                <dd className="min-w-0">
                  <UserBadge name={actor} />
                </dd>
              </div>
            ) : null}
            <div className="grid grid-cols-[4.5rem_1fr] gap-2 py-2">
              <dt className="text-xs text-muted-foreground">
                {t(maintenance ? "dayTimeline.expanded.notes" : "dayTimeline.expanded.purpose")}
              </dt>
              <dd className="text-xs leading-4">{purpose ?? t("bookings.details.noneProvided")}</dd>
            </div>
          </dl>
          {detailsBookingId !== undefined ? (
            <Link
              to="/booking/calendar/bookings/$id"
              params={{ id: String(detailsBookingId) }}
              className={cn(
                buttonVariants({ variant: "link", size: "xs" }),
                "h-auto w-full rounded-none border-t-border py-2",
              )}
            >
              {t("availabilityBar.slice.details")}
            </Link>
          ) : null}
        </div>
      </details>
    </li>
  );
}
