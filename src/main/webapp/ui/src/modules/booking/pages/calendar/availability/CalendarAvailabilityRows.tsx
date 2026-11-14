import { Link } from "@tanstack/react-router";
import { CalendarPlusIcon, EyeIcon } from "lucide-react";
import { useTranslation } from "react-i18next";
import { AvailabilityBar } from "@/modules/booking/components/AvailabilityBar";
import type { SourcedAvailabilityInterval } from "@/modules/booking/domain/availability";
import { TableList } from "@/modules/common/table-list/TableList";
import { buttonVariants } from "@/modules/common/ui/button";
import { Skeleton } from "@/modules/common/ui/skeleton";
import { TooltipIconLink } from "@/modules/common/ui/tooltip-icon-link";
import type { useCalendarAvailabilityLayout } from "./useCalendarAvailabilityLayout";

const emptyIntervals: readonly SourcedAvailabilityInterval[] = [];
export function CalendarAvailabilityRows({ data }: { data: ReturnType<typeof useCalendarAvailabilityLayout> }) {
  const { t } = useTranslation("booking");
  const { tableProps, availability, rowsBounds, rowsDate, userToday, minute, preferences } = data;
  return (
    <TableList
      {...tableProps}
      hideHeader
      hideFilterPanel
      variant="transparent"
      presentations={{ table: "wide", cards: "narrow" }}
      uiColumns={[
        {
          id: "availability",
          label: t("calendar.availability"),
          minWidth: 320,
          width: 320,
          card: { fullWidth: true },
          renderCell: (row) => {
            const target = row.target;
            if (!target || !row.timezone) return t("calendar.availabilityUnavailable");
            const item = {
              name: target.value.name,
              globalId: target.globalId,
              ...(target.location ? { location: target.location } : {}),
            };
            if (availability.isPending)
              return (
                <div aria-busy="true">
                  <span role="status" className="sr-only">
                    {t("calendar.availabilityLoading")}
                  </span>
                  <Skeleton aria-hidden="true" className="h-16 w-full" />
                </div>
              );
            if (availability.isError || !availability.data) {
              return <span role="status">{t("calendar.availabilityUnavailable")}</span>;
            }
            return (
              <AvailabilityBar
                className="min-w-0 px-1 in-[td]:min-w-72"
                intervals={availability.data.get(target.globalId) ?? emptyIntervals}
                periodStart={new Date(rowsBounds.start)}
                periodEnd={new Date(rowsBounds.end)}
                now={rowsDate === userToday ? new Date(minute) : undefined}
                showBookingContextDetails={false}
                showCurrentAvailability
                showPeriodLabels
                timeZone={preferences.timeZone}
                instrumentTimeZone={row.timezone}
                item={item}
              />
            );
          },
        },
      ]}
      rowActions={{
        renderInteraction: () => null,
        id: "actions",
        label: t("allBookableItems.fields.actions"),
        width: 112,
        minWidth: 112,
        renderCell: ({ row }) =>
          row.target ? (
            <div className="flex items-center gap-1">
              <TooltipIconLink label={t("allBookableItems.actions.viewDetails")} icon={<EyeIcon aria-hidden="true" />}>
                <Link
                  className={buttonVariants({ variant: "outline", size: "icon-lg" })}
                  data-slot="button"
                  to="/booking/bookable-items/$globalId/{-$tab}"
                  params={{ globalId: row.target.globalId, tab: undefined }}
                />
              </TooltipIconLink>
              {row.capabilities?.canCreateBooking ? (
                <TooltipIconLink
                  label={t("allBookableItems.actions.book")}
                  icon={<CalendarPlusIcon aria-hidden="true" />}
                >
                  <Link
                    className={buttonVariants({ variant: "outline", size: "icon-lg" })}
                    data-slot="button"
                    to="/booking/calendar/bookings/add"
                    search={{ date: rowsDate, target: row.target.globalId }}
                  />
                </TooltipIconLink>
              ) : null}
            </div>
          ) : null,
      }}
    />
  );
}
